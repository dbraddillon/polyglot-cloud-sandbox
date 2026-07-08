package dev.sandbox.lab.catalogapi.web;

import dev.sandbox.lab.catalogapi.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/plans")
public class PlanController {
    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanResponse> list() {
        return service.list().stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable String id) {
        return PlanResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        var plan = service.create(request.name(), request.monthlyPremium());
        return ResponseEntity.created(URI.create("/plans/" + plan.getId()))
                .body(PlanResponse.from(plan));
    }

    @PutMapping("/{id}")
    public PlanResponse update(@PathVariable String id, @Valid @RequestBody PlanRequest request) {
        return PlanResponse.from(service.update(id, request.name(), request.monthlyPremium()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
