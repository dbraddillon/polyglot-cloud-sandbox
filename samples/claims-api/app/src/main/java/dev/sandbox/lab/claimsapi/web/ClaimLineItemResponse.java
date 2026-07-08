package dev.sandbox.lab.claimsapi.web;

import dev.sandbox.lab.claimsapi.domain.ClaimLineItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ClaimLineItemResponse(UUID id, String serviceDescription, int quantity, BigDecimal unitPrice) {
    public static ClaimLineItemResponse from(ClaimLineItem item) {
        return new ClaimLineItemResponse(item.getId(), item.getServiceDescription(), item.getQuantity(), item.getUnitPrice());
    }
}
