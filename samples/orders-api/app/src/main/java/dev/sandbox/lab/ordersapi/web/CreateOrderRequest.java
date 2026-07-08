package dev.sandbox.lab.ordersapi.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// @Valid on the list cascades validation into each LineItemRequest - without it, Bean
// Validation only checks that the list itself isn't empty, not that each element is valid.
public record CreateOrderRequest(
        @NotBlank String customerName,
        @NotEmpty @Valid List<LineItemRequest> lineItems) {
}
