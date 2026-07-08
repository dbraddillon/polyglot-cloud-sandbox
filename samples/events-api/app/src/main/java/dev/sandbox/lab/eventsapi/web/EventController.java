package dev.sandbox.lab.eventsapi.web;

import dev.sandbox.lab.eventsapi.domain.EventMessage;
import dev.sandbox.lab.eventsapi.domain.ProcessedEvent;
import dev.sandbox.lab.eventsapi.repository.ProcessedEventStore;
import dev.sandbox.lab.eventsapi.service.EventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventPublisher publisher;
    private final ProcessedEventStore store;

    public EventController(EventPublisher publisher, ProcessedEventStore store) {
        this.publisher = publisher;
        this.store = store;
    }

    // 202 Accepted, not 201 Created - the event is published, not yet processed. There's
    // nothing to point a Location header at until the consumer picks it up, which might be a
    // couple of seconds away (or, if you GET fast enough, hasn't happened at all yet - see the
    // README on the eventual-consistency window this creates).
    @PostMapping
    public ResponseEntity<EventMessage> publish(@Valid @RequestBody CreateEventRequest request) {
        EventMessage message = publisher.publish(request.type(), request.payload());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(message);
    }

    @GetMapping
    public List<ProcessedEvent> list() {
        return store.findAll();
    }

    @GetMapping("/{id}")
    public ProcessedEvent get(@PathVariable String id) {
        return store.findById(id);
    }
}
