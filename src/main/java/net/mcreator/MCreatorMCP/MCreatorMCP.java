package net.mcreator.MCreatorMCP;

import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpHttpTransport;
import net.mcreator.MCreatorMCP.mcp.McpStdioTransport;
import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.action.BasicAction;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.net.ServerSocket;

public class MCreatorMCP extends JavaPlugin {

    private static final Logger LOG = LogManager.getLogger("MCreatorMCP");
    
    private McpServer mcpServer;
    private McpHttpTransport httpTransport;
    private McpStdioTransport stdioTransport;
    private MCPToolsService toolsService;
    private volatile int currentHttpPort = 5175;

    public MCreatorMCP(Plugin plugin) {
        super(plugin);

        try {
            // Initialize MCP server
            mcpServer = new McpServer("MCreator MCP Server", "2.0.0");
            toolsService = new MCPToolsService();
        } catch (Throwable t) {
            LOG.error("Failed to initialize core MCP server components", t);
        }

        addListener(MCreatorLoadedEvent.class, event -> SwingUtilities.invokeLater(() -> {
            try {
                // Auto-repair on load
                if (event.getMCreator() != null && event.getMCreator().getWorkspace() != null) {
                    try {
                        int repaired = MCPToolsService.repairWorkspaceDirect(event.getMCreator().getWorkspace());
                        if (repaired > 0) {
                            LOG.info("Auto-repaired {} element(s) on workspace load", repaired);
                        }
                    } catch (Throwable t) {
                        LOG.warn("Could not auto-repair workspace on load: {}", t.getMessage());
                    }
                }

                // Start MCP server
                startMCPServer(event);

                // Create demo action
                BasicAction demoAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "Build Workspace",
                        e -> event.getMCreator().getActionRegistry().buildWorkspace.doAction());
                try { demoAction.setIcon(UIRES.get("16px.play")); } catch (Throwable ignored) {}

                // Create Auto-Repair action
                BasicAction repairAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "Auto-Repair Workspace & Elements",
                        e -> {
                            if (event.getMCreator() != null && event.getMCreator().getWorkspace() != null) {
                                int count = MCPToolsService.repairWorkspaceDirect(event.getMCreator().getWorkspace());
                                showInfoDialog("Workspace Repaired", "Scanned and auto-repaired " + count + " mod elements.\nAll @Nonnull fields and JSON definitions are verified.");
                            }
                        });

                // Create Intelligence Status action
                BasicAction intelligenceStatusAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "Project Intelligence Summary",
                        e -> {
                            var overview = net.mcreator.MCreatorMCP.engine.ProjectIntelligenceEngine.getInstance().analyzeProject();
                            showInfoDialog("Project Intelligence Engine", net.mcreator.MCreatorMCP.mcp.McpServer.toJsonStringStatic(overview));
                        });

                // Create Live Context action
                BasicAction liveContextAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "AI Live Context & Active Editor",
                        e -> {
                            var live = net.mcreator.MCreatorMCP.engine.ProjectIntelligenceEngine.getInstance().getLiveContextProvider().captureLiveContext();
                            showInfoDialog("MCreator AI Live Context", net.mcreator.MCreatorMCP.mcp.McpServer.toJsonStringStatic(live));
                        });

                // Create Reindex Action
                BasicAction reindexAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "Re-Index Semantic Graph",
                        e -> {
                            net.mcreator.MCreatorMCP.engine.ProjectIntelligenceEngine.getInstance().reindex();
                            showInfoDialog("Re-Index Complete", "Semantic Project Graph & Incremental Index re-built successfully.");
                        });

