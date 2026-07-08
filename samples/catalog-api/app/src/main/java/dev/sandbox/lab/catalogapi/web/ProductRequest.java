package dev.sandbox.lab.catalogapi.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProductRequest(@NotBlank String name, @Positive BigDecimal price) {
}
