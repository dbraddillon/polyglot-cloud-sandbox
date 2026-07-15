package dev.sandbox.lab.attachmentsapi.domain;

// The full download payload. byte[] rather than a stream - fine for a sandbox where demo files
// are small; a real service handling large attachments would want StreamingResponseBody instead
// to avoid buffering the whole object in memory before the first byte reaches the client.
public record AttachmentContent(String filename, String contentType, byte[] content) {
}
