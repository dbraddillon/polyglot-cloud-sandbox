package dev.sandbox.lab.claimsintakeapi.domain;

// Which branch of the decision tree handled this batch. STREAMED = read/validate/write done
// synchronously in the request thread (small file, cheap enough to just do inline). QUEUED =
// rows were validated and handed to Kinesis, with a separate consumer doing the actual
// write-out asynchronously (large file, decouples upload latency from processing time).
public enum BatchMode {
    STREAMED,
    QUEUED
}
