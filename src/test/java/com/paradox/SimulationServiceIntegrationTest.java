package com.paradox;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full sample scenario against the real MySQL database:
 * build the deadlock, detect it, terminate P2, detect again.
 * The test simulation is deleted at the end.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SimulationServiceIntegrationTest {

    private static final SimulationService service = new SimulationService();
    private static int simulationId;
    private static int p1Id;
    private static int p2Id;

    @BeforeAll
    static void buildSampleScenario() {
        Simulation simulation = service.loadSampleScenario();
        simulationId = simulation.getId();

        List<Process> processes = service.getProcesses(simulationId);
        p1Id = processes.stream().filter(p -> p.getName().equals("P1"))
                .findFirst().orElseThrow().getId();
        p2Id = processes.stream().filter(p -> p.getName().equals("P2"))
                .findFirst().orElseThrow().getId();
    }

    @AfterAll
    static void cleanUp() {
        service.deleteSimulation(simulationId);
    }

    @Test
    @Order(1)
    void sampleScenarioIsSavedInMySQL() {
        List<Process> processes = service.getProcesses(simulationId);
        List<Resource> resources = service.getResources(simulationId);

        assertEquals(2, processes.size());
        assertEquals(2, resources.size());
        assertTrue(processes.stream().allMatch(p -> Process.WAITING.equals(p.getStatus())));
        assertTrue(resources.stream().noneMatch(Resource::isFree));
    }

    @Test
    @Order(2)
    void deadlockIsDetectedWithTheExpectedCycle() {
        DeadlockDetector.Result result = DeadlockDetector.detect(
                service.getResources(simulationId), service.getProcesses(simulationId));

        assertTrue(result.deadlock);
        assertEquals(List.of("P1", "R2", "P2", "R1", "P1"), result.cyclePath);
        assertEquals(List.of("P1", "P2"), result.involvedProcesses);
    }

    @Test
    @Order(3)
    void deletingAProcessThatHoldsResourcesIsBlocked() {
        try {
            service.deleteProcess(p2Id);
            throw new AssertionError("expected an error because P2 holds R2");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("holds resources"));
        }
    }

    @Test
    @Order(4)
    void terminatingP2ReleasesItsResourceAndResolvesTheDeadlock() {
        service.terminateProcess(p2Id);

        // R2 went back to the pool and P1, which was waiting for it, got it.
        List<Resource> resources = service.getResources(simulationId);
        Resource r2 = resources.stream().filter(r -> r.getName().equals("R2"))
                .findFirst().orElseThrow();
        assertFalse(r2.isFree());
        assertEquals("P1", r2.getHolderName());

        List<Process> processes = service.getProcesses(simulationId);
        assertEquals(Process.TERMINATED,
                processes.stream().filter(p -> p.getId() == p2Id)
                        .findFirst().orElseThrow().getStatus());

        // Detection now finds no cycle.
        DeadlockDetector.Result result = DeadlockDetector.detect(resources, processes);
        assertFalse(result.deadlock);
    }
}
