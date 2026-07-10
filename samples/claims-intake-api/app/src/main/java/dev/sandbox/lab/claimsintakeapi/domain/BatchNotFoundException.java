package dev.sandbox.lab.claimsintakeapi.domain;

public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(String batchId) {
        super("Batch not found: " + batchId);
    }
}
