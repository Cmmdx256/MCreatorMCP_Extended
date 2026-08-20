package net.mcreator.MCreatorMCP.engine.context;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorTabs;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.workspace.WorkspacePanel;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.FolderElement;
import net.mcreator.workspace.elements.IElement;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class LiveContextProvider {

    private static final Logger LOG = LogManager.getLogger("LiveContextProvider");

    private final MCreator mcreator;

    public LiveContextProvider(MCreator mcreator) {
        this.mcreator = mcreator;
    }

    public Map<String, Object> captureLiveContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        if (mcreator == null) {
            context.put("status", "NO_MCREATOR_INSTANCE");
            return context;
        }

        try {
            AtomicReference<Map<String, Object>> ref = new AtomicReference<>(new LinkedHashMap<>());

            if (SwingUtilities.isEventDispatchThread()) {
                ref.set(extractContextDirect());
            } else {
                SwingUtilities.invokeAndWait(() -> ref.set(extractContextDirect()));
            }

            return ref.get();
        } catch (Exception e) {
            LOG.warn("Failed to capture live MCreator context: {}", e.getMessage());
            context.put("error", e.getMessage());
            return context;
        }
    }

    private Map<String, Object> extractContextDirect() {
        Map<String, Object> ctx = new LinkedHashMap<>();

        Workspace ws = mcreator.getWorkspace();
        if (ws != null && ws.getWorkspaceSettings() != null) {
            ctx.put("activeWorkspaceName", ws.getWorkspaceSettings().getModName());
            ctx.put("activeModId", ws.getWorkspaceSettings().getModID());
            ctx.put("generatorFlavor", ws.getGeneratorConfiguration() != null ?
                    ws.getGeneratorConfiguration().getGeneratorFlavor().name() : "UNKNOWN");
        }

        // 1. Current Active Tab & Open Editor
        MCreatorTabs tabs = mcreator.getTabs();
        if (tabs != null) {
            MCreatorTabs.Tab currentTab = tabs.getCurrentTab();
            if (currentTab != null) {
                Map<String, Object> tabInfo = new LinkedHashMap<>();
                tabInfo.put("tabTitle", currentTab.getText());
                if (currentTab.getContent() != null) {
                    tabInfo.put("contentType", currentTab.getContent().getClass().getSimpleName());
                }

                // Check if the tab content is a ModElementGUI (e.g. ProcedureGUI, ItemGUI, BlockGUI)
                if (currentTab.getContent() instanceof ModElementGUI) {
                    ModElementGUI gui = (ModElementGUI) currentTab.getContent();
                    ModElement openElement = gui.getModElement();
                    if (openElement != null) {
                        tabInfo.put("activeEditingElement", openElement.getName());
                        tabInfo.put("activeEditingType", openElement.getType() != null ?
                                openElement.getType().getRegistryName() : "UNKNOWN");
                        tabInfo.put("isCodeLocked", openElement.isCodeLocked());
                    }
                }
                ctx.put("activeTab", tabInfo);
            }

            // List of all open tabs
            List<String> openTabTitles = new ArrayList<>();
            for (MCreatorTabs.Tab t : tabs.getTabs()) {
                openTabTitles.add(t.getText());
            }
            ctx.put("allOpenTabs", openTabTitles);
        }

        // 2. Currently Selected Elements in Workspace List
        if (mcreator.getWorkspacePanel() instanceof WorkspacePanel) {
            WorkspacePanel wp = (WorkspacePanel) mcreator.getWorkspacePanel();
            if (wp.list != null) {
                List<IElement> selected = wp.list.getSelectedValuesList();
                if (selected != null && !selected.isEmpty()) {
                    List<String> selectedNames = new ArrayList<>();
                    for (IElement el : selected) {
                        if (el != null) selectedNames.add(el.getName());
                    }
                    ctx.put("selectedElementsInUI", selectedNames);
                }
            }

            // Current Folder navigation
            FolderElement curFolder = wp.currentFolder;
            if (curFolder != null) {
                ctx.put("activeUIFolder", curFolder.getName());
            } else {
                ctx.put("activeUIFolder", "ROOT");
            }
        }

        // 3. Live Gradle Status
        if (mcreator.getGradleConsole() != null) {
            Map<String, Object> gradle = new LinkedHashMap<>();
            gradle.put("isTaskRunning", mcreator.getGradleConsole().isGradleSetupTaskRunning());
            gradle.put("status", mcreator.getGradleConsole().getStatus() == 0 ? "READY" :
                    (mcreator.getGradleConsole().getStatus() == 1 ? "RUNNING" : "ERROR"));
            ctx.put("gradleLiveState", gradle);
        }

        return ctx;
    }

    public String getActiveEditingElementName() {
        Map<String, Object> ctx = captureLiveContext();
        if (ctx.containsKey("activeTab") && ctx.get("activeTab") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tab = (Map<String, Object>) ctx.get("activeTab");
            return (String) tab.get("activeEditingElement");
        }
        return null;
    }
}
