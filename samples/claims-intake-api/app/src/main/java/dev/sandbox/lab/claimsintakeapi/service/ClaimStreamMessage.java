package dev.sandbox.lab.claimsintakeapi.service;

import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;

// The envelope put on the Kinesis stream: either one validated row, or an end-of-batch marker
// carrying the count the consumer should have written by the time it's done (a cheap integrity
// check - see KinesisBatchConsumer). This could instead be a sealed interface with two record
// implementations and an exhaustive `switch` pattern-match (Java 21's closest equivalent to a
// C# discriminated union) - kept as one flat tagged record instead because it maps straight
// onto Jackson's default (de)serialization with no custom subtype config, which matters more
// here than the extra type-safety would.
record ClaimStreamMessage(String batchId, String type, ClaimRow row, Long expectedValidRows) {
    static final String TYPE_ROW = "ROW";
    static final String TYPE_END = "END";

    static ClaimStreamMessage row(String batchId, ClaimRow row) {
        return new ClaimStreamMessage(batchId, TYPE_ROW, row, null);
    }

    static ClaimStreamMessage end(String batchId, long expectedValidRows) {
        return new ClaimStreamMessage(batchId, TYPE_END, null, expectedValidRows);
    }
}
