package dev.sandbox.lab.ordersapi.web;

import dev.sandbox.lab.ordersapi.domain.OrderLineItem;

import java.math.BigDecimal;
import java.util.UUID;

public record LineItemResponse(UUID id, String productName, int quantity, BigDecimal unitPrice) {
    public static LineItemResponse from(OrderLineItem item) {
        return new LineItemResponse(item.getId(), item.getProductName(), item.getQuantity(), item.getUnitPrice());
    }
}
