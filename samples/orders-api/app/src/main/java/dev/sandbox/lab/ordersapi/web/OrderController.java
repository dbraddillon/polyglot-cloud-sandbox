package dev.sandbox.lab.ordersapi.web;

import dev.sandbox.lab.ordersapi.domain.OrderLineItem;
import dev.sandbox.lab.ordersapi.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<OrderResponse> list() {
        return service.list().stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(service.get(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderLineItem> items = request.lineItems().stream()
                .map(li -> new OrderLineItem(li.productName(), li.quantity(), li.unitPrice()))
                .toList();
        var order = service.create(request.customerName(), items);
        return ResponseEntity.created(URI.create("/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(@PathVariable UUID id) {
        return OrderResponse.from(service.pay(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(service.cancel(id));
    }
}
