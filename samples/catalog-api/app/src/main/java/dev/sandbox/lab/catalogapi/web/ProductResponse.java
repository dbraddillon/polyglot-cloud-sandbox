package dev.sandbox.lab.catalogapi.web;

import dev.sandbox.lab.catalogapi.domain.Product;

import java.math.BigDecimal;

public record ProductResponse(String id, String name, BigDecimal price) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice());
    }
}
