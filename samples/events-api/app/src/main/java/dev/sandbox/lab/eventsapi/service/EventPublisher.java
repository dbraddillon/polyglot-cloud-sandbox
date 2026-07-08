package dev.sandbox.lab.eventsapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.eventsapi.domain.EventMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.UUID;

@Service
public class EventPublisher {
    private final SnsClient sns;
    private final ObjectMapper mapper;
    private final String topicArn;

    public EventPublisher(SnsClient sns, ObjectMapper mapper, @Value("${events.topic-arn}") String topicArn) {
        this.sns = sns;
        this.mapper = mapper;
        this.topicArn = topicArn;
    }

    public EventMessage publish(String type, String payload) {
        var message = new EventMessage(UUID.randomUUID().toString(), type, payload);
        String body;
        try {
            body = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // JsonProcessingException is checked - the compiler forces this try/catch even
            // though serializing a record of plain Strings realistically never fails. This is
            // exactly the kind of thing checked exceptions get a bad reputation for: a
            // compile-time obligation for a runtime scenario that isn't actually going to
            // happen here. C# has no equivalent - JSON serialization there never forces a catch.
            throw new IllegalStateException("Failed to serialize event", e);
        }
        sns.publish(PublishRequest.builder().topicArn(topicArn).message(body).build());
        return message;
    }
}
