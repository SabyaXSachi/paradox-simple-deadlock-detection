package com.paradox;

/**
 * A resource with exactly one instance.
 *
 * allocatedProcessId is the process that currently holds the instance.
 * It is null while the resource is free. This is the allocation edge
 * (Resource -> Process) of the resource allocation graph.
 */
public class Resource {

    private int id;
    private int simulationId;
    private String name;
    private Integer allocatedProcessId;
    private String holderName;   // display name, filled by the service

    public Resource() {
    }

    public Resource(String name) {
        this.name = name;
    }

    /** "Available" when nobody holds it, "Allocated" otherwise. */
    public String getStatus() {
        return allocatedProcessId == null ? "Available" : "Allocated";
    }

    public boolean isFree() {
        return allocatedProcessId == null;
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

    public Integer getAllocatedProcessId() {
        return allocatedProcessId;
    }

    public void setAllocatedProcessId(Integer allocatedProcessId) {
        this.allocatedProcessId = allocatedProcessId;
    }

    public String getHolderName() {
        return holderName == null ? "-" : holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }
}
