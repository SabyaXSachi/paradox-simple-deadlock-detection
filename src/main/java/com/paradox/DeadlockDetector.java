package com.paradox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deadlock detection with a Resource Allocation Graph (RAG).
 *
 * Graph rules:
 *   Resource -> Process : the resource is allocated to the process
 *   Process  -> Resource: the process is waiting for the resource
 *
 * Every resource has exactly one instance, therefore one cycle in this
 * graph means a deadlock. The cycle is found with a simple depth-first
 * search (DFS). This class contains no JavaFX and no database code.
 */
public class DeadlockDetector {

    /** What the detection found; displayed on the deadlock screen. */
    public static class Result {
        public final boolean deadlock;
        public final List<String> cyclePath;         // e.g. [P1, R2, P2, R1, P1]
        public final List<String> cycleKeys;         // same nodes as graph keys, e.g. [P12, R34, ...]
        public final List<String> involvedProcesses; // e.g. [P1, P2]
        public final String explanation;

        Result(boolean deadlock, List<String> cyclePath, List<String> cycleKeys,
               List<String> involvedProcesses, String explanation) {
            this.deadlock = deadlock;
            this.cyclePath = cyclePath;
            this.cycleKeys = cycleKeys;
            this.involvedProcesses = involvedProcesses;
            this.explanation = explanation;
        }
    }

    public static Result detect(List<Resource> resources, List<Process> processes) {
        // Display name for every graph node, e.g. "R2" or "P1".
        Map<String, String> nodeNames = new HashMap<>();
        for (Resource resource : resources) {
            nodeNames.put(nodeKey("R", resource.getId()), resource.getName());
        }
        for (Process process : processes) {
            nodeNames.put(nodeKey("P", process.getId()), process.getName());
        }

        // Build the graph edges.
        Map<String, List<String>> edges = new HashMap<>();
        for (Resource resource : resources) {
            if (resource.getAllocatedProcessId() != null) {
                addEdge(edges, nodeKey("R", resource.getId()),
                        nodeKey("P", resource.getAllocatedProcessId()));
            }
        }
        for (Process process : processes) {
            if (Process.WAITING.equals(process.getStatus())
                    && process.getWaitingForResourceId() != null) {
                addEdge(edges, nodeKey("P", process.getId()),
                        nodeKey("R", process.getWaitingForResourceId()));
            }
        }

        // Start a depth-first search from every node until a cycle appears.
        for (String start : nodeNames.keySet()) {
            List<String> cycleKeys = findCycle(start, edges);
            if (cycleKeys != null) {
                return buildDeadlockResult(cycleKeys, nodeNames);
            }
        }
        return new Result(false, List.of(), List.of(), List.of(),
                "No cycle exists in the resource allocation graph, "
                        + "so there is no deadlock.");
    }

    // ---------- internal helpers ----------

    private static String nodeKey(String prefix, int id) {
        return prefix + id;
    }

    private static void addEdge(Map<String, List<String>> edges, String from, String to) {
        edges.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
    }

    /**
     * Depth-first search that remembers the current path. When we reach a
     * node that is already on the path, we have found a cycle.
     */
    private static List<String> findCycle(String start, Map<String, List<String>> edges) {
        return dfs(start, edges, new ArrayList<>(), new HashSet<>());
    }

    private static List<String> dfs(String current, Map<String, List<String>> edges,
                                    List<String> path, Set<String> onPath) {
        path.add(current);
        onPath.add(current);
        for (String next : edges.getOrDefault(current, List.of())) {
            if (onPath.contains(next)) {
                // Cycle found: copy the path from the first occurrence of
                // 'next' and close the loop for display.
                List<String> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                cycle.add(next);
                return cycle;
            }
            List<String> found = dfs(next, edges, path, onPath);
            if (found != null) {
                return found;
            }
        }
        path.remove(path.size() - 1);
        onPath.remove(current);
        return null;
    }

    private static Result buildDeadlockResult(List<String> foundKeys,
                                              Map<String, String> nodeNames) {
        // The found cycle is closed (first key == last key). Open it, rotate
        // it to start at the lowest-numbered process so it reads like
        // "P1 -> R2 -> P2 -> R1 -> P1", and close it again.
        List<String> openCycle = new ArrayList<>(foundKeys.subList(0, foundKeys.size() - 1));
        int startIndex = 0;
        long smallestProcessId = Long.MAX_VALUE;
        for (int i = 0; i < openCycle.size(); i++) {
            String key = openCycle.get(i);
            if (key.startsWith("P")) {
                long processId = Long.parseLong(key.substring(1));
                if (processId < smallestProcessId) {
                    smallestProcessId = processId;
                    startIndex = i;
                }
            }
        }
        List<String> cycleKeys = new ArrayList<>(openCycle.subList(startIndex, openCycle.size()));
        cycleKeys.addAll(openCycle.subList(0, startIndex));
        cycleKeys.add(cycleKeys.get(0)); // close the loop for display

        List<String> cyclePath = new ArrayList<>();
        for (String key : cycleKeys) {
            cyclePath.add(nodeNames.getOrDefault(key, key));
        }
        List<String> involved = new ArrayList<>();
        for (String key : cycleKeys) {
            if (key.startsWith("P") && !involved.contains(nodeNames.get(key))) {
                involved.add(nodeNames.get(key));
            }
        }
        String explanation = "Cycle: " + String.join(" -> ", cyclePath)
                + ". Every process in the cycle is waiting for a resource that is "
                + "held by the next process, so none of them can ever continue.";
        return new Result(true, cyclePath, cycleKeys, involved, explanation);
    }
}
