# polyglot-cloud-sandbox

A hands-on lab for picking up Java — and the cloud-native tooling around it — coming from
~18 years of C#/.NET. Each sample builds something as if it were shipping to production (a
Lambda, a containerized API, eventually something backed by Redis), but runs entirely on a
laptop:

- **Java** for the service code itself
- **[Pulumi](https://www.pulumi.com/)**, written in Java, for the infrastructure — no YAML/HCL,
  the IaC is a real Java program
- A local, disposable deploy target instead of a real cloud account — either
  **[Floci](https://floci.io)** (an AWS/Azure/GCP emulator) for AWS-shaped samples, or plain
  local Docker for anything that's just "a service in a container," same as you'd run it in k8s

Where it's useful, the Java code carries lightweight inline comments pointing out the C#/.NET
parallel for a given construct — an interface with a default method, a builder replacing C#'s
object initializers, `Optional` vs. nullable references, that kind of thing. If you know C#
and are curious what Java (or Pulumi, or Spring, or a LocalStack-style local cloud emulator)
looks like day to day, this repo is written with you in mind too.

## What's here

- [`samples/hello-api`](samples/hello-api) — a Lambda behind an HTTP API Gateway, returns a
  JSON greeting. Smallest possible slice through the AWS-shaped path: Java service → Java
  Pulumi program → Floci.
- [`samples/task-api`](samples/task-api) — a small Spring Boot REST API (task management, a
  handful of endpoints, a real service layer with one business rule), containerized and run
  via Pulumi's Docker provider. The "how you'd actually build and ship a microservice" sample —
  closer to deploying an ASP.NET Core Web API than to a serverless function. No Floci here;
  see that sample's README for why, and for the path to a real Kubernetes deploy later.

More samples get added here as they're built — the plan includes something Redis-backed next,
plus whatever else turns out to be a useful, small, self-contained example of the stack.

## Layout

Every sample under `samples/` is fully self-contained: its own Maven module(s), its own Pulumi
project/stack, its own `deploy.sh`/`destroy.sh`. Nothing is shared at the repo root — see
`CLAUDE.md` for the exact structure and the recipe for adding a new one.

## Quickstart

```
cd samples/hello-api      # or samples/task-api
./deploy.sh
./destroy.sh
```

Each sample's README has the specifics — what it deploys to, what to expect, what it's showing off.

## Prerequisites

- JDK 21, Maven
- [Pulumi CLI](https://www.pulumi.com/docs/install/)
- Docker (samples here assume Colima on macOS, but any local Docker daemon works)
- [Floci CLI](https://floci.io) — only needed for AWS-shaped samples like `hello-api`
