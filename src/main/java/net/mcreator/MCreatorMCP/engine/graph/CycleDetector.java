package net.mcreator.MCreatorMCP.engine.graph;

import java.util.*;

public class CycleDetector {

    private final SemanticProjectGraph graph;

    public CycleDetector(SemanticProjectGraph graph) {
        this.graph = graph;
    }

    public List<List<String>> detectCycles(Set<String> allNodes) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        List<String> currentPath = new ArrayList<>();

        for (String node : allNodes) {
            if (!visited.contains(node)) {
                findCyclesDfs(node, visited, recStack, currentPath, cycles);
            }
        }
        return cycles;
    }

    private void findCyclesDfs(String node, Set<String> visited, Set<String> recStack,
                               List<String> currentPath, List<List<String>> cycles) {
        visited.add(node);
        recStack.add(node);
        currentPath.add(node);

        for (String neighbor : graph.getDependenciesOf(node)) {
            if (!visited.contains(neighbor)) {
                findCyclesDfs(neighbor, visited, recStack, currentPath, cycles);
            } else if (recStack.contains(neighbor)) {
                int startIdx = currentPath.indexOf(neighbor);
                if (startIdx != -1) {
                    List<String> cycle = new ArrayList<>(currentPath.subList(startIdx, currentPath.size()));
                    cycle.add(neighbor);
                    cycles.add(cycle);
                }
            }
        }

        recStack.remove(node);
        currentPath.remove(currentPath.size() - 1);
    }
}
