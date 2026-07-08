package dev.sandbox.lab.claimsapi.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// @Valid on the list cascades validation into each ClaimLineItemRequest - without it, Bean
// Validation only checks that the list itself isn't empty, not that each element is valid.
public record CreateClaimRequest(
        @NotBlank String memberName,
        @NotEmpty @Valid List<ClaimLineItemRequest> lineItems) {
}
