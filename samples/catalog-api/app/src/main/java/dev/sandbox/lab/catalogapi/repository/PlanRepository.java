package dev.sandbox.lab.catalogapi.repository;

import dev.sandbox.lab.catalogapi.domain.Plan;

import java.util.List;
import java.util.Optional;

public interface PlanRepository {
    List<Plan> findAll();

    Optional<Plan> findById(String id);

    Plan save(Plan plan);

    void deleteById(String id);
}
