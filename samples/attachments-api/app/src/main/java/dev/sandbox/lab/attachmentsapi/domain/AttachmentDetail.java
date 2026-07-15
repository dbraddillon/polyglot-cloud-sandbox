package dev.sandbox.lab.attachmentsapi.domain;

// The richer shape returned by an upload and by a single-item fetch, once we're already paying
// for the object's own metadata (either because we just uploaded it, or because a GetObject
// call returns it alongside the bytes anyway - the enrichment AttachmentSummary's list endpoint
// deliberately skips).
public record AttachmentDetail(String key, String filename, String contentType, long size) {
}
