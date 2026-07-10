package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;
import dev.sandbox.lab.claimsintakeapi.repository.BatchStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.StreamDescription;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// KinesisClient and BatchStore are mocked, same as KinesisBatchProducerTest - but
// ClaimOutputWriter (package-private, used internally by this class) does real file I/O, so a
// real @TempDir stands in for the configured output directory rather than mocking the
// filesystem too.
@ExtendWith(MockitoExtension.class)
class KinesisBatchConsumerTest {
    private static final String STREAM_NAME = "claims-intake-stream";
    private static final ClaimRow SAMPLE_ROW = new ClaimRow(
            "CLM-1", "MBR-1", LocalDate.of(2024, 1, 15), "Checkup", new BigDecimal("50.00"));

    @Mock
    private KinesisClient kinesis;
    @Mock
    private BatchStore store;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private Path outputDir;
    private KinesisBatchConsumer consumer;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        outputDir = tempDir;
        consumer = new KinesisBatchConsumer(kinesis, mapper, store, STREAM_NAME, tempDir.toString());

        when(kinesis.describeStream(any(DescribeStreamRequest.class))).thenReturn(
                DescribeStreamResponse.builder()
                        .streamDescription(StreamDescription.builder()
                                .shards(Shard.builder().shardId("shard-1").build())
                                .build())
                        .build());
        when(kinesis.getShardIterator(any(GetShardIteratorRequest.class))).thenReturn(
                GetShardIteratorResponse.builder().shardIterator("iterator-1").build());
    }

    @Test
    void rowsAreWrittenAndTheBatchCompletesOnTheEndMarker() throws Exception {
        when(store.findById("batch-1")).thenReturn(BatchSummary.queuedPending("batch-1"));
        stubRecords(
                rowRecord("batch-1", SAMPLE_ROW),
                rowRecord("batch-1", SAMPLE_ROW),
                endRecord("batch-1", 2));

        consumer.poll();

        List<String> lines = Files.readAllLines(outputDir.resolve("batch-1.jsonl"));
        assertThat(lines).hasSize(2);
        assertThat(mapper.readValue(lines.get(0), ClaimRow.class)).isEqualTo(SAMPLE_ROW);

        verify(store).save(argThatCompletesWith("batch-1.jsonl"));
    }

    // Regression test for the zero-valid-rows fix in commit cf07b52: a batch where every CSV
    // row failed validation never gets a ROW message, so there's no writer to find when the end
    // marker arrives. That must still be treated as a legitimate (empty) completion, not the
    // "genuine anomaly" branch below.
    @Test
    void endMarkerWithZeroExpectedRowsCompletesWithAnEmptyOutputFile() throws Exception {
        when(store.findById("batch-2")).thenReturn(BatchSummary.queuedPending("batch-2"));
        stubRecords(endRecord("batch-2", 0));

        consumer.poll();

        List<String> lines = Files.readAllLines(outputDir.resolve("batch-2.jsonl"));
        assertThat(lines).isEmpty();
        verify(store).save(argThatCompletesWith("batch-2.jsonl"));
    }

    // The other branch of that same `if`: an end marker with no prior ROW *and* a nonzero
    // expected count is a genuine anomaly (e.g. records lost/reordered), not a legitimate empty
    // batch. It should log and back off - no output file, no store update - rather than being
    // swept into the zero-row completion path.
    @Test
    void endMarkerWithNoRowsSeenAndNonZeroExpectedIsNotCompleted() throws Exception {
        stubRecords(endRecord("batch-3", 5));

        consumer.poll();

        assertThat(Files.exists(outputDir.resolve("batch-3.jsonl"))).isFalse();
        verify(store, never()).findById(any());
        verify(store, never()).save(any());
    }

    @Test
    void rowCountMismatchIsLoggedButTheBatchStillCompletes() throws Exception {
        when(store.findById("batch-4")).thenReturn(BatchSummary.queuedPending("batch-4"));
        // Producer claims 5 valid rows were sent; only 1 ROW record actually arrives here. Not
        // reproducible through the real producer/consumer pair (they always agree), but a real
        // possibility if records were ever lost in transit - the consumer should still finish
        // the batch rather than leave it stuck in PROCESSING forever over a count mismatch.
        stubRecords(rowRecord("batch-4", SAMPLE_ROW), endRecord("batch-4", 5));

        consumer.poll();

        List<String> lines = Files.readAllLines(outputDir.resolve("batch-4.jsonl"));
        assertThat(lines).hasSize(1);
        verify(store).save(argThatCompletesWith("batch-4.jsonl"));
    }

    // ensureShardIterator() should only resolve a shard iterator once - describeStream/
    // getShardIterator are one-time setup, not something every poll() tick repeats.
    @Test
    void shardIteratorIsOnlyResolvedOnce() {
        when(kinesis.getRecords(any(GetRecordsRequest.class))).thenReturn(
                GetRecordsResponse.builder().records(List.of()).nextShardIterator("iterator-2").build());

        consumer.poll();
        consumer.poll();

        verify(kinesis, times(1)).describeStream(any(DescribeStreamRequest.class));
        verify(kinesis, times(1)).getShardIterator(any(GetShardIteratorRequest.class));
        verify(kinesis, times(2)).getRecords(any(GetRecordsRequest.class));
    }

    private void stubRecords(Record... records) {
        when(kinesis.getRecords(any(GetRecordsRequest.class))).thenReturn(
                GetRecordsResponse.builder().records(records).nextShardIterator("iterator-2").build());
    }

    private Record rowRecord(String batchId, ClaimRow row) throws Exception {
        String json = mapper.writeValueAsString(ClaimStreamMessage.row(batchId, row));
        return Record.builder().partitionKey(row.claimId()).data(SdkBytes.fromUtf8String(json)).build();
    }

    private Record endRecord(String batchId, long expectedValidRows) throws Exception {
        String json = mapper.writeValueAsString(ClaimStreamMessage.end(batchId, expectedValidRows));
        return Record.builder().partitionKey(batchId).data(SdkBytes.fromUtf8String(json)).build();
    }

    private BatchSummary argThatCompletesWith(String outputFileName) {
        return org.mockito.ArgumentMatchers.argThat(summary ->
                summary != null
                        && summary.outputPath() != null
                        && summary.outputPath().endsWith(outputFileName));
    }
}
