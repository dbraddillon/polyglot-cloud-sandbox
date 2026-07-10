package dev.sandbox.lab.claimsintakeapi.service;

import dev.sandbox.lab.claimsintakeapi.domain.BatchMode;
import dev.sandbox.lab.claimsintakeapi.domain.BatchStatus;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.repository.BatchStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The decision tree itself, plus the ordering fix from commit cf07b52 - both collaborators
// (StreamingBatchProcessor, KinesisBatchProducer) and the store are mocked, same pattern as
// PlanServiceTest, since this class's only job is choosing between them and sequencing calls
// correctly.
@ExtendWith(MockitoExtension.class)
class BatchIngestServiceTest {
    private static final long THRESHOLD_BYTES = 100;

    @Mock
    private StreamingBatchProcessor streamingProcessor;
    @Mock
    private KinesisBatchProducer kinesisProducer;
    @Mock
    private BatchStore store;

    private BatchIngestService service;

    @BeforeEach
    void setUp() {
        service = new BatchIngestService(streamingProcessor, kinesisProducer, store, THRESHOLD_BYTES);
    }

    @Test
    void emptyFileThrowsBeforeEitherProcessorRuns() {
        MultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> service.ingest(file)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(streamingProcessor, kinesisProducer, store);
    }

    @Test
    void fileAtExactlyTheThresholdIsStreamedNotQueued() throws IOException {
        MultipartFile file = new MockMultipartFile("file", new byte[(int) THRESHOLD_BYTES]);
        BatchSummary streamed = BatchSummary.streamed("batch-1", 1, 1, 0, BigDecimal.TEN, "out.jsonl");
        when(streamingProcessor.process(anyString(), any())).thenReturn(streamed);

        BatchSummary result = service.ingest(file);

        assertThat(result).isEqualTo(streamed);
        verify(streamingProcessor).process(anyString(), any());
        verify(store).save(streamed);
        verifyNoInteractions(kinesisProducer);
    }

    @Test
    void fileOneByteOverTheThresholdIsQueuedNotStreamed() throws IOException {
        MultipartFile file = new MockMultipartFile("file", new byte[(int) THRESHOLD_BYTES + 1]);
        when(kinesisProducer.publish(anyString(), any()))
                .thenAnswer(invocation -> BatchSummary.queuedAccepted(invocation.getArgument(0), 5, 4, 1, BigDecimal.TEN));

        BatchSummary result = service.ingest(file);

        assertThat(result.mode()).isEqualTo(BatchMode.QUEUED);
        verify(kinesisProducer).publish(anyString(), any());
        verifyNoInteractions(streamingProcessor);
    }

    // Regression test for the ordering-race fix in commit cf07b52: the batch used to be
    // registered in BatchStore only *after* kinesisProducer.publish() returned, racing
    // KinesisBatchConsumer's independent @Scheduled poller, which could reach the end-of-batch
    // marker (and therefore call store.findById()) before the batch existed at all. This locks
    // in both the call order and the placeholder's shape, and confirms every step uses the same
    // batchId - the stub echoes back whatever id it was actually called with, rather than a
    // fixed one, so this would catch a bug that generated a second, different id partway through.
    @Test
    void queuedBatchIsRegisteredBeforePublishingASingleRecord() throws IOException {
        MultipartFile file = new MockMultipartFile("file", new byte[(int) THRESHOLD_BYTES + 1]);
        when(kinesisProducer.publish(anyString(), any()))
                .thenAnswer(invocation -> BatchSummary.queuedAccepted(invocation.getArgument(0), 5, 4, 1, BigDecimal.TEN));

        ArgumentCaptor<BatchSummary> saved = ArgumentCaptor.forClass(BatchSummary.class);
        ArgumentCaptor<String> publishedBatchId = ArgumentCaptor.forClass(String.class);

        service.ingest(file);

        InOrder inOrder = inOrder(store, kinesisProducer);
        inOrder.verify(store).save(saved.capture());
        inOrder.verify(kinesisProducer).publish(publishedBatchId.capture(), any());
        inOrder.verify(store).save(saved.capture());

        List<BatchSummary> savedSummaries = saved.getAllValues();
        BatchSummary placeholder = savedSummaries.get(0);
        BatchSummary finalSummary = savedSummaries.get(1);

        assertThat(placeholder.status()).isEqualTo(BatchStatus.PROCESSING);
        assertThat(placeholder.mode()).isEqualTo(BatchMode.QUEUED);
        assertThat(placeholder.rowsRead()).isZero();
        assertThat(placeholder.outputPath()).isNull();

        assertThat(placeholder.batchId()).isEqualTo(publishedBatchId.getValue());
        assertThat(finalSummary.batchId()).isEqualTo(publishedBatchId.getValue());
        assertThat(finalSummary.validRows()).isEqualTo(4);
    }
}
