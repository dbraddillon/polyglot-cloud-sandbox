package dev.sandbox.lab.claimsapi.domain;

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
// also why Plan (catalog-api) and Claim can't be records: ORMs of every flavor need mutable,
// reflectively-constructible objects to do their job.
//
// @OneToMany(cascade = CascadeType.ALL) mirrors an EF Core navigation property configured for
// cascading saves/deletes - persist the Claim and its line items get persisted with it,
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
@Table(name = "claims")
public class Claim {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String memberName;

    @Enumerated(EnumType.STRING)
    private ClaimStatus status;

    private Instant createdAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ClaimLineItem> lineItems = new ArrayList<>();

    protected Claim() {
    }

    public Claim(String memberName, List<ClaimLineItem> lineItems) {
        this.memberName = memberName;
        this.status = ClaimStatus.PENDING;
        this.createdAt = Instant.now();
        lineItems.forEach(this::addLineItem);
    }

    public void addLineItem(ClaimLineItem item) {
        item.setClaim(this);
        this.lineItems.add(item);
    }

    // Same status-machine idea as task-api's TaskService, just living on the entity itself here
    // instead of the service layer - a legitimate alternative style ("rich domain model")
    // worth seeing both ways.
    public void approve() {
        if (status != ClaimStatus.PENDING) {
            throw new InvalidClaimStateException(status, ClaimStatus.APPROVED);
        }
        this.status = ClaimStatus.APPROVED;
    }

    public void deny() {
        if (status != ClaimStatus.PENDING) {
            throw new InvalidClaimStateException(status, ClaimStatus.DENIED);
        }
        this.status = ClaimStatus.DENIED;
    }

    public BigDecimal total() {
        return lineItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public String getMemberName() {
        return memberName;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ClaimLineItem> getLineItems() {
        return lineItems;
    }
}
