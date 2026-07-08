package dev.sandbox.lab.claimsapi.repository;

import dev.sandbox.lab.claimsapi.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// This is the whole repository - Spring Data JPA generates the implementation at runtime from
// this interface alone (query methods, paging, all of it). Contrast with task-api's and
// catalog-api's hand-written repositories: those backing stores (an in-memory map, DynamoDB)
// don't have a Spring Data module doing this for them, so a real implementation class was
// unavoidable there.
//
// JpaRepository<Claim, UUID> covers roughly what EF Core's DbSet<Claim> + SaveChanges() gives
// you, plus a derived-query-method system in place of LINQ - name a method findByMemberName
// and Spring parses that into the right JPQL/SQL for you, no query body required.
public interface ClaimRepository extends JpaRepository<Claim, UUID> {
}
