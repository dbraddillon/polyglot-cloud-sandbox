package dev.sandbox.lab.taskapi.repository;

import dev.sandbox.lab.taskapi.domain.Task;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// @Repository marks this as a Spring-managed bean (a specialization of @Component) that Spring
// finds via classpath scanning at startup. In-memory only for this sample - no real database
// yet; see the root README for why and what a persistence-backed sample would add.
@Repository
public class InMemoryTaskRepository implements TaskRepository {
    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    @Override
    public List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public Optional<Task> findById(UUID id) {
        // Optional<T> forces the caller to explicitly deal with "might not be there" - closer
        // to a hard wrapper type than C#'s nullable reference types, which are more of a
        // compiler hint than something the runtime enforces.
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public Task save(Task task) {
        tasks.put(task.getId(), task);
        return task;
    }

    @Override
    public void deleteById(UUID id) {
        tasks.remove(id);
    }
}
