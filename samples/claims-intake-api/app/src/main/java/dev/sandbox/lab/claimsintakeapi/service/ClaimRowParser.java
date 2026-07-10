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
// support) - a documented limitation of this sample, not a safety property of its endpoint
// (the real HTTP endpoint accepts any upload, not just what deploy.sh generates). A comma
// inside serviceDescription misaligns or rejects that row rather than exposing anything, so
// the impact is correctness/availability, not data exposure - still, a real intake pipeline
// reading arbitrary uploads would want a proper library (e.g. Apache Commons CSV) instead.
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
        // A pathological scale (e.g. "1e-2147483600") parses fine and even survives the
        // compareTo() sanity check below - BigDecimal's fast path for comparison doesn't need
        // to materialize the value - but blows up later with an unchecked ArithmeticException
        // the moment anything tries to .add() it to a running total, since aligning scales
        // internally allocates a BigInteger sized to the scale difference. Bounding scale and
        // precision here, at the same validation boundary as every other check, keeps that
        // from ever reaching arithmetic code downstream instead of catching it after the fact.
        if (Math.abs(billedAmount.scale()) > 10 || billedAmount.precision() > 15) {
            return Optional.empty();
        }
        if (billedAmount.signum() <= 0 || billedAmount.compareTo(MAX_SANE_BILLED_AMOUNT) > 0) {
            return Optional.empty();
        }

        return Optional.of(new ClaimRow(claimId, memberId, serviceDate, serviceDescription, billedAmount));
    }
}
