package net.mcreator.MCreatorMCP.engine.graph;

import net.mcreator.MCreatorMCP.engine.model.DependencyRelation;
import net.mcreator.MCreatorMCP.engine.model.ProjectModel;

import java.util.*;

public class GraphQueryEngine {

    private final SemanticProjectGraph graph;
    private final ProjectModel model;

    public GraphQueryEngine(SemanticProjectGraph graph, ProjectModel model) {
        this.graph = graph;
        this.model = model;
    }

    public Map<String, Object> queryElementContext(String elementName) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (elementName == null || elementName.trim().isEmpty()) return result;

        String name = elementName.trim();
        var elementNode = model.getElement(name);
        if (elementNode != null) {
            result.put("element", elementNode.toMap());
        }

        var procNode = model.getProcedure(name);
        if (procNode != null) {
            result.put("procedure", procNode.toMap());
        }

        List<Map<String, Object>> outgoing = new ArrayList<>();
        for (SemanticProjectGraph.Edge e : graph.getOutgoing(name)) {
            outgoing.add(e.toMap());
        }
        result.put("outgoingRelations", outgoing);

        List<Map<String, Object>> incoming = new ArrayList<>();
        for (SemanticProjectGraph.Edge e : graph.getIncoming(name)) {
            incoming.add(e.toMap());
        }
        result.put("incomingRelations", incoming);

        return result;
    }

    public List<String> findProceduresTriggeredBy(String elementName) {
        List<String> procs = new ArrayList<>();
        for (SemanticProjectGraph.Edge e : graph.getIncoming(elementName)) {
            if (e.getRelation() == DependencyRelation.TRIGGERS) {
                procs.add(e.getSource());
            }
        }
        for (SemanticProjectGraph.Edge e : graph.getOutgoing(elementName)) {
            if (e.getRelation() == DependencyRelation.TRIGGERS) {
                procs.add(e.getTarget());
            }
        }
        return procs;
    }

    public List<String> findElementsReferencing(String targetName) {
        List<String> list = new ArrayList<>();
        for (SemanticProjectGraph.Edge e : graph.getIncoming(targetName)) {
            list.add(e.getSource());
        }
        return list;
    }
}
