package dev.sandbox.lab.ordersapi.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// @Entity + @Id is JPA/Hibernate's version of an EF Core entity - both are ORMs mapping a class
// to a table, both need a no-arg constructor the framework can call via reflection. This is
// also why Product (catalog-api) and Order can't be records: ORMs of every flavor need mutable,
// reflectively-constructible objects to do their job.
//
// @OneToMany(cascade = CascadeType.ALL) mirrors an EF Core navigation property configured for
// cascading saves/deletes - persist the Order and its line items get persisted with it,
// automatically, in the same transaction.
//
// fetch = FetchType.EAGER is the important part, and it's fighting Hibernate's default on
// purpose. @OneToMany/@ManyToMany default to LAZY (a proxy that loads on first access, backed
// by a live Hibernate session); @ManyToOne/@OneToOne default to EAGER. EF Core's default is a
// different shape entirely: navigation properties load eagerly only if you .Include() them,
// otherwise they're just silently empty - no lazy proxy, no session, no exception, just missing
// data if you forget. Hibernate's version fails loudly instead: this entity's original LAZY
// default threw LazyInitializationException the moment a controller (running with
// spring.jpa.open-in-view: false, so no session left open) tried to serialize lineItems after
// the transaction had already closed. EAGER is the right fix here specifically because this
// collection is small and always needed - it's not a blanket "always use EAGER" rule.
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String customerName;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderLineItem> lineItems = new ArrayList<>();

    protected Order() {
    }

    public Order(String customerName, List<OrderLineItem> lineItems) {
        this.customerName = customerName;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        lineItems.forEach(this::addLineItem);
    }

    public void addLineItem(OrderLineItem item) {
        item.setOrder(this);
        this.lineItems.add(item);
    }

    // Same status-machine idea as task-api's TaskService, just living on the entity itself here
    // instead of the service layer - a legitimate alternative style ("rich domain model")
    // worth seeing both ways.
    public void markPaid() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(status, OrderStatus.PAID);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(status, OrderStatus.CANCELLED);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public BigDecimal total() {
        return lineItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }
}
