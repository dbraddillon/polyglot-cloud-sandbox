package dev.sandbox.lab.eventsapi.domain;

import java.time.Instant;

public record ProcessedEvent(String id, String type, String payload, Instant receivedAt) {
    public static ProcessedEvent from(EventMessage message) {
        return new ProcessedEvent(message.id(), message.type(), message.payload(), Instant.now());
    }
}
