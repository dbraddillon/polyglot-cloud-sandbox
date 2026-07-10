package dev.sandbox.lab.claimsintakeapi.repository;

import dev.sandbox.lab.claimsintakeapi.domain.BatchNotFoundException;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// In-memory only, same as events-api's ProcessedEventStore - a sandbox stand-in for wherever a
// real system would persist batch status (a table, DynamoDB, whatever). ConcurrentHashMap
// because both the request thread (writing the initial QUEUED/PROCESSING summary) and the
// @Scheduled consumer thread (overwriting it with the COMPLETE summary) touch this concurrently.
@Repository
public class BatchStore {
    private final Map<String, BatchSummary> batches = new ConcurrentHashMap<>();

    public void save(BatchSummary summary) {
        batches.put(summary.batchId(), summary);
    }

    public BatchSummary findById(String batchId) {
        BatchSummary summary = batches.get(batchId);
        if (summary == null) {
            throw new BatchNotFoundException(batchId);
        }
        return summary;
    }

    public List<BatchSummary> findAll() {
        return List.copyOf(batches.values());
    }
}
