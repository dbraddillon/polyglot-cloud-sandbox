package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ClaimStreamMessage is package-private (a deliberate choice - see its own comment on why it's a
// flat tagged record rather than a sealed interface, to stay Jackson-friendly), so this test
// lives in the same package to reach it, same as the class it's testing would from elsewhere in
// this package.
class ClaimStreamMessageTest {
    private static final ClaimRow SAMPLE_ROW = new ClaimRow(
            "CLM-1", "MBR-1", LocalDate.of(2024, 1, 15), "Checkup", new BigDecimal("50.00"));

    // Spring Boot's auto-configured ObjectMapper bean - the one actually injected into
    // KinesisBatchProducer/KinesisBatchConsumer in production - registers JavaTimeModule
    // automatically because jackson-datatype-jsr310 is on the classpath. Built by hand here to
    // match that, rather than spinning up a Spring context just for a unit test.
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void rowFactoryProducesATaggedRowMessage() {
        ClaimStreamMessage message = ClaimStreamMessage.row("batch-1", SAMPLE_ROW);

        assertThat(message.batchId()).isEqualTo("batch-1");
        assertThat(message.type()).isEqualTo(ClaimStreamMessage.TYPE_ROW);
        assertThat(message.row()).isEqualTo(SAMPLE_ROW);
        assertThat(message.expectedValidRows()).isNull();
    }

    @Test
    void endFactoryProducesATaggedEndMessage() {
        ClaimStreamMessage message = ClaimStreamMessage.end("batch-1", 42);

        assertThat(message.batchId()).isEqualTo("batch-1");
        assertThat(message.type()).isEqualTo(ClaimStreamMessage.TYPE_END);
        assertThat(message.row()).isNull();
        assertThat(message.expectedValidRows()).isEqualTo(42L);
    }

    @Test
    void rowMessageRoundTripsThroughJson() throws Exception {
        ClaimStreamMessage original = ClaimStreamMessage.row("batch-1", SAMPLE_ROW);

        String json = mapper.writeValueAsString(original);
        ClaimStreamMessage rehydrated = mapper.readValue(json, ClaimStreamMessage.class);

        assertThat(rehydrated).isEqualTo(original);
    }

    @Test
    void endMessageRoundTripsThroughJson() throws Exception {
        ClaimStreamMessage original = ClaimStreamMessage.end("batch-1", 42);

        String json = mapper.writeValueAsString(original);
        ClaimStreamMessage rehydrated = mapper.readValue(json, ClaimStreamMessage.class);

        assertThat(rehydrated).isEqualTo(original);
    }

    // The trap this test class exists partly to document: ClaimRow carries a LocalDate, and
    // Jackson has no built-in support for java.time types - without JavaTimeModule registered,
    // it doesn't silently degrade, it throws. A C# dev coming from System.Text.Json (which
    // handles DateOnly/DateTime out of the box, no extra module needed) wouldn't expect this.
    // events-api's EventMessage never hit this because its fields are string-only - ClaimRow is
    // the first domain type in this repo that actually needs the module registered.
    @Test
    void serializingALocalDateWithoutJavaTimeModuleThrows() {
        ObjectMapper mapperWithoutTimeModule = new ObjectMapper();
        ClaimStreamMessage message = ClaimStreamMessage.row("batch-1", SAMPLE_ROW);

        assertThatThrownBy(() -> mapperWithoutTimeModule.writeValueAsString(message))
                .isInstanceOf(JsonProcessingException.class);
    }
}
