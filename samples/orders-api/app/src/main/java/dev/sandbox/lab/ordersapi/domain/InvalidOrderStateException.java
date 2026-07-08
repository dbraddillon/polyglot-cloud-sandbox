package dev.sandbox.lab.ordersapi.domain;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(OrderStatus from, OrderStatus to) {
        super("Cannot move order from %s to %s".formatted(from, to));
    }
}
