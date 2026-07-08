package dev.sandbox.lab.claimsapi.service;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {
    public ClaimNotFoundException(UUID id) {
        super("Claim not found: " + id);
    }
}
