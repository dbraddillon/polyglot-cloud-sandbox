package dev.sandbox.lab.ordersapi.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// No Spring context, no database, no mocks - the status-transition logic lives on the entity
// itself (see Order.markPaid/cancel), so a plain constructor + assertions is enough to test it.
class OrderTest {

    @Test
    void newOrderStartsPending() {
        Order order = new Order("Ada", List.of(new OrderLineItem("Widget", 2, new BigDecimal("9.99"))));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void cannotPayTwice() {
        Order order = new Order("Ada", List.of(new OrderLineItem("Widget", 1, BigDecimal.TEN)));
        order.markPaid();
        assertThatThrownBy(order::markPaid).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cannotCancelAPaidOrder() {
        Order order = new Order("Ada", List.of(new OrderLineItem("Widget", 1, BigDecimal.TEN)));
        order.markPaid();
        assertThatThrownBy(order::cancel).isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void totalSumsLineItems() {
        Order order = new Order("Ada", List.of(
                new OrderLineItem("Widget", 2, new BigDecimal("9.99")),
                new OrderLineItem("Gadget", 1, new BigDecimal("5.00"))));
        assertThat(order.total()).isEqualByComparingTo(new BigDecimal("24.98"));
    }
}
