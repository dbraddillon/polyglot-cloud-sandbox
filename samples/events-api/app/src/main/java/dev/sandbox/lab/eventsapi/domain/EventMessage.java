package dev.sandbox.lab.eventsapi.domain;

// A plain record - this is what gets JSON-serialized straight onto the SNS topic, and it's pure
// data with no persistence framework touching it, so unlike Task/Order/Product there's no
// reason this can't be immutable.
public record EventMessage(String id, String type, String payload) {
}
