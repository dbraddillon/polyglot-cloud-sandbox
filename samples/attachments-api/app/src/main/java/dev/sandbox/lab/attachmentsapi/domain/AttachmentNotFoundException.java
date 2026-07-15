package dev.sandbox.lab.attachmentsapi.domain;

public class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException(String key) {
        super("Attachment not found: " + key);
    }
}
