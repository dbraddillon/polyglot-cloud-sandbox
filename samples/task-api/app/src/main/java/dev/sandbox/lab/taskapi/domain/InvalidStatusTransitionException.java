package dev.sandbox.lab.taskapi.domain;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("Cannot move task from %s to %s".formatted(from, to));
    }
}
