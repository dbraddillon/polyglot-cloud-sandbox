package dev.sandbox.lab.catalogapi.web;

import dev.sandbox.lab.catalogapi.domain.Plan;

import java.math.BigDecimal;

public record PlanResponse(String id, String name, BigDecimal monthlyPremium) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getMonthlyPremium());
    }
}
