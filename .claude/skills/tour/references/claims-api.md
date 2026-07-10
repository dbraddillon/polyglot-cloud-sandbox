# claims-api

## ELI5

The classic relational-database story: a claim with a list of line items, modeled the way
you've always modeled a parent/children relationship in a SQL database — foreign keys, a join,
an ORM that hides most of the SQL. This sample is Spring Data JPA + a real Postgres, an insurance
claims aggregate (`Claim` with a `@OneToMany` of `ClaimLineItem`s), plus a business-rule workflow
(`PENDING` → `APPROVED`/`DENIED`). It's the direct relational counterpart to `catalog-api`'s
NoSQL approach — read them back to back for the contrast.

Originally built as a generic "orders" tutorial domain, then renamed once the whole sample set
got its light theme — the one sample where that rename touched folder/package names too, not
just class names, specifically because it's *the* cliché e-commerce tutorial domain.

## Deploy shape, and why

Plain Docker, not Floci. Floci's RDS emulation is real (a genuine Postgres container per
instance) but keeps that container off the host network entirely, by design — unreachable from
anything not on Floci's internal Docker network. Same class of finding as `search-api`'s
OpenSearch limitation, different specific gap. The fix is the same move too: run plain Postgres
directly via Pulumi's Docker provider, on a Pulumi-managed network so the app can reach it by
container name (`claims-db`) instead of an IP.

## Reading order

1. `app/src/main/java/.../domain/Claim.java` — the JPA entity, and the best single comment in
   the whole repo (see below).
2. `app/src/main/java/.../domain/ClaimLineItem.java`, `ClaimStatus.java`.
3. `app/src/main/java/.../repository/ClaimRepository.java` — a bare Spring Data interface.
4. `app/src/main/java/.../service/ClaimService.java` — `@Transactional` boundaries.
5. `infra/src/main/java/.../App.java` — a Docker network + Postgres container + app container.

## Don't miss these

- `domain/Claim.java:20-23` — `@Entity` + `@Id` is JPA/Hibernate's version of an EF Core entity:
  both ORMs map a class to a table, both need a no-arg constructor the framework can call via
  reflection. This is also *why* `Claim` (and `catalog-api`'s `Plan`) can't be Java records —
  every ORM flavor needs mutable, reflectively-constructible objects.
- `domain/Claim.java:29-38` — the best "real bug found building this" comment in the repo.
  `@OneToMany` defaults to `FetchType.LAZY` in Hibernate (a proxy loading on first access, backed
  by a live session); this entity's original LAZY default threw a genuine
  `LazyInitializationException` the moment a controller tried to serialize `lineItems` after the
  transaction had already closed (`spring.jpa.open-in-view: false`). Fixed with
  `fetch = FetchType.EAGER` — deliberately scoped to this one small, always-needed collection,
  not a blanket rule. The EF Core contrast is worth sitting with: its default is the *opposite*
  failure mode — unpopulated navigation properties are just silently empty unless you
  `.Include()` them. No exception, no lazy proxy, no session — arguably more dangerous, since it
  never crashes, it just quietly returns incomplete data.
- `repository/ClaimRepository.java:14` — `JpaRepository<Claim, UUID>` covers roughly what EF
  Core's `DbSet<Claim>` + `SaveChanges()` gives you, except Spring Data generates the
  implementation for you from the interface alone — no hand-written repository class at all
  (contrast with `catalog-api`'s `DynamoPlanRepository`, which *is* hand-written, since no
  Spring Data module does this part for DynamoDB).
- `service/ClaimService.java:28-31` — `@Transactional` is conceptually close to wrapping a
  method body in a C# `TransactionScope`, but mechanically different: Spring does it via a
  dynamic proxy wrapped around the method call, not an explicit `using` block you write
  yourself.

## A Pulumi bug found while building this

Rebuilding the app image didn't update the running container — the `Container` resource
originally referenced `image.imageName()` (a stable tag like `claims-api:local`). Rebuilding
produces new content under the *same* tag, so Pulumi saw no diff and left the old container
running, silently. `docker restart` doesn't fix it either (it reuses the already-extracted
container, not the new image). Fixed by referencing `image.repoDigest()` instead, which changes
whenever the content does — now a rebuild correctly triggers a container replace. Same latent bug
existed in `task-api` too, fixed there at the same time.

## Running it

```
./deploy.sh    # build the image, start Postgres + the app, submit a claim, approve it
./destroy.sh   # tear both containers and the network down
```

Runs on `http://localhost:8083`, Postgres published on `5433` if you want to `psql` in directly.