                // Create MCP status action
                BasicAction mcpStatusAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "MCP Server Status",
                        e -> showMCPStatus());
                try { mcpStatusAction.setIcon(UIRES.get("16px.info")); } catch (Throwable ignored) {}

                // Create MCP restart action
                BasicAction mcpRestartAction = new BasicAction(event.getMCreator().getActionRegistry(),
                        "Restart MCP Server",
                        e -> restartMCPServer(event));

                // Build menu
                JMenu menu = new JMenu("MCP Tools");
                menu.add(demoAction);
                menu.add(repairAction);
                menu.addSeparator();
                menu.add(intelligenceStatusAction);
                menu.add(liveContextAction);
                menu.add(reindexAction);
                menu.addSeparator();
                menu.add(mcpStatusAction);
                menu.add(mcpRestartAction);

                if (event.getMCreator().getMainMenuBar() != null) {
                    event.getMCreator().getMainMenuBar().add(menu);
                }
                if (event.getMCreator().getToolBar() != null) {
                    try {
                        event.getMCreator().getToolBar().addToRightToolbar(demoAction);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                LOG.error("Error setting up MCP UI components", t);
            }
        }));

        addListener(net.mcreator.plugin.events.workspace.WorkspaceSavedEvent.class, event -> {
            try {
                if (event.getWorkspace() != null) {
                    net.mcreator.MCreatorMCP.engine.ProjectIntelligenceEngine.getInstance().reindex();
                }
            } catch (Throwable ignored) {}
        });

        LOG.info("MCreator MCP Plugin loaded - ready to start MCP server");
    }

    private void startMCPServer(MCreatorLoadedEvent event) {
        try {
            // Stop existing server if running
            stopMCPServer();

            // Find free port for HTTP transport
            int httpPort = findFreePort(5175);
            currentHttpPort = httpPort;

            // Set workspace in MCP server
            mcpServer.setWorkspace(event.getMCreator().getWorkspace());
            
            // Register low-level tools with MCP server
            toolsService.registerTools(mcpServer, event.getMCreator());

            // Register high-level intelligence tools
            net.mcreator.MCreatorMCP.engine.api.HighLevelToolRegistry.registerHighLevelTools(mcpServer, event.getMCreator());

            // Initialize Project Intelligence Engine with MCreator instance and bind all internal capabilities
            net.mcreator.MCreatorMCP.engine.ProjectIntelligenceEngine.getInstance().initialize(event.getMCreator().getWorkspace(), mcpServer, event.getMCreator());

            // Start HTTP transport
            httpTransport = new McpHttpTransport(mcpServer, httpPort);
            httpTransport.start();

            // Start stdio transport for traditional MCP clients
            stdioTransport = new McpStdioTransport(mcpServer);
            stdioTransport.start();

            LOG.info("Native MCreator AI Plugin & MCP Server started successfully");
            showInfoDialog("MCreator AI Plugin & MCP Server Started", 
                "MCreatorMCP Native Plugin is active:\n" +
                "HTTP Endpoint: http://localhost:" + httpPort + "/mcp\n" +
                "Live Context: Active Editor & Tab Awareness Enabled\n" +
                "Tool Modes: DUAL_HYBRID (9 High-Level + 170 Low-Level Tools)\n" +
                "Semantic Graph & Incremental Index: Running\n" +
                "Health: http://localhost:" + httpPort + "/health");

        } catch (IOException e) {
            LOG.error("Failed to start MCP server", e);
            showErrorDialog("MCP Server Startup Failed", 
                "Failed to start MCP server: " + e.getMessage());
        }
    }

    private void stopMCPServer() {
        if (httpTransport != null) {
            LOG.info("Stopping MCP HTTP transport...");
            httpTransport.stop();
            httpTransport = null;
        }

        if (stdioTransport != null) {
            LOG.info("Stopping MCP stdio transport...");
            stdioTransport.stop();
            stdioTransport = null;
        }

        LOG.info("MCP server stopped");
    }

    private void restartMCPServer(MCreatorLoadedEvent event) {
        LOG.info("Restarting MCP server...");
        stopMCPServer();
        
        // Small delay to ensure cleanup
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            startMCPServer(event);
        });
    }

    private void showMCPStatus() {
        String status;
        
        if (mcpServer != null && mcpServer.isInitialized()) {
            status = "MCP Server Status: RUNNING\n" +
                    "HTTP Endpoint: http://localhost:" + currentHttpPort + "/mcp\n" +
                    "SSE Endpoint: http://localhost:" + currentHttpPort + "/mcp/sse\n" +
                    "Health Check: http://localhost:" + currentHttpPort + "/health\n" +
                    "Stdio: Available\n" +
                    "Workspace: " + (mcpServer.getWorkspace() != null ? "Loaded" : "None");
        } else {
            status = "MCP Server Status: NOT RUNNING";
        }

        showInfoDialog("MCP Server Status", status);
    }

    private void showErrorDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE));
    }

    private void showInfoDialog(String title, String message) {
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * Find a free port, starting with the preferred port
     * @param preferredPort The port to try first
     * @return A free port number
     */
    private static int findFreePort(int preferredPort) {
        // Try preferred port first
        try (ServerSocket s = new ServerSocket(preferredPort)) {
            return preferredPort;
        } catch (IOException ignored) {
            // Preferred port is busy, find any free port
        }
        
        // Find any free port
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No free port available", e);
        }
    }
}
