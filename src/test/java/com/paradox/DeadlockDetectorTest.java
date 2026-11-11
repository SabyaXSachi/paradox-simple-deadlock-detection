package com.paradox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Resource Allocation Graph cycle detection.
 * No database and no UI - the detector works on plain lists.
 */
class DeadlockDetectorTest {

    private Process process(int id, String name) {
        Process process = new Process(name);
        process.setId(id);
        return process;
    }

    private Resource resource(int id, String name, Integer holder) {
        Resource resource = new Resource(name);
        resource.setId(id);
        resource.setAllocatedProcessId(holder);
        return resource;
    }

    private Process waiting(Process process, int resourceId) {
        process.setStatus(Process.WAITING);
        process.setWaitingForResourceId(resourceId);
        return process;
    }

    @Test
    void noEdgesMeansNoDeadlock() {
        // P1 holds R1; nobody waits.
        List<Resource> resources = List.of(resource(1, "R1", 10));
        List<Process> processes = List.of(process(10, "P1"));

        DeadlockDetector.Result result = DeadlockDetector.detect(resources, processes);

        assertFalse(result.deadlock);
        assertTrue(result.involvedProcesses.isEmpty());
    }

    @Test
    void twoProcessCycleIsADeadlock() {
        // P1 holds R1 and waits for R2; P2 holds R2 and waits for R1.
        List<Resource> resources = List.of(
                resource(1, "R1", 10),
                resource(2, "R2", 11));
        List<Process> processes = List.of(
                waiting(process(10, "P1"), 2),
                waiting(process(11, "P2"), 1));

        DeadlockDetector.Result result = DeadlockDetector.detect(resources, processes);

        assertTrue(result.deadlock);
        // The cycle in the display names: P1 -> R2 -> P2 -> R1 -> P1
        assertEquals(List.of("P1", "R2", "P2", "R1", "P1"), result.cyclePath);
        assertEquals(List.of("P1", "P2"), result.involvedProcesses);
        assertTrue(result.explanation.contains("P1"));
    }

    @Test
    void threeProcessCircularWaitIsADeadlock() {
        // P1 holds R1 wants R2; P2 holds R2 wants R3; P3 holds R3 wants R1.
        List<Resource> resources = List.of(
                resource(1, "R1", 10),
                resource(2, "R2", 11),
                resource(3, "R3", 12));
        List<Process> processes = List.of(
                waiting(process(10, "P1"), 2),
                waiting(process(11, "P2"), 3),
                waiting(process(12, "P3"), 1));

        DeadlockDetector.Result result = DeadlockDetector.detect(resources, processes);

        assertTrue(result.deadlock);
        assertEquals(3, result.involvedProcesses.size());
        assertTrue(result.involvedProcesses.containsAll(List.of("P1", "P2", "P3")));
        // The cycle must be closed (first element equals last element).
        assertEquals(result.cyclePath.get(0), result.cyclePath.get(result.cyclePath.size() - 1));
    }

    @Test
    void waitingInAChainIsNotADeadlock() {
        // P1 waits for R2 which P2 holds, but P2 waits for nothing:
        // no cycle, so no deadlock (P2 will finish and release R2).
        List<Resource> resources = List.of(
                resource(1, "R1", 10),
                resource(2, "R2", 11));
        List<Process> processes = List.of(
                waiting(process(10, "P1"), 2),
                process(11, "P2"));

        DeadlockDetector.Result result = DeadlockDetector.detect(resources, processes);

        assertFalse(result.deadlock);
        assertTrue(result.involvedProcesses.isEmpty());
    }

    @Test
    void terminatedProcessDoesNotCreateEdges() {
        // P1 is terminated: it holds nothing and waits for nothing.
        Process terminated = process(10, "P1");
        terminated.setStatus(Process.TERMINATED);
        List<Resource> resources = List.of(resource(1, "R1", null));
        List<Process> processes = List.of(terminated);

        DeadlockDetector.Result result = DeadlockDetector.detect(resources, processes);

        assertFalse(result.deadlock);
    }
}
