package dev.sandbox.lab.claimsapi.web;

import dev.sandbox.lab.claimsapi.domain.ClaimLineItem;
import dev.sandbox.lab.claimsapi.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/claims")
public class ClaimController {
    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClaimResponse> list() {
        return service.list().stream().map(ClaimResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@PathVariable UUID id) {
        return ClaimResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> create(@Valid @RequestBody CreateClaimRequest request) {
        List<ClaimLineItem> items = request.lineItems().stream()
                .map(li -> new ClaimLineItem(li.serviceDescription(), li.quantity(), li.unitPrice()))
                .toList();
        var claim = service.create(request.memberName(), items);
        return ResponseEntity.created(URI.create("/claims/" + claim.getId()))
                .body(ClaimResponse.from(claim));
    }

    @PostMapping("/{id}/approve")
    public ClaimResponse approve(@PathVariable UUID id) {
        return ClaimResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/deny")
    public ClaimResponse deny(@PathVariable UUID id) {
        return ClaimResponse.from(service.deny(id));
    }
}
