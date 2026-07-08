package dev.sandbox.lab.ordersapi.web;

import dev.sandbox.lab.ordersapi.domain.Order;
import dev.sandbox.lab.ordersapi.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerName,
        OrderStatus status,
        Instant createdAt,
        List<LineItemResponse> lineItems,
        BigDecimal total) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getLineItems().stream().map(LineItemResponse::from).toList(),
                order.total());
    }
}
