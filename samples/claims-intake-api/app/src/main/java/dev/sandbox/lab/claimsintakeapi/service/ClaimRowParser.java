package dev.sandbox.lab.claimsintakeapi.service;

import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

// A non-instantiable holder for one static method - C#'s `static class` keyword enforces this
// at the language level (no instance members allowed at all); Java has no such modifier, so the
// convention is a `final` class with a `private` no-op constructor to block `new ClaimRowParser()`
// by hand instead.
//
// Deliberately a naive split(","), not a real CSV parser (no quoted-field/embedded-comma
// support) - fine here because the sample only ever feeds it CSVs it generated itself
// (deploy.sh). A real intake pipeline reading arbitrary uploaded files would want a proper
// library (e.g. Apache Commons CSV) instead.
public final class ClaimRowParser {
    private static final BigDecimal MAX_SANE_BILLED_AMOUNT = new BigDecimal("50000.00");

    private ClaimRowParser() {
    }

    public static Optional<ClaimRow> parse(String csvLine) {
        String[] fields = csvLine.split(",", -1);
        if (fields.length != 5) {
            return Optional.empty();
        }

        String claimId = fields[0].strip();
        String memberId = fields[1].strip();
        String serviceDescription = fields[3].strip();
        if (claimId.isEmpty() || memberId.isEmpty() || serviceDescription.isEmpty()) {
            return Optional.empty();
        }

        LocalDate serviceDate;
        try {
            serviceDate = LocalDate.parse(fields[2].strip());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        if (serviceDate.isAfter(LocalDate.now())) {
            return Optional.empty();
        }

        BigDecimal billedAmount;
        try {
            billedAmount = new BigDecimal(fields[4].strip());
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (billedAmount.signum() <= 0 || billedAmount.compareTo(MAX_SANE_BILLED_AMOUNT) > 0) {
            return Optional.empty();
        }

        return Optional.of(new ClaimRow(claimId, memberId, serviceDate, serviceDescription, billedAmount));
    }
}
