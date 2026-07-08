package dev.sandbox.lab.taskapi.service;

import dev.sandbox.lab.taskapi.domain.InvalidStatusTransitionException;
import dev.sandbox.lab.taskapi.domain.Task;
import dev.sandbox.lab.taskapi.domain.TaskNotFoundException;
import dev.sandbox.lab.taskapi.domain.TaskStatus;
import dev.sandbox.lab.taskapi.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// @Service is another @Component specialization - purely a naming convention to say "this bean
// holds business logic," Spring treats it identically to @Repository under the hood.
// Constructor injection (no @Autowired needed on a single constructor since Spring 4.3) is the
// same shape as a C# service class taking its dependencies through the constructor.
@Service
public class TaskService {
    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public List<Task> list() {
        return repository.findAll();
    }

    public Task get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    public Task create(String title) {
        Task task = new Task(UUID.randomUUID(), title, TaskStatus.TODO, Instant.now());
        return repository.save(task);
    }

    public Task updateStatus(UUID id, TaskStatus newStatus) {
        Task task = get(id);
        // A small business rule to fake some real domain logic: a task can only be marked
        // DONE once it's actually IN_PROGRESS - no skipping straight from TODO to DONE.
        if (newStatus == TaskStatus.DONE && task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new InvalidStatusTransitionException(task.getStatus(), newStatus);
        }
        task.updateStatus(newStatus);
        return repository.save(task);
    }

    public void delete(UUID id) {
        get(id); // throws TaskNotFoundException if missing, so DELETE 404s like GET does
        repository.deleteById(id);
    }
}
