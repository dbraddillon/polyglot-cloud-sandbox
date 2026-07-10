package dev.sandbox.lab.eventsapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.eventsapi.domain.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {
    @Mock
    private SnsClient sns;

    private final ObjectMapper mapper = new ObjectMapper();
    private EventPublisher publisher;

    // Built here rather than as a field initializer - see EventConsumerTest for why (Mockito
    // injects @Mock fields after the instance is constructed, so a field initializer would
    // capture a still-null sns).
    @BeforeEach
    void setUp() {
        publisher = new EventPublisher(sns, mapper, "arn:aws:sns:us-east-1:000000000000:events-topic");
    }

    @Test
    void publishSendsTheSerializedMessageToTheConfiguredTopic() throws Exception {
        EventMessage published = publisher.publish("claim.approved", "{\"claimId\":\"abc\"}");

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(captor.capture());

        PublishRequest request = captor.getValue();
        assertThat(request.topicArn()).isEqualTo("arn:aws:sns:us-east-1:000000000000:events-topic");

        EventMessage roundTripped = mapper.readValue(request.message(), EventMessage.class);
        assertThat(roundTripped).isEqualTo(published);
        assertThat(roundTripped.type()).isEqualTo("claim.approved");
        assertThat(roundTripped.payload()).isEqualTo("{\"claimId\":\"abc\"}");
    }
}
