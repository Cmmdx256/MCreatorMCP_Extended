package net.mcreator.MCreatorMCP.engine.graph;

import java.util.*;

public class ImpactAnalyzer {

    private final SemanticProjectGraph graph;

    public ImpactAnalyzer(SemanticProjectGraph graph) {
        this.graph = graph;
    }

    public Map<String, Object> analyzeDeletionImpact(String elementName) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (elementName == null || elementName.trim().isEmpty()) {
            report.put("error", "elementName required");
            return report;
        }

        String name = elementName.trim();
        Set<String> directDependents = graph.getDependentsOf(name);
        Set<String> allTransitiveDependents = new HashSet<>();
        Queue<String> queue = new LinkedList<>(directDependents);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (allTransitiveDependents.add(current)) {
                for (String dep : graph.getDependentsOf(current)) {
                    if (!allTransitiveDependents.contains(dep) && !dep.equals(name)) {
                        queue.add(dep);
                    }
                }
            }
        }

        report.put("targetElement", name);
        report.put("directBrokenElementsCount", directDependents.size());
        report.put("directBrokenElements", new ArrayList<>(directDependents));
        report.put("transitiveBrokenElementsCount", allTransitiveDependents.size());
        report.put("transitiveBrokenElements", new ArrayList<>(allTransitiveDependents));
        report.put("riskLevel", directDependents.size() > 5 ? "HIGH" : (directDependents.isEmpty() ? "SAFE" : "MEDIUM"));
        return report;
    }
}
