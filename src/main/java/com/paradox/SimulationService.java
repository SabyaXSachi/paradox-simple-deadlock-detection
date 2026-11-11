package com.paradox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * All simulation logic in one place: managing simulations, processes and
 * resources, allocation with waiting, process termination, and loading the
 * sample scenario. Every method talks to MySQL with prepared statements.
 *
 * Rules of the simulation:
 *   - a resource has one instance; allocating sets its holder
 *   - requesting a free resource grants it immediately
 *   - requesting a taken resource makes the process WAITING
 *   - releasing frees the resource and wakes the first waiting process
 *   - terminating a process releases everything it holds
 */
public class SimulationService {

    // ---------- simulations ----------

    public Simulation createSimulation(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Simulation name is required.");
        }
        String sql = "INSERT INTO simulations (name) VALUES (?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
            Simulation simulation = new Simulation(name.trim());
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    simulation.setId(keys.getInt(1));
                }
            }
            return simulation;
        } catch (SQLException e) {
            throw new RuntimeException("Could not create simulation: " + e.getMessage(), e);
        }
    }

    public List<Simulation> listSimulations() {
        String sql = "SELECT simulation_id, name, DATE_FORMAT(created_at, '%d-%m-%Y %H:%i') "
                + "AS created_text FROM simulations ORDER BY simulation_id DESC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Simulation> simulations = new ArrayList<>();
            while (rs.next()) {
                Simulation simulation = new Simulation();
                simulation.setId(rs.getInt("simulation_id"));
                simulation.setName(rs.getString("name"));
                simulation.setCreatedAt(rs.getString("created_text"));
                simulations.add(simulation);
            }
            return simulations;
        } catch (SQLException e) {
            throw new RuntimeException("Could not load simulations: " + e.getMessage(), e);
        }
    }

    public Simulation openSimulation(int simulationId) {
        String sql = "SELECT simulation_id, name, "
                + "DATE_FORMAT(created_at, '%d-%m-%Y %H:%i') AS created_text "
                + "FROM simulations WHERE simulation_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Simulation does not exist.");
                }
                Simulation simulation = new Simulation();
                simulation.setId(rs.getInt("simulation_id"));
                simulation.setName(rs.getString("name"));
                simulation.setCreatedAt(rs.getString("created_text"));
                return simulation;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not open simulation: " + e.getMessage(), e);
        }
    }

    public void deleteSimulation(int simulationId) {
        // The database removes its processes and resources automatically (CASCADE).
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM simulations WHERE simulation_id = ?")) {
            ps.setInt(1, simulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete simulation: " + e.getMessage(), e);
        }
    }

    /** Creates the sample deadlock: P1 and P2 wait for each other's resource. */
    public Simulation loadSampleScenario() {
        Simulation simulation = createSimulation("Sample Scenario");
        int simulationId = simulation.getId();

        addProcess(simulationId, "P1");
        addProcess(simulationId, "P2");
        addResource(simulationId, "R1");
        addResource(simulationId, "R2");

        int p1 = findProcessId(simulationId, "P1");
        int p2 = findProcessId(simulationId, "P2");
        int r1 = findResourceId(simulationId, "R1");
        int r2 = findResourceId(simulationId, "R2");

        allocate(p1, r1);          // 1. R1 -> P1
        allocate(p2, r2);          // 2. R2 -> P2
        request(p1, r2);           // 3. P1 waits for R2
        request(p2, r1);           // 4. P2 waits for R1  -> circular wait

        return simulation;
    }

    // ---------- processes ----------

    public void addProcess(int simulationId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Process name is required.");
        }
        if (processNameExists(simulationId, name.trim())) {
            throw new IllegalArgumentException("A process named " + name.trim()
                    + " already exists.");
        }
        String sql = "INSERT INTO processes (simulation_id, name) VALUES (?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            ps.setString(2, name.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not add process: " + e.getMessage(), e);
        }
    }

    /** Deleting is only safe when the process holds no resource. */
    public void deleteProcess(int processId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Process process = getProcess(con, processId);
            if (countHeldResources(con, processId) > 0) {
                throw new IllegalArgumentException("Process " + process.getName()
                        + " still holds resources. Terminate it first.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM processes WHERE process_id = ?")) {
                ps.setInt(1, processId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete process: " + e.getMessage(), e);
        }
    }

    /**
     * Recovery: stop the process, release everything it holds, and wake up
     * the first process that was waiting for each freed resource.
     */
    public void terminateProcess(int processId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false); // one unit of work: everything or nothing
            try {
                Process process = getProcess(con, processId);
                if (Process.TERMINATED.equals(process.getStatus())) {
                    throw new IllegalArgumentException("Process " + process.getName()
                            + " is already terminated.");
                }

                // Which resources does the process hold and wait for?
                List<Integer> freedResources = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT resource_id FROM resources WHERE allocated_process_id = ?")) {
                    ps.setInt(1, processId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            freedResources.add(rs.getInt(1));
                        }
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE resources SET allocated_process_id = NULL "
                                + "WHERE allocated_process_id = ?")) {
                    ps.setInt(1, processId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE processes SET status = 'TERMINATED', "
                                + "waiting_for_resource_id = NULL WHERE process_id = ?")) {
                    ps.setInt(1, processId);
                    ps.executeUpdate();
                }
                for (int resourceId : freedResources) {
                    grantResourceToFirstWaiting(con, resourceId);
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (SQLException e) {
            throw new RuntimeException("Could not terminate process: " + e.getMessage(), e);
        }
    }

    public List<Process> getProcesses(int simulationId) {
        String sql = "SELECT p.process_id, p.simulation_id, p.name, p.status, "
                + "p.waiting_for_resource_id, r.name AS waiting_for_name "
                + "FROM processes p LEFT JOIN resources r "
                + "ON p.waiting_for_resource_id = r.resource_id "
                + "WHERE p.simulation_id = ? ORDER BY p.process_id";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Process> processes = new ArrayList<>();
                while (rs.next()) {
                    Process process = new Process();
                    process.setId(rs.getInt("process_id"));
                    process.setSimulationId(rs.getInt("simulation_id"));
                    process.setName(rs.getString("name"));
                    process.setStatus(rs.getString("status"));
                    int waitingFor = rs.getInt("waiting_for_resource_id");
                    process.setWaitingForResourceId(rs.wasNull() ? null : waitingFor);
                    process.setWaitingForName(rs.getString("waiting_for_name"));
                    processes.add(process);
                }
                return processes;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load processes: " + e.getMessage(), e);
        }
    }

    // ---------- resources ----------

    public void addResource(int simulationId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource name is required.");
        }
        if (resourceNameExists(simulationId, name.trim())) {
            throw new IllegalArgumentException("A resource named " + name.trim()
                    + " already exists.");
        }
        String sql = "INSERT INTO resources (simulation_id, name) VALUES (?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            ps.setString(2, name.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not add resource: " + e.getMessage(), e);
        }
    }

    /** Deleting is only safe when the resource is free and nobody waits for it. */
    public void deleteResource(int resourceId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Resource resource = getResource(con, resourceId);
            if (!resource.isFree()) {
                throw new IllegalArgumentException("Resource " + resource.getName()
                        + " is allocated to " + resource.getHolderName()
                        + ". Release it first.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM processes WHERE waiting_for_resource_id = ?")) {
                ps.setInt(1, resourceId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        throw new IllegalArgumentException("A process is waiting for "
                                + resource.getName() + ". Cancel the wait first.");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM resources WHERE resource_id = ?")) {
                ps.setInt(1, resourceId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete resource: " + e.getMessage(), e);
        }
    }

    public List<Resource> getResources(int simulationId) {
        String sql = "SELECT r.resource_id, r.simulation_id, r.name, "
                + "r.allocated_process_id, p.name AS holder_name "
                + "FROM resources r LEFT JOIN processes p "
                + "ON r.allocated_process_id = p.process_id "
                + "WHERE r.simulation_id = ? ORDER BY r.resource_id";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Resource> resources = new ArrayList<>();
                while (rs.next()) {
                    Resource resource = new Resource();
                    resource.setId(rs.getInt("resource_id"));
                    resource.setSimulationId(rs.getInt("simulation_id"));
                    resource.setName(rs.getString("name"));
                    int holder = rs.getInt("allocated_process_id");
                    resource.setAllocatedProcessId(rs.wasNull() ? null : holder);
                    resource.setHolderName(rs.getString("holder_name"));
                    resources.add(resource);
                }
                return resources;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load resources: " + e.getMessage(), e);
        }
    }

    // ---------- allocation ----------

    /** Gives the free resource to the process (Resource -> Process edge). */
    public void allocate(int processId, int resourceId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Process process = getProcess(con, processId);
            if (Process.TERMINATED.equals(process.getStatus())) {
                throw new IllegalArgumentException("Process " + process.getName()
                        + " is terminated.");
            }
            Resource resource = getResource(con, resourceId);
            if (!resource.isFree()) {
                throw new IllegalArgumentException("Resource " + resource.getName()
                        + " is already allocated to " + resource.getHolderName() + ".");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE resources SET allocated_process_id = ? WHERE resource_id = ?")) {
                ps.setInt(1, processId);
                ps.setInt(2, resourceId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not allocate resource: " + e.getMessage(), e);
        }
    }

    /**
     * The process asks for the resource. Free means it gets it immediately;
     * taken means the process has to WAIT (this is how deadlocks build up).
     */
    public void request(int processId, int resourceId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Process process = getProcess(con, processId);
            if (Process.TERMINATED.equals(process.getStatus())) {
                throw new IllegalArgumentException("Process " + process.getName()
                        + " is terminated.");
            }
            if (Process.WAITING.equals(process.getStatus())) {
                throw new IllegalArgumentException("Process " + process.getName()
                        + " is already waiting for " + process.getWaitingForName() + ".");
            }
            Resource resource = getResource(con, resourceId);
            if (resource.getAllocatedProcessId() != null
                    && resource.getAllocatedProcessId() == processId) {
                throw new IllegalArgumentException("Process " + process.getName()
                        + " already holds " + resource.getName() + ".");
            }
            if (resource.isFree()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE resources SET allocated_process_id = ? WHERE resource_id = ?")) {
                    ps.setInt(1, processId);
                    ps.setInt(2, resourceId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE processes SET status = 'WAITING', "
                                + "waiting_for_resource_id = ? WHERE process_id = ?")) {
                    ps.setInt(1, resourceId);
                    ps.setInt(2, processId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not request resource: " + e.getMessage(), e);
        }
    }

    /** Gives the resource back; a waiting process receives it immediately. */
    public void release(int processId, int resourceId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Resource resource = getResource(con, resourceId);
            if (resource.getAllocatedProcessId() == null
                    || resource.getAllocatedProcessId() != processId) {
                throw new IllegalArgumentException("This process does not hold "
                        + resource.getName() + ".");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE resources SET allocated_process_id = NULL WHERE resource_id = ?")) {
                ps.setInt(1, resourceId);
                ps.executeUpdate();
            }
            grantResourceToFirstWaiting(con, resourceId);
        } catch (SQLException e) {
            throw new RuntimeException("Could not release resource: " + e.getMessage(), e);
        }
    }

    // ---------- internal helpers ----------

    private Process getProcess(Connection con, int processId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT process_id, simulation_id, name, status, waiting_for_resource_id "
                        + "FROM processes WHERE process_id = ?")) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Process does not exist.");
                }
                Process process = new Process();
                process.setId(rs.getInt("process_id"));
                process.setSimulationId(rs.getInt("simulation_id"));
                process.setName(rs.getString("name"));
                process.setStatus(rs.getString("status"));
                int waitingFor = rs.getInt("waiting_for_resource_id");
                process.setWaitingForResourceId(rs.wasNull() ? null : waitingFor);
                return process;
            }
        }
    }

    private Resource getResource(Connection con, int resourceId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT resource_id, simulation_id, name, allocated_process_id "
                        + "FROM resources WHERE resource_id = ?")) {
            ps.setInt(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Resource does not exist.");
                }
                Resource resource = new Resource();
                resource.setId(rs.getInt("resource_id"));
                resource.setSimulationId(rs.getInt("simulation_id"));
                resource.setName(rs.getString("name"));
                int holder = rs.getInt("allocated_process_id");
                resource.setAllocatedProcessId(rs.wasNull() ? null : holder);
                return resource;
            }
        }
    }

    /** Wakes the first (oldest) process that waits for this resource. */
    private void grantResourceToFirstWaiting(Connection con, int resourceId)
            throws SQLException {
        Integer waitingProcessId = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT process_id FROM processes WHERE waiting_for_resource_id = ? "
                        + "AND status = 'WAITING' ORDER BY process_id LIMIT 1")) {
            ps.setInt(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    waitingProcessId = rs.getInt(1);
                }
            }
        }
        if (waitingProcessId != null) {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE processes SET status = 'RUNNING', waiting_for_resource_id = NULL "
                            + "WHERE process_id = ?")) {
                ps.setInt(1, waitingProcessId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE resources SET allocated_process_id = ? WHERE resource_id = ?")) {
                ps.setInt(1, waitingProcessId);
                ps.setInt(2, resourceId);
                ps.executeUpdate();
            }
        }
    }

    private int countHeldResources(Connection con, int processId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM resources WHERE allocated_process_id = ?")) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private boolean processNameExists(int simulationId, String name) throws RuntimeException {
        String sql = "SELECT 1 FROM processes WHERE simulation_id = ? AND name = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not check process name: " + e.getMessage(), e);
        }
    }

    private boolean resourceNameExists(int simulationId, String name) throws RuntimeException {
        String sql = "SELECT 1 FROM resources WHERE simulation_id = ? AND name = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, simulationId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not check resource name: " + e.getMessage(), e);
        }
    }

    private int findProcessId(int simulationId, String name) {
        for (Process process : getProcesses(simulationId)) {
            if (process.getName().equals(name)) {
                return process.getId();
            }
        }
        throw new IllegalArgumentException("Process " + name + " not found.");
    }

    private int findResourceId(int simulationId, String name) {
        for (Resource resource : getResources(simulationId)) {
            if (resource.getName().equals(name)) {
                return resource.getId();
            }
        }
        throw new IllegalArgumentException("Resource " + name + " not found.");
    }
}
