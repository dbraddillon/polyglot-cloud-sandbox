package dev.sandbox.lab.catalogapi.service;

import dev.sandbox.lab.catalogapi.domain.Plan;
import dev.sandbox.lab.catalogapi.repository.PlanRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PlanService {
    private final PlanRepository repository;

    public PlanService(PlanRepository repository) {
        this.repository = repository;
    }

    public List<Plan> list() {
        return repository.findAll();
    }

    public Plan get(String id) {
        return repository.findById(id).orElseThrow(() -> new PlanNotFoundException(id));
    }

    public Plan create(String name, BigDecimal monthlyPremium) {
        return repository.save(new Plan(UUID.randomUUID().toString(), name, monthlyPremium));
    }

    public Plan update(String id, String name, BigDecimal monthlyPremium) {
        return repository.updateIfExists(new Plan(id, name, monthlyPremium))
                .orElseThrow(() -> new PlanNotFoundException(id));
    }

    public void delete(String id) {
        get(id);
        repository.deleteById(id);
    }
}
