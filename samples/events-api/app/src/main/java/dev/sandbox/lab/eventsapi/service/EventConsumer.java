package dev.sandbox.lab.eventsapi.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.eventsapi.domain.EventMessage;
import dev.sandbox.lab.eventsapi.domain.ProcessedEvent;
import dev.sandbox.lab.eventsapi.repository.ProcessedEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

// @Scheduled is Spring's equivalent of a hosted IHostedService/BackgroundService running on a
// timer in ASP.NET Core - a plain method annotation here instead of a class implementing an
// interface and overriding ExecuteAsync. Needs @EnableScheduling on the application class to
// actually fire (see EventsApiApplication).
@Component
public class EventConsumer {
    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final SqsClient sqs;
    private final ObjectMapper mapper;
    private final ProcessedEventStore store;
    private final String queueUrl;

    public EventConsumer(SqsClient sqs, ObjectMapper mapper, ProcessedEventStore store,
                          @Value("${events.queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.mapper = mapper;
        this.store = store;
        this.queueUrl = queueUrl;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(2)
                        .build())
                .messages();

        for (Message sqsMessage : messages) {
            try {
                // A message that arrived via an SNS subscription isn't your payload directly -
                // SNS wraps it in its own JSON envelope ({"Type":"Notification","Message":"...",
                // ...}), and the body you actually published is a string *inside* that
                // envelope's "Message" field, not the SQS message body itself. A plain
                // SQS-only producer (no SNS in front of it) wouldn't need this unwrap step.
                SnsEnvelope envelope = mapper.readValue(sqsMessage.body(), SnsEnvelope.class);
                EventMessage event = mapper.readValue(envelope.message(), EventMessage.class);
                store.save(ProcessedEvent.from(event));

                // Delete only happens on the success path. SQS is at-least-once delivery: if we
                // deleted unconditionally (including from the catch block below, or after it),
                // a message that fails processing would be gone for good instead of becoming
                // visible again for a retry after the visibility timeout. Leaving it un-deleted
                // on failure is what makes the queue's own redrive policy (maxReceiveCount, see
                // the Pulumi infra) actually able to retry it and eventually route it to the DLQ.
                sqs.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(sqsMessage.receiptHandle())
                        .build());
            } catch (Exception e) {
                log.warn("Failed to process message {}", sqsMessage.messageId(), e);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SnsEnvelope(@JsonProperty("Message") String message) {
    }
}
