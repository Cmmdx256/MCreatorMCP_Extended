package net.mcreator.MCreatorMCP.engine.api;

import net.mcreator.MCreatorMCP.engine.ProjectIntelligenceEngine;
import net.mcreator.MCreatorMCP.engine.validator.ValidationEngine;
import net.mcreator.MCreatorMCP.engine.validator.ValidationReport;
import net.mcreator.MCreatorMCP.mcp.McpServer;
import net.mcreator.MCreatorMCP.mcp.McpTypes;
import net.mcreator.ui.MCreator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class HighLevelToolRegistry {

    private static final Logger LOG = LogManager.getLogger("HighLevelToolRegistry");

    public static void registerHighLevelTools(McpServer server, MCreator mcreator) {
        ProjectIntelligenceEngine engine = ProjectIntelligenceEngine.getInstance();

        // 1. analyze_project
        server.registerTool(
                McpServer.createTool("analyze_project",
                        "Perform an exhaustive structural, health, performance, security, and dependency analysis on the entire MCreator mod project.",
                        Map.of("type", "object", "properties", Collections.emptyMap())),
                args -> {
                    Map<String, Object> analysis = engine.analyzeProject();
                    return createToolResult(McpServer.toJsonStringStatic(analysis));
                }
        );

        // 2. inspect_project
        server.registerTool(
                McpServer.createTool("inspect_project",
                        "Inspect elements, procedures, variables, models, or query relationships and deletion impact using the Semantic Project Graph. Pass 'current' or leave empty to inspect the actively open editor element.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "targetName", Map.of("type", "string", "description", "Optional name of the element, procedure, or variable to inspect (use 'current' for active editor)"),
                                        "queryType", Map.of("type", "string", "description", "Optional query type: 'context', 'impact', 'dependencies'")
                                ))),
                args -> {
                    String target = (String) args.get("targetName");
                    String queryType = (String) args.get("queryType");
                    Map<String, Object> result = engine.inspectProject(target, queryType);
                    return createToolResult(McpServer.toJsonStringStatic(result));
                }
        );

        // 3. execute_task
        server.registerTool(
                McpServer.createTool("execute_task",
                        "Execute a high-level goal or multi-step workflow in a single atomic transaction with auto-rollback, pre/post-validation, and live UI synchronization.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "intent", Map.of("type", "string", "description", "Natural language description of the goal (e.g. 'Create a sword with speed effect at night')"),
                                        "steps", Map.of("type", "array", "description", "Optional explicit list of step objects with 'operation' and 'arguments'"),
                                        "parameters", Map.of("type", "object", "description", "Optional key-value parameters for intent decomposition")
                                ),
                                "required", List.of("intent"))),
                args -> {
                    String intent = (String) args.get("intent");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) args.getOrDefault("parameters", args);
                    Map<String, Object> execResult = engine.executeTask(intent, params);
                    return createToolResult(McpServer.toJsonStringStatic(execResult));
                }
        );

        // 4. modify_project
        server.registerTool(
                McpServer.createTool("modify_project",
                        "Execute declarative batch modifications across multiple elements, properties, tags, or localizations atomically with live UI reload.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "modifications", Map.of("type", "array", "description", "List of step objects to execute sequentially")
                                ),
                                "required", List.of("modifications"))),
                args -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("steps", args.get("modifications"));
                    Map<String, Object> execResult = engine.executeTask("Batch Project Modification", params);
                    return createToolResult(McpServer.toJsonStringStatic(execResult));
                }
        );

        // 5. create_element
        server.registerTool(
                McpServer.createTool("create_element",
                        "Create a complete mod element with automated property configuration, procedure bindings, tags, and localizations.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "name", Map.of("type", "string", "description", "Unique name of the mod element"),
                                        "type", Map.of("type", "string", "description", "Element type: 'item', 'block', 'procedure', 'entity', 'biome', 'armor', 'gui', etc."),
                                        "properties", Map.of("type", "object", "description", "Optional initial properties to configure on creation"),
                                        "procedureBinding", Map.of("type", "object", "description", "Optional procedure to bind: { 'event': 'onRightClicked', 'procedureName': 'Foo' }")
                                ),
                                "required", List.of("name", "type"))),
                args -> {
                    String name = (String) args.get("name");
                    String type = (String) args.get("type");
                    List<Map<String, Object>> steps = new ArrayList<>();

                    // Step 1: Create element
                    Map<String, Object> s1 = new HashMap<>();
                    s1.put("operation", "createElement");
                    s1.put("description", "Create " + type + " element: " + name);
                    s1.put("arguments", Map.of("name", name, "type", type));
                    steps.add(s1);

                    // Step 2: Patch properties if provided
                    if (args.containsKey("properties") && args.get("properties") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> props = (Map<String, Object>) args.get("properties");
                        for (var entry : props.entrySet()) {
                            Map<String, Object> sp = new HashMap<>();
                            sp.put("operation", "patchElementProperty");
                            sp.put("description", "Patch property " + entry.getKey());
                            sp.put("arguments", Map.of("elementName", name, "propertyKey", entry.getKey(), "propertyValue", entry.getValue()));
                            steps.add(sp);
                        }
                    }

                    // Step 3: Link procedure if provided
                    if (args.containsKey("procedureBinding") && args.get("procedureBinding") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pb = (Map<String, Object>) args.get("procedureBinding");
                        String event = (String) pb.get("event");
                        String proc = (String) pb.get("procedureName");
                        if (event != null && proc != null) {
                            Map<String, Object> sl = new HashMap<>();
                            sl.put("operation", "linkProcedureToElement");
                            sl.put("description", "Link procedure " + proc + " to event " + event);
                            sl.put("arguments", Map.of("elementName", name, "eventName", event, "procedureName", proc));
                            steps.add(sl);
                        }
                    }

                    Map<String, Object> planArgs = new HashMap<>();
                    planArgs.put("steps", steps);
                    Map<String, Object> execResult = engine.executeTask("Create Element: " + name, planArgs);
                    return createToolResult(McpServer.toJsonStringStatic(execResult));
                }
        );

        // 6. validate_project
        server.registerTool(
                McpServer.createTool("validate_project",
                        "Run multi-stage validation: FreeMarker dry-run simulation, tick loop detection, broken references, and MCreator version compatibility.",
                        Map.of("type", "object", "properties", Collections.emptyMap())),
                args -> {
                    ValidationReport report = ValidationEngine.validateWorkspace(mcreator != null ? mcreator.getWorkspace() : null, engine.getModel(), engine.getGraph());
                    return createToolResult(McpServer.toJsonStringStatic(report.toMap()));
                }
        );

        // 7. get_project_context
        server.registerTool(
                McpServer.createTool("get_project_context",
                        "Retrieve a compact, token-optimized semantic context summary of the entire mod project designed for LLM prompts, including active editor and UI state.",
                        Map.of("type", "object", "properties", Collections.emptyMap())),
                args -> {
                    Map<String, Object> context = engine.getProjectContext();
                    return createToolResult(McpServer.toJsonStringStatic(context));
                }
        );

        // 8. get_live_context
        server.registerTool(
                McpServer.createTool("get_live_context",
                        "Capture live MCreator runtime state: active editor tab, currently open element, selected elements in workspace list, active folder, open tabs, and build status.",
                        Map.of("type", "object", "properties", Collections.emptyMap())),
                args -> {
                    Map<String, Object> liveCtx = engine.getLiveContextProvider() != null ?
                            engine.getLiveContextProvider().captureLiveContext() : Map.of("status", "UNAVAILABLE");
                    return createToolResult(McpServer.toJsonStringStatic(liveCtx));
                }
        );

        // 9. manage_tool_mode
        server.registerTool(
                McpServer.createTool("manage_tool_mode",
                        "Set the active MCP tool catalog mode: 'DUAL_HYBRID' (Default: 9 High-Level + 170 Low-Level), 'HIGH_LEVEL_ONLY' (Optimized 9 tools), or 'LEGACY_FULL' (170 Low-Level tools).",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "mode", Map.of("type", "string", "enum", List.of("DUAL_HYBRID", "HIGH_LEVEL_ONLY", "LEGACY_FULL"))
                                ),
                                "required", List.of("mode"))),
                args -> {
                    String mode = (String) args.get("mode");
                    server.setToolMode(mode);
                    return createToolResult("Tool mode switched to: " + mode);
                }
        );

        LOG.info("High-Level Intelligence MCP tools registered successfully");
    }

    private static McpTypes.ToolResult createToolResult(String text) {
        return new McpTypes.ToolResult(List.of(new McpTypes.ToolContent("text", text)), false);
    }
}
