package dev.sandbox.lab.catalogapi.repository;

import dev.sandbox.lab.catalogapi.domain.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    List<Product> findAll();

    Optional<Product> findById(String id);

    Product save(Product product);

    void deleteById(String id);
}
