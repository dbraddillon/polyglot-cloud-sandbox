package dev.sandbox.lab.claimsintakeapi.domain;

import java.math.BigDecimal;

// outputPath is null until a batch reaches COMPLETE - for QUEUED batches that's a real eventual-
// consistency window (same idea as events-api's ProcessedEvent 404-until-polled gap), not a
// sandbox artifact. Two named static factories instead of a public canonical constructor read
// clearer at call sites than `new BatchSummary(id, mode, status, ..., null, null)` - closest
// .NET parallel is a couple of static factory methods on a record, since C# doesn't have Java's
// convention of hiding a record's canonical constructor in favor of named creators.
public record BatchSummary(
        String batchId,
        BatchMode mode,
        BatchStatus status,
        long rowsRead,
        long validRows,
        long invalidRows,
        BigDecimal totalBilledAmount,
        String outputPath) {

    public static BatchSummary streamed(String batchId, long rowsRead, long validRows, long invalidRows,
                                         BigDecimal totalBilledAmount, String outputPath) {
        return new BatchSummary(batchId, BatchMode.STREAMED, BatchStatus.COMPLETE,
                rowsRead, validRows, invalidRows, totalBilledAmount, outputPath);
    }

    public static BatchSummary queuedAccepted(String batchId, long rowsRead, long validRows, long invalidRows,
                                               BigDecimal totalBilledAmount) {
        return new BatchSummary(batchId, BatchMode.QUEUED, BatchStatus.PROCESSING,
                rowsRead, validRows, invalidRows, totalBilledAmount, null);
    }

    public BatchSummary asComplete(String outputPath) {
        return new BatchSummary(batchId, mode, BatchStatus.COMPLETE,
                rowsRead, validRows, invalidRows, totalBilledAmount, outputPath);
    }
}
