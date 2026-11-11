package com.paradox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

/**
 * The one controller of the application. It fills the three tabs
 * (Dashboard, Simulation, Deadlock Detection), reacts to every button and
 * draws the resource allocation graph on a canvas.
 *
 * After every action the tables are loaded again from MySQL, so the screen
 * always shows exactly what the database stores.
 */
public class SimulationController {

    private final SimulationService service = new SimulationService();

    private Simulation current;                 // the open simulation (may be null)
    private List<Process> processes = List.of();
    private List<Resource> resources = List.of();
    private DeadlockDetector.Result lastResult = null;

    // ---------- header ----------
    @FXML private TextField simulationNameField;
    @FXML private ComboBox<Simulation> simulationCombo;

    // ---------- dashboard tab ----------
    @FXML private Label totalProcessesValue;
    @FXML private Label totalResourcesValue;
    @FXML private Label availableResourcesValue;
    @FXML private Label allocatedResourcesValue;
    @FXML private Label deadlockStatusValue;

    // ---------- simulation tab ----------
    @FXML private TextField processNameField;
    @FXML private TableView<Process> processTable;
    @FXML private TableColumn<Process, String> colProcessName;
    @FXML private TableColumn<Process, String> colProcessStatus;
    @FXML private TableColumn<Process, String> colProcessWaiting;
    @FXML private TextField resourceNameField;
    @FXML private TableView<Resource> resourceTable;
    @FXML private TableColumn<Resource, String> colResourceName;
    @FXML private TableColumn<Resource, String> colResourceStatus;
    @FXML private TableColumn<Resource, String> colResourceHolder;
    @FXML private ComboBox<Process> processCombo;
    @FXML private ComboBox<Resource> resourceCombo;

    // ---------- deadlock detection tab ----------
    @FXML private Label deadlockResultLabel;
    @FXML private Canvas graphCanvas;
    @FXML private TextArea edgesArea;
    @FXML private Label involvedLabel;
    @FXML private ComboBox<Process> deadlockedProcessCombo;
    @FXML private Button terminateInvolvedButton;
    @FXML private Label recoveryLabel;
    @FXML private Label cycleLabel;

    @FXML
    private void initialize() {
        colProcessName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colProcessStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProcessWaiting.setCellValueFactory(new PropertyValueFactory<>("waitingForName"));
        colResourceName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colResourceStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colResourceHolder.setCellValueFactory(new PropertyValueFactory<>("holderName"));

        simulationCombo.setConverter(simpleConverter());
        simulationCombo.setButtonCell(simpleCell());
        processCombo.setConverter(processConverter());
        processCombo.setButtonCell(processCell());
        deadlockedProcessCombo.setConverter(processConverter());
        deadlockedProcessCombo.setButtonCell(processCell());
        resourceCombo.setConverter(resourceConverter());
        resourceCombo.setButtonCell(resourceCell());

        refreshAll();
    }

    // ================= header actions =================

