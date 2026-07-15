package dev.sandbox.lab.attachmentsapi.domain;

import java.time.Instant;

// What ListObjectsV2 gives you for free: key, size, last-modified. Deliberately not the same
// shape as AttachmentDetail below - getting the original filename/content-type back requires a
// second round trip per object (S3 doesn't return custom metadata from a list call), so the
// list endpoint stays cheap and honest about what it actually knows instead of silently doing
// N+1 HeadObject calls to enrich it.
public record AttachmentSummary(String key, long size, Instant uploadedAt) {
}
