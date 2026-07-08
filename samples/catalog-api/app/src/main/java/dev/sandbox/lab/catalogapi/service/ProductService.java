package dev.sandbox.lab.catalogapi.service;

import dev.sandbox.lab.catalogapi.domain.Product;
import dev.sandbox.lab.catalogapi.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> list() {
        return repository.findAll();
    }

    public Product get(String id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product create(String name, BigDecimal price) {
        return repository.save(new Product(UUID.randomUUID().toString(), name, price));
    }

    public Product update(String id, String name, BigDecimal price) {
        get(id); // 404s if missing, same convention as task-api
        return repository.save(new Product(id, name, price));
    }

    public void delete(String id) {
        get(id);
        repository.deleteById(id);
    }
}
