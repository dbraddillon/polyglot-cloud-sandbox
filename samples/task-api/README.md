# task-api

A small Spring Boot REST API — the "this is what an actual enterprise service looks like"
sample, as opposed to `hello-api`'s one-file Lambda. Task management: create tasks (e.g. a
member's care to-dos — schedule a checkup, refill a prescription), move them through a status
workflow, list/get/delete them. The domain is deliberately generic under the hood (`title`,
`status`) — it's just a to-do list — but the example data leans lightly on a health-insurance
member-services theme, same as the rest of this repo's samples.

## Endpoints

| Method | Path                 | Notes                                                       |
|--------|----------------------|--------------------------------------------------------------|
| GET    | `/tasks`             | list all                                                     |
| GET    | `/tasks/{id}`        | 404 if missing                                                |
| POST   | `/tasks`             | `{"title": "..."}`, 400 if blank; 201 + `Location` header     |
| PATCH  | `/tasks/{id}/status` | `{"status": "TODO"\|"IN_PROGRESS"\|"DONE"}`                   |
| DELETE | `/tasks/{id}`        | 204                                                            |

The one bit of actual business logic: a task can't jump straight from `TODO` to `DONE`, it has
to pass through `IN_PROGRESS` first. Skipping that returns `409 Conflict`. It's a deliberately
small rule, there to demonstrate a service layer doing more than pure CRUD passthrough.

## Structure

```
app/
  domain/       Task, TaskStatus, and the two exception types
  repository/   TaskRepository interface + an in-memory implementation
  service/      TaskService - the business rule lives here
  web/          controller, DTOs (records), and the centralized exception handler
  Dockerfile    multi-stage build (Maven -> slim JRE)
infra/          Pulumi program: builds the image, runs the container
```

No database yet — `InMemoryTaskRepository` is a `ConcurrentHashMap`. The repository interface
is there specifically so swapping in a real one (Spring Data JPA + Postgres, most likely) later
doesn't touch the service or controller layers.

## Why this sample doesn't use Floci

`hello-api` deploys to Floci because Lambda + API Gateway are AWS concepts that need an AWS
emulator to stand in for. A Spring Boot service is just... a program that listens on a port.
Containerizing and running it doesn't need an AWS stand-in — it needs a container runtime,
which is already sitting right there (the same Docker daemon Floci itself runs on, via Colima).
So the infra here uses Pulumi's **Docker provider** instead: build the image, run the container,
nothing AWS-shaped about it. This is much closer to how you'd actually containerize and run an
ASP.NET Core Web API day to day.

**Kubernetes** is the natural next step and is explicitly not done here: there's no local k8s
cluster on this machine yet (no kubectl, kind, minikube, or k8s-enabled Colima profile). The
image this sample builds is exactly what a Kubernetes `Deployment` would reference — once a
cluster exists, swapping Pulumi's Docker provider for its Kubernetes provider to write a real
`Deployment` + `Service` is a small, separate follow-up, not a redesign.

## Running it

```
./deploy.sh    # package the jar, build the image, run the container, exercise a couple of endpoints
./destroy.sh   # stop and remove the container/image
```

Runs on `http://localhost:8080`. Local Pulumi state lives in `infra/.pulumi-state/` (gitignored),
same pattern as `hello-api` — see root `CLAUDE.md` for why that's a local file backend and not
Pulumi Cloud.

## Service-level tests (Ruby + Cucumber)

`service-tests/` is a separate, black-box test suite: Gherkin scenarios (`features/`) driven by
Ruby step definitions that hit the running service over real HTTP, via `httparty`, the same way a
QA suite or Postman collection would — as opposed to the JUnit tests under `app/src/test`, which
exercise the code from the inside. It covers the full status lifecycle, the `TODO`→`DONE` 409
business rule, and the 404s, deliberately mirroring what the JUnit suite already checks to show
the same behavior proven two different ways.

```
./deploy.sh                 # start task-api first (from this directory)
cd service-tests
./run.sh                    # bundle install + bundle exec cucumber
```

`run.sh` needs Ruby 3+; macOS's system Ruby is a frozen, EOL 2.6. It looks for Homebrew's
**`ruby@3.3`** specifically (`brew install ruby@3.3`), not the plain `ruby` formula. Ruby 3.4/4.x
added a C23 `stdckdint.h` compatibility shim to its own headers that, under current Apple clang
(16.x), self-matches via `__has_include(<stdckdint.h>)` and then fails to actually include what
it just claimed existed — breaking the native-extension build for `bigdecimal` (pulled in
transitively by `cucumber-cucumber-expressions`), and in principle any gem with a C extension,
not just this one. `ruby@3.3` predates that shim and isn't affected. Details in `run.sh`'s
comments.

## Metrics (Datadog)

`infra/App.java` also stands up a local Datadog agent container on the same Docker network as the
app, and `application.yml` wires Micrometer's StatsD registry (`flavor: datadog`) to send it
metrics — auto-instrumented HTTP request timings (`http.server.requests`) and JVM stats, no custom
code required. Verified for real: hitting the running API repeatedly and checking
`docker exec task-api-datadog-agent agent status | grep -A12 DogStatsD` shows genuine packet/byte
counts climbing, not just "no errors were thrown."

Datadog's agent always needs an API key to boot — there's no local-only mode the way Floci stands
in for AWS. `infra/App.java` defaults to a dummy key (`DD_API_KEY` env var, opt-in override, same
convention as this repo's "Real AWS as an opt-in path"), which is enough for the full local
pipeline — the agent runs, DogStatsD genuinely receives and counts real metrics from task-api —
just with nowhere real to forward them. Set `DD_API_KEY` to your own free-tier key before running
`deploy.sh` if you want an actual live dashboard on top of the same wiring; nothing else changes.

Two gotchas found building this, both non-obvious enough to be worth flagging:

- **`management.metrics.export.statsd.*` (what most tutorials/older docs still show) silently
  no-ops in Spring Boot 3.x.** No error, no warning, no log line — metrics are recorded internally
  (visible at `/actuator/metrics`) but never leave the process. That property is deprecated at
  *error* level in favor of the top-level `management.statsd.metrics.export.*` namespace; found
  by checking Spring's own `spring-configuration-metadata.json` after "it deployed clean but zero
  packets ever arrived" pointed at a config problem rather than a network one.
- **Colima's UDP port-forwarding from host to container is unreliable** — confirmed directly:
  packets sent from the host to a container's UDP port published via `docker run -p` never
  arrived, while the identical packets sent container-to-container over a shared Docker network
  worked immediately. TCP forwarding (used everywhere else in this repo) isn't affected. This is
  why the app reaches the agent via container-name DNS (`DD_AGENT_HOST`), not a host-published
  port — which also happens to be the more realistic pattern anyway (a co-located agent reached
  by service discovery, not host loopback).
