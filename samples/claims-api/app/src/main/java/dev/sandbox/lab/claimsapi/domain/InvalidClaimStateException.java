package dev.sandbox.lab.claimsapi.domain;

public class InvalidClaimStateException extends RuntimeException {
    public InvalidClaimStateException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move claim from %s to %s".formatted(from, to));
    }
}
