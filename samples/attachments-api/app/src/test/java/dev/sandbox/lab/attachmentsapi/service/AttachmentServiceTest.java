package dev.sandbox.lab.attachmentsapi.service;

import dev.sandbox.lab.attachmentsapi.domain.AttachmentContent;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentDetail;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentNotFoundException;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentSummary;
import dev.sandbox.lab.attachmentsapi.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock
    private AttachmentRepository repository;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(repository);
    }

    @Test
    void listDelegatesToTheRepository() {
        when(repository.findAll())
                .thenReturn(List.of(new AttachmentSummary("key-1", 100, Instant.now())));

        List<AttachmentSummary> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("key-1");
    }

    @Test
    void uploadDelegatesToTheRepository() {
        byte[] content = "hello".getBytes();
        when(repository.upload("photo.png", "image/png", content))
                .thenReturn(new AttachmentDetail("generated-key", "photo.png", "image/png", content.length));

        AttachmentDetail result = service.upload("photo.png", "image/png", content);

        assertThat(result.key()).isEqualTo("generated-key");
        assertThat(result.filename()).isEqualTo("photo.png");
    }

    @Test
    void downloadReturnsContentWhenPresent() {
        AttachmentContent content = new AttachmentContent("photo.png", "image/png", "hello".getBytes());
        when(repository.download("key-1")).thenReturn(Optional.of(content));

        assertThat(service.download("key-1")).isEqualTo(content);
    }

    @Test
    void downloadThrowsWhenMissing() {
        when(repository.download("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download("missing"))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void deleteDelegatesToTheRepository() {
        service.delete("key-1");

        verify(repository).deleteByKey("key-1");
    }
}
