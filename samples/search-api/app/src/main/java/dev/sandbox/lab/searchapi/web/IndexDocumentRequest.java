package dev.sandbox.lab.searchapi.web;

import jakarta.validation.constraints.NotBlank;

public record IndexDocumentRequest(@NotBlank String title, @NotBlank String body) {
}
