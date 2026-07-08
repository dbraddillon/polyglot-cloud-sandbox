package dev.sandbox.lab.taskapi.web;

import dev.sandbox.lab.taskapi.domain.Task;
import dev.sandbox.lab.taskapi.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

// @RestController = @Controller + @ResponseBody - closest match is an ASP.NET Core
// [ApiController] controller where every method's return value is serialized straight to the
// response body (JSON by default) instead of resolving a view.
@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> list() {
        return service.list().stream().map(TaskResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable UUID id) {
        return TaskResponse.from(service.get(id));
    }

    // @Valid triggers Jakarta Bean Validation on the request body - like [ApiController]'s
    // automatic ModelState validation, a bad request short-circuits into a 400 before this
    // method body even runs (Spring Boot's default handler covers that; see ApiExceptionHandler
    // for the cases handled explicitly here).
    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = service.create(request.title());
        // ResponseEntity is Java's ActionResult<T> - status, headers, and body together.
        // .created(uri) ~ C#'s CreatedAtAction(...): a 201 plus a Location header pointing at
        // the new resource, instead of building both by hand.
        return ResponseEntity.created(URI.create("/tasks/" + task.getId())).body(TaskResponse.from(task));
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return TaskResponse.from(service.updateStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
