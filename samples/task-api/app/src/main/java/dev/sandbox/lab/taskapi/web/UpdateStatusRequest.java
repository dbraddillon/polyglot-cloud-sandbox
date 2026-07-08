package dev.sandbox.lab.taskapi.web;

import dev.sandbox.lab.taskapi.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull TaskStatus status) {
}
