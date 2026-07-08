package dev.sandbox.lab.claimsapi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "claim_line_items")
public class ClaimLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "claim_id")
    private Claim claim;

    private String serviceDescription;
    private int quantity;
    private BigDecimal unitPrice;

    protected ClaimLineItem() {
        // Hibernate needs a no-arg constructor to build entities via reflection - it never
        // calls this from application code, which is why it's protected, not public.
    }

    public ClaimLineItem(String serviceDescription, int quantity, BigDecimal unitPrice) {
        this.serviceDescription = serviceDescription;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void setClaim(Claim claim) {
        this.claim = claim;
    }

    public UUID getId() {
        return id;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
