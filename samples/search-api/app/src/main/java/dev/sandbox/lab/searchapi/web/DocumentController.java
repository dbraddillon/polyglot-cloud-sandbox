package dev.sandbox.lab.searchapi.web;

import dev.sandbox.lab.searchapi.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentController {
    private final SearchService service;

    public DocumentController(SearchService service) {
        this.service = service;
    }

    @PostMapping
    public DocumentResponse index(@Valid @RequestBody IndexDocumentRequest request) {
        return service.index(request.title(), request.body());
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/search")
    public List<DocumentResponse> search(@RequestParam String q) {
        return service.search(q);
    }
}
