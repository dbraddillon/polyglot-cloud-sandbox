package dev.sandbox.lab.claimsapi.service;

import dev.sandbox.lab.claimsapi.domain.Claim;
import dev.sandbox.lab.claimsapi.domain.ClaimLineItem;
import dev.sandbox.lab.claimsapi.repository.ClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClaimService {
    private final ClaimRepository repository;

    public ClaimService(ClaimRepository repository) {
        this.repository = repository;
    }

    public List<Claim> list() {
        return repository.findAll();
    }

    public Claim get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ClaimNotFoundException(id));
    }

    // @Transactional is Spring's declarative transaction management - conceptually close to
    // wrapping a method body in a C# TransactionScope, but done via a dynamic proxy around this
    // method rather than an explicit using block. EF Core, by contrast, wraps each
    // SaveChanges() call in its own transaction implicitly, without needing an annotation at all.
    @Transactional
    public Claim create(String memberName, List<ClaimLineItem> lineItems) {
        return repository.save(new Claim(memberName, lineItems));
    }

    @Transactional
    public Claim approve(UUID id) {
        Claim claim = get(id);
        claim.approve();
        return repository.save(claim);
    }

    @Transactional
    public Claim deny(UUID id) {
        Claim claim = get(id);
        claim.deny();
        return repository.save(claim);
    }
}