    @FXML
    private void createSimulation() {
        try {
            current = service.createSimulation(simulationNameField.getText());
            simulationNameField.clear();
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void openSimulation() {
        Simulation selected = simulationCombo.getValue();
        if (selected == null) {
            showInfo("Select a simulation in the list first.");
            return;
        }
        try {
            current = service.openSimulation(selected.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void deleteSimulation() {
        Simulation selected = simulationCombo.getValue();
        if (selected == null) {
            showInfo("Select a simulation in the list first.");
            return;
        }
        if (!confirm("Delete simulation '" + selected.getName()
                + "' with all its processes and resources?")) {
            return;
        }
        try {
            service.deleteSimulation(selected.getId());
            if (current != null && current.getId() == selected.getId()) {
                current = null;
            }
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void loadSampleScenario() {
        try {
            current = service.loadSampleScenario();
            refreshAll();
            // Show the deadlock of the sample scenario right away.
            runDetection();
        } catch (Exception e) {
            showError(e);
        }
    }

    // ================= process actions =================

    @FXML
    private void addProcess() {
        if (requireSimulation()) {
            return;
        }
        try {
            service.addProcess(current.getId(), processNameField.getText());
            processNameField.clear();
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void terminateSelectedProcess() {
        Process selected = selectedProcess();
        if (selected == null) {
            showInfo("Select a process in the table first.");
            return;
        }
        if (!confirm("Terminate " + selected.getName()
                + "? All resources it holds are released.")) {
            return;
        }
        try {
            service.terminateProcess(selected.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void deleteSelectedProcess() {
        Process selected = selectedProcess();
        if (selected == null) {
            showInfo("Select a process in the table first.");
            return;
        }
        if (!confirm("Delete process " + selected.getName() + "?")) {
            return;
        }
        try {
            service.deleteProcess(selected.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    // ================= resource actions =================

    @FXML
    private void addResource() {
        if (requireSimulation()) {
            return;
        }
        try {
            service.addResource(current.getId(), resourceNameField.getText());
            resourceNameField.clear();
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void deleteSelectedResource() {
        Resource selected = selectedResource();
        if (selected == null) {
            showInfo("Select a resource in the table first.");
            return;
        }
        if (!confirm("Delete resource " + selected.getName() + "?")) {
            return;
        }
        try {
            service.deleteResource(selected.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    // ================= allocation actions =================

    @FXML
    private void allocate() {
        if (requireSimulation()) {
            return;
        }
        Process process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            showInfo("Choose a process and a resource first.");
            return;
        }
        try {
            service.allocate(process.getId(), resource.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void request() {
        if (requireSimulation()) {
            return;
        }
        Process process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            showInfo("Choose a process and a resource first.");
            return;
        }
        try {
            service.request(process.getId(), resource.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    @FXML
    private void release() {
        if (requireSimulation()) {
            return;
        }
        Process process = processCombo.getValue();
        Resource resource = resourceCombo.getValue();
        if (process == null || resource == null) {
            showInfo("Choose a process and a resource first.");
            return;
        }
        try {
            service.release(process.getId(), resource.getId());
            refreshAll();
        } catch (Exception e) {
            showError(e);
        }
    }

    // ================= deadlock detection =================

    @FXML
    private void detectDeadlock() {
        if (requireSimulation()) {
            return;
        }
        runDetection();
    }

    @FXML
    private void terminateInvolvedProcess() {
        Process selected = deadlockedProcessCombo.getValue();
        if (selected == null) {
            showInfo("There is no deadlocked process to terminate.");
            return;
        }
        if (!confirm("Terminate " + selected.getName()
                + "? Its resources are released and the deadlock is checked again.")) {
            return;
        }
        try {
            service.terminateProcess(selected.getId());
            refreshAll();          // reloads everything and runs detection again
            if (lastResult != null && !lastResult.deadlock) {
                recoveryLabel.setText("DEADLOCK RESOLVED - no cycle in the graph anymore.");
                recoveryLabel.setStyle("-fx-text-fill: #1c7c46; -fx-font-weight: bold;");
            } else {
                recoveryLabel.setText("The deadlock is still present.");
                recoveryLabel.setStyle("-fx-text-fill: #b32222; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    /**
     * Runs the detector and updates the whole deadlock tab. It is called
     * after every change (so the graph is never out of date) and by the
     * "Detect Deadlock" button.
     */
    private void runDetection() {
        lastResult = DeadlockDetector.detect(resources, processes);

        if (lastResult.deadlock) {
            deadlockResultLabel.setText("DEADLOCK DETECTED");
            deadlockResultLabel.setStyle("-fx-background-color: #fde8e8; -fx-text-fill: #b32222;");
            cycleLabel.setText(lastResult.explanation);
        } else {
            deadlockResultLabel.setText("NO DEADLOCK");
            deadlockResultLabel.setStyle("-fx-background-color: #e2f5ea; -fx-text-fill: #1c7c46;");
            cycleLabel.setText("");
        }
        recoveryLabel.setText("");

        involvedLabel.setText(lastResult.deadlock
                ? "Deadlocked processes: " + String.join(", ", lastResult.involvedProcesses)
                : "Deadlocked processes: -");

        List<Process> involved = processes.stream()
                .filter(p -> lastResult.involvedProcesses.contains(p.getName()))
                .toList();
        deadlockedProcessCombo.setItems(FXCollections.observableArrayList(involved));
        terminateInvolvedButton.setDisable(involved.isEmpty());

        edgesArea.setText(buildEdgeText());

        drawGraph();
    }

    // ================= display refresh =================

    private void refreshAll() {
        loadSimulationList();

        if (current != null) {
            processes = service.getProcesses(current.getId());
            resources = service.getResources(current.getId());
        } else {
            processes = List.of();
            resources = List.of();
        }

        processTable.setItems(FXCollections.observableArrayList(processes));
        resourceTable.setItems(FXCollections.observableArrayList(resources));

        List<Process> activeProcesses = processes.stream()
                .filter(p -> !Process.TERMINATED.equals(p.getStatus()))
                .toList();
        processCombo.setItems(FXCollections.observableArrayList(activeProcesses));
        resourceCombo.setItems(FXCollections.observableArrayList(resources));

        refreshDashboard();
        runDetection();
    }

    private void refreshDashboard() {
        if (current == null) {
            totalProcessesValue.setText("-");
            totalResourcesValue.setText("-");
            availableResourcesValue.setText("-");
            allocatedResourcesValue.setText("-");
            deadlockStatusValue.setText("No simulation open");
            deadlockStatusValue.setStyle("-fx-text-fill: #6b7890;");
            return;
        }
        long allocated = resources.stream().filter(r -> !r.isFree()).count();
        long available = resources.size() - allocated;

        totalProcessesValue.setText(String.valueOf(processes.size()));
        totalResourcesValue.setText(String.valueOf(resources.size()));
        availableResourcesValue.setText(String.valueOf(available));
        allocatedResourcesValue.setText(String.valueOf(allocated));

        if (lastResult != null && lastResult.deadlock) {
            deadlockStatusValue.setText("DEADLOCK DETECTED");
            deadlockStatusValue.setStyle("-fx-text-fill: #b32222; -fx-font-weight: bold;");
        } else {
            deadlockStatusValue.setText("No deadlock");
            deadlockStatusValue.setStyle("-fx-text-fill: #1c7c46; -fx-font-weight: bold;");
        }
    }

    private void loadSimulationList() {
        List<Simulation> simulations = service.listSimulations();
        simulationCombo.setItems(FXCollections.observableArrayList(simulations));
        if (current != null) {
            simulations.stream()
                    .filter(s -> s.getId() == current.getId())
                    .findFirst()
                    .ifPresent(simulationCombo.getSelectionModel()::select);
        }
    }

    /** Builds the simple text list of graph edges, useful for explaining. */
    private String buildEdgeText() {
        StringBuilder text = new StringBuilder();
        text.append("Allocations (Resource -> Process):\n");
        boolean any = false;
        for (Resource resource : resources) {
            if (resource.getAllocatedProcessId() != null) {
                text.append("  ").append(resource.getName()).append(" -> ")
                        .append(resource.getHolderName()).append("\n");
                any = true;
            }
        }
        if (!any) {
            text.append("  (none)\n");
        }
        text.append("\nRequests (Process -> Resource):\n");
        any = false;
        for (Process process : processes) {
            if (Process.WAITING.equals(process.getStatus())
                    && process.getWaitingForResourceId() != null) {
                text.append("  ").append(process.getName()).append(" -> ")
                        .append(process.getWaitingForName()).append("\n");
                any = true;
            }
        }
        if (!any) {
            text.append("  (none)");
        }
        return text.toString();
    }

    // ================= graph drawing =================

    /** Draws the resource allocation graph: circles = processes, squares = resources. */
    private void drawGraph() {
        GraphicsContext g = graphCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, graphCanvas.getWidth(), graphCanvas.getHeight());

        if (current == null) {
            g.setFill(Color.GRAY);
            g.fillText("Create or open a simulation to see the graph.", 20, 30);
            return;
        }

        int rows = Math.max(processes.size(), resources.size());
        graphCanvas.setWidth(640);
        graphCanvas.setHeight(Math.max(280, 60 + rows * 75));

        // Positions of all nodes.
        Map<String, double[]> position = new HashMap<>();
        for (int i = 0; i < processes.size(); i++) {
            position.put("P" + processes.get(i).getId(), new double[]{110, 50 + i * 75});
        }
        for (int i = 0; i < resources.size(); i++) {
            position.put("R" + resources.get(i).getId(), new double[]{480, 50 + i * 75});
        }

        Set<String> cycleNodes = new HashSet<>(
                lastResult == null ? List.of() : lastResult.cycleKeys);

        // Edges first, so the nodes are drawn on top of them.
        g.setLineWidth(2);
        for (Resource resource : resources) {
            if (resource.getAllocatedProcessId() != null) {
                boolean inCycle = cycleNodes.contains("R" + resource.getId())
                        && cycleNodes.contains("P" + resource.getAllocatedProcessId());
                drawArrow(g, position.get("R" + resource.getId()),
                        position.get("P" + resource.getAllocatedProcessId()),
                        inCycle ? Color.valueOf("#b32222") : Color.valueOf("#2f6fed"),
                        false, 30, 32);
            }
        }
        for (Process process : processes) {
            if (Process.WAITING.equals(process.getStatus())
                    && process.getWaitingForResourceId() != null) {
                boolean inCycle = cycleNodes.contains("P" + process.getId())
                        && cycleNodes.contains("R" + process.getWaitingForResourceId());
                drawArrow(g, position.get("P" + process.getId()),
                        position.get("R" + process.getWaitingForResourceId()),
                        inCycle ? Color.valueOf("#b32222") : Color.valueOf("#e08a2e"),
                        true, 30, 32);
            }
        }

        // Process nodes: circles.
        for (Process process : processes) {
            double[] p = position.get("P" + process.getId());
            boolean inCycle = cycleNodes.contains("P" + process.getId());
            g.setFill(inCycle ? Color.valueOf("#d64545") : Color.valueOf("#2f6fed"));
            g.fillOval(p[0] - 24, p[1] - 24, 48, 48);
            g.setFill(Color.WHITE);
            g.fillText(process.getName(), p[0] - process.getName().length() * 4.0, p[1] + 4);
        }

        // Resource nodes: squares.
        for (Resource resource : resources) {
            double[] p = position.get("R" + resource.getId());
            boolean inCycle = cycleNodes.contains("R" + resource.getId());
            g.setFill(inCycle ? Color.valueOf("#d64545") : Color.valueOf("#e08a2e"));
            g.fillRect(p[0] - 22, p[1] - 22, 44, 44);
            g.setFill(Color.WHITE);
            g.fillText(resource.getName(), p[0] - resource.getName().length() * 4.0, p[1] + 4);
        }

        // Legend.
        g.setFill(Color.GRAY);
        g.fillText("circle = process     square = resource     solid arrow = allocated     "
                + "dashed arrow = waiting     red = deadlock cycle", 14,
                graphCanvas.getHeight() - 12);
    }

    /** Draws one arrow with an arrowhead, shortened by the node size. */
    private void drawArrow(GraphicsContext g, double[] from, double[] to,
                           Color color, boolean dashed,
                           double startGap, double endGap) {
        if (from == null || to == null) {
            return;
        }
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double startX = from[0] + ux * startGap;
        double startY = from[1] + uy * startGap;
        double endX = to[0] - ux * endGap;
        double endY = to[1] - uy * endGap;

        g.setStroke(color);
        if (dashed) {
            g.setLineDashes(7, 5);
        } else {
            g.setLineDashes();
        }
        g.strokeLine(startX, startY, endX, endY);

        double size = 9;
        double px = -uy;
        double py = ux;
        g.setFill(color);
        g.fillPolygon(
                new double[]{endX,
                        endX - ux * size + px * size / 2,
                        endX - ux * size - px * size / 2},
                new double[]{endY,
                        endY - uy * size + py * size / 2,
                        endY - uy * size - py * size / 2},
                3);
        g.setLineDashes();
    }

    // ================= small helpers =================

    private boolean requireSimulation() {
        if (current == null) {
            showInfo("Create or open a simulation first (top of the window).");
            return true;
        }
        return false;
    }

    private Process selectedProcess() {
        return processTable.getSelectionModel().getSelectedItem();
    }

    private Resource selectedResource() {
        return resourceTable.getSelectionModel().getSelectedItem();
    }

    private javafx.util.StringConverter<Simulation> simpleConverter() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Simulation simulation) {
                if (simulation == null) {
                    return "";
                }
                return simulation.getName() + " (" + simulation.getCreatedAt() + ")";
            }

            @Override
            public Simulation fromString(String text) {
                return null; // read-only combo box
            }
        };
    }

    private javafx.scene.control.ListCell<Simulation> simpleCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Simulation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : simpleConverter().toString(item));
            }
        };
    }

    private javafx.util.StringConverter<Process> processConverter() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Process process) {
                return process == null ? "" : process.getName() + " [" + process.getStatus() + "]";
            }

            @Override
            public Process fromString(String text) {
                return null; // read-only combo box
            }
        };
    }

    private javafx.scene.control.ListCell<Process> processCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Process item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : processConverter().toString(item));
            }
        };
    }

    private javafx.util.StringConverter<Resource> resourceConverter() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Resource resource) {
                return resource == null ? "" : resource.getName() + " [" + resource.getStatus() + "]";
            }

            @Override
            public Resource fromString(String text) {
                return null; // read-only combo box
            }
        };
    }

    private javafx.scene.control.ListCell<Resource> resourceCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Resource item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : resourceConverter().toString(item));
            }
        };
    }

    private void showError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Paradox");
        alert.setHeaderText("Action failed");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Paradox");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean confirm(String question) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Paradox");
        alert.setHeaderText(null);
        alert.setContentText(question);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }
}
