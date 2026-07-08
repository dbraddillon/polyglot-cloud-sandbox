package dev.sandbox.lab.eventsapi.web;

import jakarta.validation.constraints.NotBlank;

public record CreateEventRequest(@NotBlank String type, @NotBlank String payload) {
}
