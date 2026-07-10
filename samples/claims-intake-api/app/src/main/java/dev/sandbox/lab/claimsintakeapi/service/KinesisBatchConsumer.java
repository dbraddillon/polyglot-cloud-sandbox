package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.repository.BatchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// The other half of the queue-based branch: drains the Kinesis stream at its own pace and does
// the part deferred by KinesisBatchProducer - writing each row out and, once a batch's end
// marker shows up, flipping its BatchSummary to COMPLETE. @Scheduled with a single-thread
// default executor (same as EventConsumer in events-api) means poll() never runs concurrently
// with itself, so the plain (non-concurrent) HashMap of in-flight writers below is safe without
// extra locking.
//
// Single-shard only, on purpose: Kinesis has no SQS-style "receive" call - you get a shard
// iterator once, then keep exchanging it for the next one via GetRecords. A multi-shard stream
// needs one iterator per shard plus a checkpoint store (this is exactly what the Kinesis Client
// Library / DynamoDB checkpointing exists to solve) so consumers can resume correctly after a
// restart - both skipped here since a demo CSV firehose has no real need for more than one
// shard's worth of throughput. This consumer also always starts from TRIM_HORIZON (the oldest
// untrimmed record) on startup rather than checkpointing its position, so a restart mid-batch
// reprocesses from the beginning of the stream's retention window - fine for a sandbox, not
// how you'd want a production consumer to behave.
@Component
public class KinesisBatchConsumer {
    private static final Logger log = LoggerFactory.getLogger(KinesisBatchConsumer.class);

    private final KinesisClient kinesis;
    private final ObjectMapper mapper;
    private final BatchStore store;
    private final String streamName;
    private final Path outputDir;

    private final Map<String, ClaimOutputWriter> openWriters = new HashMap<>();
    private String shardIterator;

    public KinesisBatchConsumer(KinesisClient kinesis, ObjectMapper mapper, BatchStore store,
                                 @Value("${claims-intake.stream-name}") String streamName,
                                 @Value("${claims-intake.output-dir}") String outputDir) {
        this.kinesis = kinesis;
        this.mapper = mapper;
        this.store = store;
        this.streamName = streamName;
        this.outputDir = Path.of(outputDir);
    }

    @Scheduled(fixedDelay = 200)
    public void poll() {
        ensureShardIterator();
        if (shardIterator == null) {
            return;
        }

        // limit(1000) is still a bounded pull, not "drain everything at once" - it just sets
        // the bound high enough that a several-thousand-row demo batch clears in a handful of
        // polls instead of a couple of minutes. Found the hard way running deploy.sh: the
        // original limit(25)/fixedDelay(1000) pairing (modeled after events-api's SQS poller,
        // which only ever has a handful of test messages) took 6000 rows / 25 per poll = ~240
        // polls to drain - correct, just needlessly slow for what this consumer is meant to
        // demonstrate.
        GetRecordsResponse response = kinesis.getRecords(GetRecordsRequest.builder()
                .shardIterator(shardIterator)
                .limit(1000)
                .build());
        shardIterator = response.nextShardIterator();

        for (Record record : response.records()) {
            try {
                handle(record);
            } catch (Exception e) {
                log.warn("Failed to process Kinesis record", e);
            }
        }
    }

    private void ensureShardIterator() {
        if (shardIterator != null) {
            return;
        }
        var shards = kinesis.describeStream(DescribeStreamRequest.builder()
                        .streamName(streamName)
                        .build())
                .streamDescription()
                .shards();
        if (shards.isEmpty()) {
            return;
        }
        shardIterator = kinesis.getShardIterator(GetShardIteratorRequest.builder()
                        .streamName(streamName)
                        .shardId(shards.get(0).shardId())
                        .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
                        .build())
                .shardIterator();
    }

    private void handle(Record record) throws Exception {
        ClaimStreamMessage message = mapper.readValue(record.data().asUtf8String(), ClaimStreamMessage.class);

        if (ClaimStreamMessage.TYPE_ROW.equals(message.type())) {
            ClaimOutputWriter writer = openWriters.computeIfAbsent(message.batchId(), this::openWriter);
            writer.writeRow(message.row());
            return;
        }

        if (ClaimStreamMessage.TYPE_END.equals(message.type())) {
            ClaimOutputWriter writer = openWriters.remove(message.batchId());
            if (writer == null) {
                log.warn("Got end-of-batch marker for {} with no rows seen first", message.batchId());
                return;
            }
            writer.close();
            if (writer.rowsWritten() != message.expectedValidRows()) {
                log.warn("Batch {}: wrote {} rows but producer reported {}",
                        message.batchId(), writer.rowsWritten(), message.expectedValidRows());
            }
            BatchSummary current = store.findById(message.batchId());
            store.save(current.asComplete(outputDir.resolve(message.batchId() + ".jsonl").toString()));
        }
    }

    private ClaimOutputWriter openWriter(String batchId) {
        try {
            return new ClaimOutputWriter(mapper, outputDir.resolve(batchId + ".jsonl"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open output file for batch " + batchId, e);
        }
    }
}
