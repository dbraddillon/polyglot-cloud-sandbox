package dev.sandbox.lab.ordersapi.service;

import dev.sandbox.lab.ordersapi.domain.Order;
import dev.sandbox.lab.ordersapi.domain.OrderLineItem;
import dev.sandbox.lab.ordersapi.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public List<Order> list() {
        return repository.findAll();
    }

    public Order get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    // @Transactional is Spring's declarative transaction management - conceptually close to
    // wrapping a method body in a C# TransactionScope, but done via a dynamic proxy around this
    // method rather than an explicit using block. EF Core, by contrast, wraps each
    // SaveChanges() call in its own transaction implicitly, without needing an annotation at all.
    @Transactional
    public Order create(String customerName, List<OrderLineItem> lineItems) {
        return repository.save(new Order(customerName, lineItems));
    }

    @Transactional
    public Order pay(UUID id) {
        Order order = get(id);
        order.markPaid();
        return repository.save(order);
    }

    @Transactional
    public Order cancel(UUID id) {
        Order order = get(id);
        order.cancel();
        return repository.save(order);
    }
}
