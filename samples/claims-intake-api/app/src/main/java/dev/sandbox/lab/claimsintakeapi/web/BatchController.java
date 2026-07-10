package dev.sandbox.lab.claimsintakeapi.web;

import dev.sandbox.lab.claimsintakeapi.domain.BatchMode;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.repository.BatchStore;
import dev.sandbox.lab.claimsintakeapi.service.BatchIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/claims-intake")
public class BatchController {
    private final BatchIngestService ingestService;
    private final BatchStore store;

    public BatchController(BatchIngestService ingestService, BatchStore store) {
        this.ingestService = ingestService;
        this.store = store;
    }

    // 200 for a STREAMED batch (already COMPLETE by the time this returns), 202 for a QUEUED one
    // (accepted, still being written out by KinesisBatchConsumer) - same status-code convention
    // events-api uses for "accepted but not yet processed."
    @PostMapping
    public ResponseEntity<BatchSummary> upload(@RequestParam("file") MultipartFile file) throws IOException {
        BatchSummary summary = ingestService.ingest(file);
        HttpStatus status = summary.mode() == BatchMode.STREAMED ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(summary);
    }

    @GetMapping("/{batchId}")
    public BatchSummary get(@PathVariable String batchId) {
        return store.findById(batchId);
    }

    @GetMapping
    public List<BatchSummary> list() {
        return store.findAll();
    }
}
