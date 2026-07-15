package dev.sandbox.lab.attachmentsapi.repository;

import dev.sandbox.lab.attachmentsapi.domain.AttachmentContent;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentDetail;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentSummary;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository {
    List<AttachmentSummary> findAll();

    AttachmentDetail upload(String filename, String contentType, byte[] content);

    Optional<AttachmentContent> download(String key);

    void deleteByKey(String key);
}
