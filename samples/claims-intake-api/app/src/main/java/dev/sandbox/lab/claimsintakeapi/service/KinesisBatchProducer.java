package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// The "large enough that we don't want the upload request itself hostage to writing the whole
// output file" branch. Still reads the CSV one line at a time (same bounded-memory story as
// StreamingBatchProcessor) and still runs on the request thread - but per-row work here is just
// "validate, serialize, batch onto Kinesis", not "validate, then perform the actual write-out".
// That's cheap and roughly constant-time per row regardless of what the eventual output format/
// destination is, which is what makes it reasonable to still do synchronously. The genuinely
// slow/variable part (writing the transformed output, KinesisBatchConsumer) is what actually
// gets deferred to a separate consumer running at its own pace.
@Service
public class KinesisBatchProducer {
    private static final Logger log = LoggerFactory.getLogger(KinesisBatchProducer.class);

    // Kinesis's own ceiling for a single PutRecords call - 500 records or 5MB, whichever comes
    // first (we're nowhere near the byte limit with rows this small).
    private static final int MAX_RECORDS_PER_PUT = 500;

    private final KinesisClient kinesis;
    private final ObjectMapper mapper;
    private final String streamName;

    public KinesisBatchProducer(KinesisClient kinesis, ObjectMapper mapper,
                                 @Value("${claims-intake.stream-name}") String streamName) {
        this.kinesis = kinesis;
        this.mapper = mapper;
        this.streamName = streamName;
    }

    public BatchSummary publish(String batchId, InputStream csv) throws IOException {
        long rowsRead = 0;
        long invalidRows = 0;
        BigDecimal totalBilledAmount = BigDecimal.ZERO;
        List<PutRecordsRequestEntry> pending = new ArrayList<>(MAX_RECORDS_PER_PUT);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            reader.readLine(); // header

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rowsRead++;
                Optional<ClaimRow> row = ClaimRowParser.parse(line);
                if (row.isEmpty()) {
                    invalidRows++;
                    continue;
                }
                pending.add(toEntry(row.get().claimId(), ClaimStreamMessage.row(batchId, row.get())));
                totalBilledAmount = totalBilledAmount.add(row.get().billedAmount());
                if (pending.size() == MAX_RECORDS_PER_PUT) {
                    putRecords(pending);
                    pending.clear();
                }
            }
        }
        long validRows = rowsRead - invalidRows;

        // The end marker's partition key doesn't matter for correctness with a single shard -
        // every record lands on the same (only) shard regardless of key. A multi-shard stream
        // would need the marker sent to every shard, plus a checkpoint store tracking which
        // shards have reported their own end, which is real added complexity intentionally
        // skipped here (see the README's note on why this sample sticks to one shard).
        pending.add(toEntry(batchId, ClaimStreamMessage.end(batchId, validRows)));
        putRecords(pending);

        return BatchSummary.queuedAccepted(batchId, rowsRead, validRows, invalidRows, totalBilledAmount);
    }

    private PutRecordsRequestEntry toEntry(String partitionKey, ClaimStreamMessage message) {
        String json;
        try {
            json = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // Checked exception forcing a catch for a serialization that realistically can't
            // fail here - same story as EventPublisher's identical try/catch in events-api.
            throw new IllegalStateException("Failed to serialize Kinesis record", e);
        }
        return PutRecordsRequestEntry.builder()
                .partitionKey(partitionKey)
                .data(SdkBytes.fromUtf8String(json))
                .build();
    }

    // Batches of up to 500 instead of one PutRecord call per row - found the hard way running
    // deploy.sh against a 6000-row file: one HTTP round trip per row (even to a local emulator)
    // added up to well over a minute for the whole upload. PutRecords trades that for ~12 round
    // trips. A partial failure (some entries rejected, e.g. for throttling) doesn't fail the
    // whole request - failedRecordCount reports how many, and this sandbox just logs it rather
    // than retrying, which a production producer would want to do for the failed entries only
    // (PutRecordsResponse.records() lines up positionally with the request, and a failed entry
    // has an errorCode set instead of a sequence number).
    private void putRecords(List<PutRecordsRequestEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        PutRecordsResponse response = kinesis.putRecords(PutRecordsRequest.builder()
                .streamName(streamName)
                .records(entries)
                .build());
        if (response.failedRecordCount() != null && response.failedRecordCount() > 0) {
            log.warn("{} of {} Kinesis records in this batch were rejected", response.failedRecordCount(), entries.size());
        }
    }
}
