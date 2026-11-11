# Paradox – Simple Deadlock Detection System

A small academic JavaFX + MySQL application that demonstrates the basic
Operating Systems deadlock concept:

1. How processes use resources.
2. How deadlocks occur (circular waiting).
3. How the system detects a deadlock (Resource Allocation Graph + DFS).
4. How terminating a process resolves the deadlock.

Built with **Java 17, JavaFX, FXML, CSS, MySQL and Maven**. The whole logic
lives in 8 small classes so the project is easy to read and explain.

## How it works (2-minute explanation)

- A **resource** (R1, R2, ...) has exactly **one instance**.
- **Allocate** gives a free resource to a process (graph edge `Resource → Process`).
- **Request** gives a free resource to a process; if it is taken, the process
  **waits** (graph edge `Process → Resource`).
- The **DeadlockDetector** builds this graph and runs a depth-first search.
  With single-instance resources, **a cycle = deadlock**.
- **Terminating** one process in the cycle releases its resources, waiting
  processes receive them, and the deadlock is gone.

## Prerequisites

| Software | Notes |
|---|---|
| JDK 17 or newer | the project is compiled for Java 17 |
| Maven | `mvn -v` must work |
| MySQL Server 8+ | running on localhost:3306 |

## Database setup (once)

1. Create the database (using your own root password):

   ```sql
   CREATE DATABASE paradox_db CHARACTER SET utf8mb4;
   ```

2. Create the three tables:

   ```bash
   mysql -u root -p paradox_db < database/schema.sql
   ```

3. Enter your MySQL password in `config/database.properties`
   (copy `config/database.properties.example`; the real file is gitignored).

## Run

```bash
mvn javafx:run
```

## Test

```bash
mvn clean verify
```

The tests cover the deadlock detection (no deadlock, two-process cycle,
three-process circular wait, waiting chains) and the full sample scenario
against the real database.

## The three screens

| Screen | What it shows |
|---|---|
| **Dashboard** | Total processes, total resources, available/allocated resources, current deadlock status |
| **Simulation** | Add/terminate/delete processes, add/delete resources, Allocate / Request / Release buttons, live tables |
| **Deadlock Detection** | The drawn resource allocation graph, Detect Deadlock button, deadlocked processes, Terminate Process button, recovery result |

## 5-minute class demonstration

1. Click **Load Sample Scenario** - P1 holds R1, P2 holds R2, and each waits
   for the other's resource. The graph shows the cycle and the system reports
   **DEADLOCK DETECTED** with the cycle `P1 → R2 → P2 → R1 → P1`.
2. Explain the graph: solid arrows are allocations (`R → P`), dashed arrows
   are requests (`P → R`), red means "part of the deadlock cycle".
3. On the **Deadlock Detection** tab select **P2** and press
   **Terminate Process** - its resources are released, the waiting process
   receives them, detection runs again and reports **NO DEADLOCK**.
4. Show the **Dashboard** - the status flipped from deadlock to no deadlock.
5. Everything you did is stored in MySQL (table `simulations`, `processes`,
   `resources`) - close the app, reopen the simulation from the list.

## Project structure

```
database/schema.sql                 3 tables (simulations, processes, resources)
config/database.properties          MySQL credentials (gitignored)
src/main/java/com/paradox/
    MainApp.java                    entry point
    Simulation.java                 model: one saved simulation
    Process.java                    model: status + what it waits for
    Resource.java                   model: who holds the single instance
    DeadlockDetector.java           pure DFS cycle detection (no UI, no SQL)
    SimulationService.java          all logic + JDBC prepared statements
    DatabaseConnection.java         reads config, returns connections
    SimulationController.java       one controller for the three tabs
src/main/resources/
    fxml/simulation.fxml            the window with the three tabs
    css/styles.css                  the stylesheet
src/test/java/com/paradox/          detector tests + full scenario test
```
