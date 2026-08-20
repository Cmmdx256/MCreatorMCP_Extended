package net.mcreator.MCreatorMCP.engine.index;

import net.mcreator.MCreatorMCP.engine.graph.SemanticProjectGraph;
import net.mcreator.MCreatorMCP.engine.model.ProjectModel;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IncrementalChangeTracker {

    private static final Logger LOG = LogManager.getLogger("IncrementalChangeTracker");

    private final ProjectModel model;
    private final SemanticProjectGraph graph;

    public IncrementalChangeTracker(ProjectModel model, SemanticProjectGraph graph) {
        this.model = model;
        this.graph = graph;
    }

    public synchronized void onElementModified(Workspace workspace, ModElement element) {
        if (element == null) return;
        String name = element.getName();
        LOG.debug("Incremental update for element: {}", name);
        graph.removeNode(name);
        model.getElements().remove(name);
        model.getProcedures().remove(name);

        ProjectIndexer.indexSingleElement(workspace, element, model, graph);
    }

    public synchronized void onElementDeleted(String elementName) {
        if (elementName == null) return;
        LOG.debug("Incremental deletion for element: {}", elementName);
        graph.removeNode(elementName);
        model.getElements().remove(elementName);
        model.getProcedures().remove(elementName);
    }
}
