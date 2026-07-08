package dev.sandbox.lab.claimsapi.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ClaimLineItemRequest(@NotBlank String serviceDescription, @Positive int quantity, @Positive BigDecimal unitPrice) {
}
