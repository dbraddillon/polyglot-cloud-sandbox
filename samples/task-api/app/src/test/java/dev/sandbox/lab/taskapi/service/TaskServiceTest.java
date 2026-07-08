package dev.sandbox.lab.taskapi.service;

import dev.sandbox.lab.taskapi.domain.InvalidStatusTransitionException;
import dev.sandbox.lab.taskapi.domain.Task;
import dev.sandbox.lab.taskapi.domain.TaskNotFoundException;
import dev.sandbox.lab.taskapi.domain.TaskStatus;
import dev.sandbox.lab.taskapi.repository.InMemoryTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// JUnit 5 is Java's xUnit/NUnit. @Test works the same way; assertions here use AssertJ's fluent
// assertThat(...) style, which reads a lot like FluentAssertions in .NET (the two libraries
// actually influenced each other over the years). No [TestClass]/[TestMethod]-style attributes
// on the class itself - a plain class with @Test methods is enough.
class TaskServiceTest {
    private final TaskService service = new TaskService(new InMemoryTaskRepository());

    @Test
    void createStartsInTodo() {
        Task task = service.create("Write the README");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void cannotJumpStraightToDone() {
        Task task = service.create("Ship it");
        assertThatThrownBy(() -> service.updateStatus(task.getId(), TaskStatus.DONE))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void canCompleteAfterInProgress() {
        Task task = service.create("Ship it");
        service.updateStatus(task.getId(), TaskStatus.IN_PROGRESS);
        Task done = service.updateStatus(task.getId(), TaskStatus.DONE);
        assertThat(done.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void missingTaskThrows() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
