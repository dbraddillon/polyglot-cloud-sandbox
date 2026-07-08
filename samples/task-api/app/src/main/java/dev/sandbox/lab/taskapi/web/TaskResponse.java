package dev.sandbox.lab.taskapi.web;

import dev.sandbox.lab.taskapi.domain.Task;
import dev.sandbox.lab.taskapi.domain.TaskStatus;

import java.time.Instant;
import java.util.UUID;

// Record accessors are named after the field, not getX() - it's task.id(), not task.getId().
// That's the trade for the brevity: it only reads naturally once you know the convention.
public record TaskResponse(UUID id, String title, TaskStatus status, Instant createdAt) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getStatus(), task.getCreatedAt());
    }
}
