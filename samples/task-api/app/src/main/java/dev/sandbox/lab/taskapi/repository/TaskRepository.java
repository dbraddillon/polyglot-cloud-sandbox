package dev.sandbox.lab.taskapi.repository;

import dev.sandbox.lab.taskapi.domain.Task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// An interface + a single implementation - the same shape as a C# ITaskRepository/TaskRepository
// pair. Spring wires whichever @Component implements this into anything that asks for a
// TaskRepository, conceptually the same as `services.AddScoped<ITaskRepository, TaskRepository>()`
// in Program.cs, just found via classpath scanning instead of an explicit registration line.
public interface TaskRepository {
    List<Task> findAll();

    Optional<Task> findById(UUID id);

    Task save(Task task);

    void deleteById(UUID id);
}
