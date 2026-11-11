package com.paradox;

/**
 * A process in the simulation.
 *
 * Status values:
 *   RUNNING   - the process is working (it may hold resources)
 *   WAITING   - the process asked for a resource that is taken by someone else
 *   TERMINATED - the process was stopped, it holds nothing anymore
 *
 * waitingForResourceId is only set while the process is WAITING; it is the
 * request edge (Process -> Resource) of the resource allocation graph.
 */
public class Process {

    public static final String RUNNING = "RUNNING";
    public static final String WAITING = "WAITING";
    public static final String TERMINATED = "TERMINATED";

    private int id;
    private int simulationId;
    private String name;
    private String status = RUNNING;
    private Integer waitingForResourceId;
    private String waitingForName;   // display name, filled by the service

    public Process() {
    }

    public Process(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(int simulationId) {
        this.simulationId = simulationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getWaitingForResourceId() {
        return waitingForResourceId;
    }

    public void setWaitingForResourceId(Integer waitingForResourceId) {
        this.waitingForResourceId = waitingForResourceId;
    }

    public String getWaitingForName() {
        return waitingForName == null ? "-" : waitingForName;
    }

    public void setWaitingForName(String waitingForName) {
        this.waitingForName = waitingForName;
    }
}
