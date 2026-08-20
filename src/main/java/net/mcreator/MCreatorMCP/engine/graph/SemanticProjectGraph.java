package net.mcreator.MCreatorMCP.engine.graph;

import net.mcreator.MCreatorMCP.engine.model.DependencyRelation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SemanticProjectGraph {

    public static class Edge {
        private final String source;
        private final String target;
        private final DependencyRelation relation;
        private final String metadata;

        public Edge(String source, String target, DependencyRelation relation, String metadata) {
            this.source = source;
            this.target = target;
            this.relation = relation;
            this.metadata = metadata != null ? metadata : "";
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public DependencyRelation getRelation() { return relation; }
        public String getMetadata() { return metadata; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", source);
            m.put("target", target);
            m.put("relation", relation.name());
            m.put("metadata", metadata);
            return m;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Edge)) return false;
            Edge edge = (Edge) o;
            return Objects.equals(source, edge.source) &&
                    Objects.equals(target, edge.target) &&
                    relation == edge.relation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, target, relation);
        }
    }

    private final Map<String, Set<Edge>> outgoingEdges;
    private final Map<String, Set<Edge>> incomingEdges;

    public SemanticProjectGraph() {
        this.outgoingEdges = new ConcurrentHashMap<>();
        this.incomingEdges = new ConcurrentHashMap<>();
    }

    public synchronized void addEdge(String source, String target, DependencyRelation relation, String metadata) {
        if (source == null || target == null || relation == null) return;
        Edge edge = new Edge(source, target, relation, metadata);
        outgoingEdges.computeIfAbsent(source, k -> ConcurrentHashMap.newKeySet()).add(edge);
        incomingEdges.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(edge);
    }

    public synchronized void removeNode(String node) {
        if (node == null) return;
        Set<Edge> out = outgoingEdges.remove(node);
        if (out != null) {
            for (Edge e : out) {
                Set<Edge> in = incomingEdges.get(e.getTarget());
                if (in != null) in.remove(e);
            }
        }

        Set<Edge> in = incomingEdges.remove(node);
        if (in != null) {
            for (Edge e : in) {
                Set<Edge> outSet = outgoingEdges.get(e.getSource());
                if (outSet != null) outSet.remove(e);
            }
        }
    }

    public synchronized void clear() {
        outgoingEdges.clear();
        incomingEdges.clear();
    }

    public Set<Edge> getOutgoing(String node) {
        return outgoingEdges.getOrDefault(node, Collections.emptySet());
    }

    public Set<Edge> getIncoming(String node) {
        return incomingEdges.getOrDefault(node, Collections.emptySet());
    }

    public Set<String> getDependenciesOf(String node) {
        Set<String> deps = new HashSet<>();
        for (Edge e : getOutgoing(node)) {
            deps.add(e.getTarget());
        }
        return deps;
    }

    public Set<String> getDependentsOf(String node) {
        Set<String> dependents = new HashSet<>();
        for (Edge e : getIncoming(node)) {
            dependents.add(e.getSource());
        }
        return dependents;
    }

    public Map<String, Object> exportGraphSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        int totalEdges = 0;
        for (Set<Edge> edges : outgoingEdges.values()) {
            totalEdges += edges.size();
        }
        summary.put("totalNodes", outgoingEdges.size());
        summary.put("totalRelationships", totalEdges);
        return summary;
    }
}
