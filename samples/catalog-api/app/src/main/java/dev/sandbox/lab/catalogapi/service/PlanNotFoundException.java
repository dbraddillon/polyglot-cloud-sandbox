package dev.sandbox.lab.catalogapi.service;

public class PlanNotFoundException extends RuntimeException {
    public PlanNotFoundException(String id) {
        super("Plan not found: " + id);
    }
}
