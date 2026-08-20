package net.mcreator.MCreatorMCP.engine.ui;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;

public class UISynchronizer {

    private static final Logger LOG = LogManager.getLogger("UISynchronizer");

    public static void runOnEDT(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    public static void runOnEDTAndWait(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch (Exception e) {
                LOG.error("Error executing task on EDT", e);
            }
        }
    }

    public static void refreshWorkspaceUI(MCreator mcreator) {
        if (mcreator == null) return;
        runOnEDT(() -> {
            try {
                mcreator.reloadWorkspaceTabContents();
                LOG.debug("Workspace UI refreshed on EDT");
            } catch (Exception e) {
                LOG.warn("Failed to reload workspace tab contents: {}", e.getMessage());
            }
        });
    }

    public static void syncElementModified(MCreator mcreator, Workspace workspace, ModElement element) {
        if (element == null || workspace == null) return;
        runOnEDTAndWait(() -> {
            try {
                element.reinit(workspace);
                workspace.markDirty();
                if (mcreator != null) {
                    mcreator.reloadWorkspaceTabContents();
                }
                LOG.debug("Synced modified element {} to MCreator UI", element.getName());
            } catch (Exception e) {
                LOG.error("Failed to sync modified element: " + element.getName(), e);
            }
        });
    }

    public static void syncElementDeleted(MCreator mcreator, Workspace workspace, ModElement element) {
        if (element == null || workspace == null) return;
        runOnEDTAndWait(() -> {
            try {
                workspace.removeModElement(element);
                if (mcreator != null) {
                    mcreator.reloadWorkspaceTabContents();
                }
                LOG.debug("Synced deleted element {} to MCreator UI", element.getName());
            } catch (Exception e) {
                LOG.error("Failed to sync deleted element: " + element.getName(), e);
            }
        });
    }
}
