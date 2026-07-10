package dev.sandbox.lab.claimsintakeapi.service;

import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.repository.BatchStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

// The decision tree itself, such as it is: one `if`, based on upload size. Deliberately keyed
// off `MultipartFile.getSize()` rather than a row count - the whole point is picking a strategy
// *before* paying the cost of reading the file, and byte size is the one thing already known
// for free from the multipart upload, whereas a row count would mean scanning the file first
// just to decide how to scan the file. A production version of this would likely tune the
// threshold against real numbers (available heap, acceptable p99 request latency) rather than
// a flat config value - left as a single @Value here to keep the demo legible.
@Service
public class BatchIngestService {
    private final StreamingBatchProcessor streamingProcessor;
    private final KinesisBatchProducer kinesisProducer;
    private final BatchStore store;
    private final long streamingThresholdBytes;

    public BatchIngestService(StreamingBatchProcessor streamingProcessor,
                               KinesisBatchProducer kinesisProducer,
                               BatchStore store,
                               @Value("${claims-intake.streaming-threshold-bytes}") long streamingThresholdBytes) {
        this.streamingProcessor = streamingProcessor;
        this.kinesisProducer = kinesisProducer;
        this.store = store;
        this.streamingThresholdBytes = streamingThresholdBytes;
    }

    public BatchSummary ingest(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String batchId = UUID.randomUUID().toString();
        if (file.getSize() <= streamingThresholdBytes) {
            BatchSummary summary = streamingProcessor.process(batchId, file.getInputStream());
            store.save(summary);
            return summary;
        }

        // Register the batch before publishing a single Kinesis record. KinesisBatchConsumer
        // runs on its own independent @Scheduled cadence and could otherwise reach this batch's
        // end-of-batch marker before it exists in the store at all - store.findById() would
        // throw BatchNotFoundException, silently caught and logged by the consumer's per-record
        // catch block with no retry, since the shard iterator has already moved past that
        // record by the time anyone notices.
        store.save(BatchSummary.queuedPending(batchId));
        BatchSummary summary = kinesisProducer.publish(batchId, file.getInputStream());
        store.save(summary);
        return summary;
    }
}
