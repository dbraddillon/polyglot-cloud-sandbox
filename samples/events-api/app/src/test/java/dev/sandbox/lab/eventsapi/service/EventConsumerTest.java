package dev.sandbox.lab.eventsapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.eventsapi.domain.EventMessage;
import dev.sandbox.lab.eventsapi.domain.ProcessedEvent;
import dev.sandbox.lab.eventsapi.repository.ProcessedEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// MockitoExtension is Mockito's JUnit 5 integration - the direct equivalent of wiring up
// Mock<T> instances in a C# test project via Moq's MockRepository, just declared through a
// class-level annotation instead of constructed manually. ProcessedEventStore is a concrete
// class, not an interface, but Mockito mocks both the same way @Mock does here.
@ExtendWith(MockitoExtension.class)
class EventConsumerTest {
    @Mock
    private SqsClient sqs;
    @Mock
    private ProcessedEventStore store;

    private final ObjectMapper mapper = new ObjectMapper();
    private EventConsumer consumer;

    // Built here, not as a field initializer: field initializers run during instance
    // construction, which happens before MockitoExtension injects the @Mock fields - capturing
    // sqs/store at that point would capture null. @BeforeEach runs after injection.
    @BeforeEach
    void setUp() {
        consumer = new EventConsumer(sqs, mapper, store, "http://queue-url");
    }

    @Test
    void successfullyProcessedMessageIsDeleted() throws Exception {
        Message message = snsWrappedMessage("msg-1", "claim.approved", "{\"claimId\":\"abc\"}");
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        consumer.poll();

        verify(store).save(any(ProcessedEvent.class));
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    // Regression test for the original bug: EventConsumer.poll() used to call deleteMessage()
    // unconditionally after the try/catch, so a message that failed to parse was deleted anyway
    // instead of becoming visible again for SQS to redeliver. This test fails against that old
    // shape (deleteMessage() would have been called here) and passes against the fixed one.
    @Test
    void malformedMessageIsNotDeletedSoItCanBeRetried() {
        Message message = Message.builder()
                .messageId("msg-2")
                .receiptHandle("receipt-2")
                .body("not valid json")
                .build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());

        consumer.poll();

        verify(store, never()).save(any());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void storeFailureAlsoLeavesTheMessageUndeleted() throws Exception {
        Message message = snsWrappedMessage("msg-3", "claim.approved", "{\"claimId\":\"xyz\"}");
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        doThrow(new RuntimeException("store unavailable")).when(store).save(any());

        consumer.poll();

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    // Builds an SQS message body shaped like a real SNS-to-SQS delivery: the outer envelope has
    // a "Message" field whose value is itself the JSON-encoded EventMessage as a string, not a
    // nested object. This is what EventConsumer's private SnsEnvelope record expects to unwrap.
    private Message snsWrappedMessage(String messageId, String type, String payload) throws Exception {
        String inner = mapper.writeValueAsString(new EventMessage(messageId, type, payload));
        String envelope = "{\"Type\":\"Notification\",\"Message\":" + mapper.writeValueAsString(inner) + "}";
        return Message.builder()
                .messageId(messageId)
                .receiptHandle("receipt-" + messageId)
                .body(envelope)
                .build();
    }
}
