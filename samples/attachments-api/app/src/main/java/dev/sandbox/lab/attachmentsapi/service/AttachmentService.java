package dev.sandbox.lab.attachmentsapi.service;

import dev.sandbox.lab.attachmentsapi.domain.AttachmentContent;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentDetail;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentNotFoundException;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentSummary;
import dev.sandbox.lab.attachmentsapi.repository.AttachmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AttachmentService {
    private final AttachmentRepository repository;

    public AttachmentService(AttachmentRepository repository) {
        this.repository = repository;
    }

    public List<AttachmentSummary> list() {
        return repository.findAll();
    }

    public AttachmentDetail upload(String filename, String contentType, byte[] content) {
        return repository.upload(filename, contentType, content);
    }

    public AttachmentContent download(String key) {
        return repository.download(key).orElseThrow(() -> new AttachmentNotFoundException(key));
    }

    public void delete(String key) {
        repository.deleteByKey(key);
    }
}
