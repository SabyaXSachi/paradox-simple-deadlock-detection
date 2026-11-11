package com.paradox;

/**
 * One saved simulation. It groups the processes and resources that belong
 * together, exactly like one row of the simulations table.
 */
public class Simulation {

    private int id;
    private String name;
    private String createdAt;

    public Simulation() {
    }

    public Simulation(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
