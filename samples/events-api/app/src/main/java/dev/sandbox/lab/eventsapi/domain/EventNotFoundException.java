package dev.sandbox.lab.eventsapi.domain;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String id) {
        super("Event not found (not yet processed?): " + id);
    }
}
