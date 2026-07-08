package dev.sandbox.lab.ordersapi.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LineItemRequest(@NotBlank String productName, @Positive int quantity, @Positive BigDecimal unitPrice) {
}
