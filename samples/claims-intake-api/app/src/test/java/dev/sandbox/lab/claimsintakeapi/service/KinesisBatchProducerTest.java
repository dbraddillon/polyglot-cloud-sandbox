package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KinesisBatchProducerTest {
    private static final String STREAM_NAME = "claims-intake-stream";

    @Mock
    private KinesisClient kinesis;

    // Same reasoning as ClaimStreamMessageTest: matches what Spring Boot's auto-configured
    // ObjectMapper bean actually provides in production (JavaTimeModule registered), built by
    // hand rather than pulling in a Spring context.
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private KinesisBatchProducer producer;

    @BeforeEach
    void setUp() {
        producer = new KinesisBatchProducer(kinesis, mapper, STREAM_NAME);
        // lenient: partialKinesisFailuresAreLoggedNotThrown overrides this with its own stub,
        // which would otherwise make this one an "unnecessary stubbing" for that one test.
        lenient().when(kinesis.putRecords(any(PutRecordsRequest.class)))
                .thenReturn(PutRecordsResponse.builder().failedRecordCount(0).build());
    }

    @Test
    void smallBatchPublishesOneCallWithAllRowsPlusTheEndMarker() throws Exception {
        BatchSummary summary = producer.publish("batch-1", csvWithValidRows(3));

        assertThat(summary.validRows()).isEqualTo(3);
        assertThat(summary.invalidRows()).isZero();
        assertThat(summary.totalBilledAmount()).isEqualByComparingTo("150.00");

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(kinesis, times(1)).putRecords(captor.capture());

        assertThat(captor.getValue().streamName()).isEqualTo(STREAM_NAME);
        List<PutRecordsRequestEntry> entries = captor.getValue().records();
        assertThat(entries).hasSize(4); // 3 rows + 1 end marker, all in the same call

        ClaimStreamMessage last = decode(entries.get(entries.size() - 1));
        assertThat(last.type()).isEqualTo(ClaimStreamMessage.TYPE_END);
        assertThat(last.batchId()).isEqualTo("batch-1");
        assertThat(last.expectedValidRows()).isEqualTo(3L);
    }

    // The batching boundary this class exists to get right: PutRecords is capped at 500 entries
    // per call (see MAX_RECORDS_PER_PUT). 501 valid rows should flush exactly once mid-loop at
    // the 500th row (500 entries - there's still file left to read, so no end marker yet), then
    // a second, final call carrying just the 501st row's entry plus the end marker. This is what
    // replaced the naive one-PutRecord-per-row producer this class's own comment describes.
    @Test
    void flushesAtExactlyFiveHundredRecordsNotBeforeOrAfter() throws Exception {
        BatchSummary summary = producer.publish("batch-2", csvWithValidRows(501));

        assertThat(summary.validRows()).isEqualTo(501);

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(kinesis, times(2)).putRecords(captor.capture());

        List<PutRecordsRequest> calls = captor.getAllValues();
        assertThat(calls.get(0).records()).hasSize(500);
        assertThat(calls.get(1).records()).hasSize(2); // the 501st row + the end marker

        ClaimStreamMessage last = decode(calls.get(1).records().get(1));
        assertThat(last.type()).isEqualTo(ClaimStreamMessage.TYPE_END);
        assertThat(last.expectedValidRows()).isEqualTo(501L);
    }

    @Test
    void invalidRowsAreCountedButNeverPublishedToKinesis() throws Exception {
        String csv = """
                claimId,memberId,serviceDate,serviceDescription,billedAmount
                CLM-1,MBR-1,2024-01-01,Checkup,50.00
                CLM-2,,2024-01-01,Missing member id,50.00
                CLM-3,MBR-3,2024-01-01,Checkup,75.00
                """;

        BatchSummary summary = producer.publish("batch-3",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(summary.rowsRead()).isEqualTo(3);
        assertThat(summary.validRows()).isEqualTo(2);
        assertThat(summary.invalidRows()).isEqualTo(1);
        assertThat(summary.totalBilledAmount()).isEqualByComparingTo("125.00");

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(kinesis, times(1)).putRecords(captor.capture());
        assertThat(captor.getValue().records()).hasSize(3); // 2 valid rows + end marker, not 3 + end
    }

    @Test
    void partialKinesisFailuresAreLoggedNotThrown() throws Exception {
        when(kinesis.putRecords(any(PutRecordsRequest.class)))
                .thenReturn(PutRecordsResponse.builder().failedRecordCount(1).build());

        BatchSummary summary = producer.publish("batch-4", csvWithValidRows(2));

        assertThat(summary.validRows()).isEqualTo(2);
    }

    private InputStream csvWithValidRows(int count) {
        StringBuilder sb = new StringBuilder("claimId,memberId,serviceDate,serviceDescription,billedAmount\n");
        for (int i = 0; i < count; i++) {
            sb.append("CLM-").append(i).append(",MBR-1,2024-01-01,Checkup,50.00\n");
        }
        return new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ClaimStreamMessage decode(PutRecordsRequestEntry entry) throws Exception {
        return mapper.readValue(entry.data().asUtf8String(), ClaimStreamMessage.class);
    }
}
