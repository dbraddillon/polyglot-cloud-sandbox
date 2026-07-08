package dev.sandbox.lab.claimsapi.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// No Spring context, no database, no mocks - the status-transition logic lives on the entity
// itself (see Claim.approve/deny), so a plain constructor + assertions is enough to test it.
class ClaimTest {

    @Test
    void newClaimStartsPending() {
        Claim claim = new Claim("Ada", List.of(new ClaimLineItem("Office visit", 1, new BigDecimal("150.00"))));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PENDING);
    }

    @Test
    void cannotApproveTwice() {
        Claim claim = new Claim("Ada", List.of(new ClaimLineItem("Office visit", 1, BigDecimal.TEN)));
        claim.approve();
        assertThatThrownBy(claim::approve).isInstanceOf(InvalidClaimStateException.class);
    }

    @Test
    void cannotDenyAnApprovedClaim() {
        Claim claim = new Claim("Ada", List.of(new ClaimLineItem("Office visit", 1, BigDecimal.TEN)));
        claim.approve();
        assertThatThrownBy(claim::deny).isInstanceOf(InvalidClaimStateException.class);
    }

    @Test
    void totalSumsLineItems() {
        Claim claim = new Claim("Ada", List.of(
                new ClaimLineItem("Office visit", 1, new BigDecimal("150.00")),
                new ClaimLineItem("Lab work - CBC panel", 1, new BigDecimal("45.50"))));
        assertThat(claim.total()).isEqualByComparingTo(new BigDecimal("195.50"));
    }
}
