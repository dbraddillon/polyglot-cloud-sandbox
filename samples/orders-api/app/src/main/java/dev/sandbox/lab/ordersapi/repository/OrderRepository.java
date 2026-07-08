package dev.sandbox.lab.ordersapi.repository;

import dev.sandbox.lab.ordersapi.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// This is the whole repository - Spring Data JPA generates the implementation at runtime from
// this interface alone (query methods, paging, all of it). Contrast with task-api's and
// catalog-api's hand-written repositories: those backing stores (an in-memory map, DynamoDB)
// don't have a Spring Data module doing this for them, so a real implementation class was
// unavoidable there.
//
// JpaRepository<Order, UUID> covers roughly what EF Core's DbSet<Order> + SaveChanges() gives
// you, plus a derived-query-method system in place of LINQ - name a method findByCustomerName
// and Spring parses that into the right JPQL/SQL for you, no query body required.
public interface OrderRepository extends JpaRepository<Order, UUID> {
}
