package dev.sandbox.lab.catalogapi.repository;

import dev.sandbox.lab.catalogapi.domain.Plan;

import java.util.List;
import java.util.Optional;

public interface PlanRepository {
    List<Plan> findAll();

    Optional<Plan> findById(String id);

    Plan save(Plan plan);

    // Conditional update: succeeds only if an item with this id already exists. Returns empty
    // instead of throwing so the repository layer stays free of domain exceptions - the service
    // decides what "not found" means to a caller.
    Optional<Plan> updateIfExists(Plan plan);

    void deleteById(String id);
}
