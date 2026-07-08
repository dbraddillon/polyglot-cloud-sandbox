package dev.sandbox.lab.eventsapi.repository;

import dev.sandbox.lab.eventsapi.domain.EventNotFoundException;
import dev.sandbox.lab.eventsapi.domain.ProcessedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// In-memory only, same idea as task-api's InMemoryTaskRepository - this sample is about the
// publish/subscribe/consume pipeline, not persistence, so there's nothing behind this beyond a
// map. A real system would persist processed events somewhere durable.
@Component
public class ProcessedEventStore {
    private final Map<String, ProcessedEvent> events = new ConcurrentHashMap<>();

    public void save(ProcessedEvent event) {
        events.put(event.id(), event);
    }

    public List<ProcessedEvent> findAll() {
        return List.copyOf(events.values());
    }

    public ProcessedEvent findById(String id) {
        ProcessedEvent event = events.get(id);
        if (event == null) {
            throw new EventNotFoundException(id);
        }
        return event;
    }
}
