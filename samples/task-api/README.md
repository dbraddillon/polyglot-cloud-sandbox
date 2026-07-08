# task-api

A small Spring Boot REST API — the "this is what an actual enterprise service looks like"
sample, as opposed to `hello-api`'s one-file Lambda. Task management: create tasks, move them
through a status workflow, list/get/delete them.

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
