package dev.sandbox.lab.claimsapi.web;

import dev.sandbox.lab.claimsapi.domain.Claim;
import dev.sandbox.lab.claimsapi.domain.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        String memberName,
        ClaimStatus status,
        Instant createdAt,
        List<ClaimLineItemResponse> lineItems,
        BigDecimal total) {
    public static ClaimResponse from(Claim claim) {
        return new ClaimResponse(
                claim.getId(),
                claim.getMemberName(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getLineItems().stream().map(ClaimLineItemResponse::from).toList(),
                claim.total());
    }
}
