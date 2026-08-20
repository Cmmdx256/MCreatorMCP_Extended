package net.mcreator.MCreatorMCP.engine.transaction;

import net.mcreator.io.FileIO;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.*;

public class WorkspaceSnapshot {

    private static final Logger LOG = LogManager.getLogger("WorkspaceSnapshot");

    private final String snapshotId;
    private final long timestamp;
    private final Map<String, String> elementJsonBackups;
    private final Set<String> createdElementNames;

    public WorkspaceSnapshot(String snapshotId) {
        this.snapshotId = snapshotId;
        this.timestamp = System.currentTimeMillis();
        this.elementJsonBackups = new HashMap<>();
        this.createdElementNames = new HashSet<>();
    }

    public String getSnapshotId() { return snapshotId; }
    public long getTimestamp() { return timestamp; }

    public void captureElement(Workspace workspace, String elementName) {
        if (workspace == null || elementName == null) return;
        File modFile = new File(workspace.getFolderManager().getModElementsDir(), elementName + ".mod.json");
        if (modFile.exists()) {
            elementJsonBackups.put(elementName, FileIO.readFileToString(modFile));
        }
    }

    public void markElementCreated(String elementName) {
        if (elementName != null) createdElementNames.add(elementName);
    }

    public void restore(Workspace workspace) {
        if (workspace == null) return;
        LOG.warn("Restoring workspace snapshot: {}", snapshotId);

        // Delete newly created elements
        for (String created : createdElementNames) {
            ModElement el = workspace.getModElementByName(created);
            if (el != null) {
                try {
                    workspace.removeModElement(el);
                    LOG.info("Rollback: deleted created element {}", created);
                } catch (Exception e) {
                    LOG.error("Failed to delete element during rollback: " + created, e);
                }
            }
        }

        // Restore modified files
        for (Map.Entry<String, String> entry : elementJsonBackups.entrySet()) {
            String name = entry.getKey();
            String json = entry.getValue();
            File modFile = new File(workspace.getFolderManager().getModElementsDir(), name + ".mod.json");
            try {
                FileIO.writeStringToFile(json, modFile);
                LOG.info("Rollback: restored JSON definition for {}", name);
            } catch (Exception e) {
                LOG.error("Failed to restore JSON for " + name, e);
            }
        }
    }
}
