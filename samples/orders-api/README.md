# orders-api

A Spring Boot + Spring Data JPA order service — real relational modeling (an `Order` with a
`@OneToMany` of `OrderLineItem`s) against a real local Postgres, contrasted with catalog-api's
NoSQL/DynamoDB approach and task-api's in-memory one.

## Endpoints

| Method | Path                | Notes                                              |
|--------|---------------------|-----------------------------------------------------|
| GET    | `/orders`           | list all                                             |
| GET    | `/orders/{id}`      | 404 if missing                                       |
| POST   | `/orders`           | customer name + a non-empty list of line items       |
| POST   | `/orders/{id}/pay`  | PENDING → PAID; 409 if not PENDING                   |
| POST   | `/orders/{id}/cancel` | PENDING → CANCELLED; 409 if not PENDING            |

## Structure

```
app/
  domain/       Order, OrderLineItem (JPA entities), OrderStatus, InvalidOrderStateException
  repository/   OrderRepository - a bare Spring Data JPA interface, no implementation needed
  service/      OrderService (@Transactional boundaries) + OrderNotFoundException
  web/          controller, request/response DTOs, exception handler
  Dockerfile
infra/          Pulumi program: a Docker network, a Postgres container, the app's image + container
```

## Infra: two containers, one network

Unlike task-api (one container, no database), this sample provisions a Postgres container
alongside the app's own, both on a Pulumi-managed Docker network so the app can reach the
database by container name (`orders-db`) instead of an IP or the host's published port — the
same reason Docker Compose services and Kubernetes Pods use DNS names instead of IPs.

No Floci here, same reasoning as task-api: Floci's own RDS emulation does spin up a real Postgres
container (confirmed while building catalog-api's sibling samples), but keeps it on an
internal-only Docker network by design, unreachable from the host. A plain Postgres container
via Pulumi's Docker provider is simpler and just as realistic.

## Two real bugs found and fixed while building this

Worth knowing about since they're exactly the kind of thing that shows up in real Spring/Docker
work, not sandbox-specific quirks:

1. **`LazyInitializationException` on every endpoint except create.** `@OneToMany` defaults to
   `FetchType.LAZY` in Hibernate — a proxy that loads on first access via a live session. With
   `spring.jpa.open-in-view: false` (deliberately set — see `application.yml`), that session is
   gone by the time a controller serializes the response, so touching `order.getLineItems()`
   outside the `@Transactional` boundary blew up with a 500. Fixed by switching this specific
   relationship to `FetchType.EAGER` (see the comment on `Order.lineItems` for why that's the
   right call here and not a blanket rule). Worth knowing: EF Core's default is the opposite
   shape — navigation properties are simply *not populated* unless you `.Include()` them, no
   lazy proxy, no exception, just silently empty data if you forget.
2. **Rebuilding the app image didn't update the running container.** The `Container` resource
   originally referenced `image.imageName()` — a stable tag (`orders-api:local`). Rebuilding
   produces new image content under the *same* tag, so Pulumi saw no diff and left the old
   container running. Fixed by referencing `image.repoDigest()` instead, which changes whenever
   the content does, so a rebuild now correctly triggers a container replace. `docker restart`
   doesn't fix this either — it reuses the already-extracted container, not the new image.

## Known, accepted race: app vs. Postgres startup

The app container isn't gated on Postgres actually being ready to accept connections — Pulumi's
Docker provider (Terraform-bridged) has no `depends_on: condition: service_healthy` equivalent.
Spring Boot fails fast if the database isn't reachable at startup; `restart: unless-stopped`
means Docker just retries a few seconds later, by which point Postgres is up. Same class of
problem Kubernetes readiness probes exist to solve. `deploy.sh`'s retry loop absorbs this.

## Running it

```
./deploy.sh    # build the image, start Postgres + the app, create an order, mark it paid
./destroy.sh   # tear both containers and the network down
```

Runs on `http://localhost:8083`, Postgres published on `5433` if you want to `psql` in directly.
