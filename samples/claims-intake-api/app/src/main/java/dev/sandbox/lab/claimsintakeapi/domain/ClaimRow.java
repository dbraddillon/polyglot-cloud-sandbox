package dev.sandbox.lab.claimsintakeapi.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

// One validated row from an intake CSV. A plain record here (no @JsonProperty etc. needed) -
// Jackson serializes/deserializes it field-for-field by default, same as it would a C# record
// with System.Text.Json, which is why this doubles as both the REST response shape and the
// wire format written to Kinesis/the output file.
public record ClaimRow(
        String claimId,
        String memberId,
        LocalDate serviceDate,
        String serviceDescription,
        BigDecimal billedAmount) {
}
