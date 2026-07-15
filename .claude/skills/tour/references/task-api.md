# task-api

## ELI5

Most real services aren't Lambdas — they're a program that stays running and listens on a port,
same as an ASP.NET Core Web API. This sample is that: a small Spring Boot REST API managing a
list of tasks (a member's care to-dos — schedule a checkup, refill a prescription), with one
real business rule (a task can't skip straight from `TODO` to `DONE`) instead of pure CRUD
passthrough. No database yet — that's `catalog-api`/`claims-api`'s job — just an in-memory
`ConcurrentHashMap` behind a repository interface, deliberately shaped so a real persistence
layer could be swapped in later without touching the service or controller.

## Deploy shape, and why

Plain Docker, not Floci — and this is the sample to point at for *why* that split exists at all.
A Spring Boot app is just a program that listens on a port; it doesn't need an AWS emulator to
run, it needs a container runtime, which is already sitting right there (the same Docker daemon
Floci itself runs on). Pulumi's Docker provider builds the image and runs the container directly
— much closer to how you'd actually containerize and run an ASP.NET Core app day to day than
`hello-api`'s Lambda path is.

## Reading order

1. `app/src/main/java/.../domain/Task.java` + `TaskStatus.java` — the shape of the data and the
   allowed status transitions.
2. `app/src/main/java/.../service/TaskService.java` — the one business rule.
3. `app/src/main/java/.../repository/InMemoryTaskRepository.java` — the in-memory stand-in.
4. `app/src/main/java/.../web/TaskController.java` + `ApiExceptionHandler.java` — the HTTP layer.
5. `infra/src/main/java/.../App.java` — Pulumi's Docker provider: build image, run container.

## Don't miss these

- `domain/Task.java:24` — no auto-properties in Java; explicit `get`/`set` methods are the
  idiom where C# just writes `{ get; set; }` (the compiler generates the same thing either way,
  Java just makes you write it by hand, or have an IDE/Lombok do it).
- `repository/InMemoryTaskRepository.java:26-28` — `Optional<T>` forces the caller to explicitly
  unwrap "might not be there," closer to a real wrapper type than C#'s nullable reference types,
  which are more of a compiler hint than something the runtime actually enforces.
- `service/TaskService.java:17` — the constructor takes its dependencies as parameters, same
  shape as a C# service class's constructor injection; the difference is *how* Spring finds
  what to inject (classpath scanning via `@Repository`/`@Service`/`@Component`) vs. .NET's
  explicit `services.AddScoped<T>()` registration — worth pointing out since it's the kind of
  "magic" that trips people up moving from explicit DI registration to classpath scanning.
- `infra/.../App.java:15` — the comment frames this sample directly: "build the image and run
  it like you would an ASP.NET Core Web API," as opposed to `hello-api`'s AWS-emulator path.

## Running it

```
./deploy.sh    # build the image, run the container, exercise a couple of endpoints
./destroy.sh   # stop and remove the container/image
```

Runs on `http://localhost:8080`. Kubernetes is the natural next step and deliberately not done
here (no local k8s cluster on this machine yet) — the image this sample builds is exactly what a
`Deployment` would reference whenever that changes.

## Service-level tests (Ruby + Cucumber)

`service-tests/` is the one sample in this repo tested from two directions: the JUnit suite
under `app/src/test` exercises the code from the inside, and `service-tests/` is a black-box
Gherkin/Cucumber suite (`features/task_lifecycle.feature` + Ruby step definitions using
`httparty`) that drives the same behavior purely over HTTP, the way a QA suite or a Postman
collection would. Worth a look for readers who haven't seen Cucumber before — SpecFlow is the
closest .NET equivalent (same Gherkin `.feature` files, C# step definitions instead of Ruby).

```
./deploy.sh                 # start task-api first
cd service-tests && ./run.sh
```

Needs Homebrew's `ruby@3.3` specifically, not the plain `ruby` formula — see the "macOS" gotcha
in root `CLAUDE.md` for why (Ruby 4.x's C23 header shim breaks native gem extensions under
current Apple clang; `ruby@3.3` predates it).

## Metrics (Datadog)

`infra/App.java` stands up a local Datadog agent container alongside the app, on the same Docker
network — the third dependency this repo's `infra/App.java` programs manage for task-api, after
the JUnit tests and the Cucumber suite already covered its behavior. `application.yml` wires
Micrometer's StatsD registry (`flavor: datadog`) to send it real metrics, no custom instrumentation
code needed for the auto-captured `http.server.requests`/JVM stats.

Two things worth a look here specifically:
- **`application.yml`'s comment on the `statsd:` block** — a genuinely sneaky Spring Boot 3.x
  gotcha: `management.metrics.export.statsd.*` (still what most tutorials show) is deprecated at
  *error* level and silently does nothing; the real property is the top-level
  `management.statsd.metrics.export.*`. No error, no log line — metrics just never leave the
  process. Found by checking Spring's own configuration metadata, not by guessing.
- **`infra/App.java`'s `DEFAULT_DD_API_KEY` constant** — Datadog's agent always needs an API key
  to boot, unlike Floci which fully stands in for AWS. A dummy key still gets you a fully working
  *local* pipeline (the agent runs, DogStatsD genuinely receives and counts real packets — verified
  by watching those counts climb against live traffic); a real key (opt-in via `DD_API_KEY`, same
  convention as this repo's "Real AWS as an opt-in path") is only needed for an actual dashboard on
  top of the same wiring.

See the main README's "Metrics (Datadog)" section for the Colima UDP-forwarding gotcha this also
surfaced (container-to-container works; host-to-container over a published UDP port doesn't).
