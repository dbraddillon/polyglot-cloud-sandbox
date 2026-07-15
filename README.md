# polyglot-cloud-sandbox

A hands-on lab for Java microservice patterns and the cloud-native tooling around them. Each
sample builds something as if it were shipping to production (a Lambda, a containerized API, a
search index, a message queue), but runs entirely on a laptop:

- **Java** for the service code itself
- **[Pulumi](https://www.pulumi.com/)**, written in Java, for the infrastructure — no YAML/HCL,
  the IaC is a real Java program
- A local, disposable deploy target instead of a real cloud account — either
  **[Floci](https://floci.io)** (an AWS/Azure/GCP emulator) for AWS-shaped samples, or plain
  local Docker for anything that's just "a service in a container," same as you'd run it in k8s

Where it's useful, the Java code carries light inline comments comparing a construct to its
closest C#/.NET equivalent — an interface with a default method, a builder replacing C#'s
object initializers, `Optional` vs. nullable references, that kind of thing. Useful if you're
coming to Java from a C# background, or just curious how the two ecosystems compare.

Mostly Java, with a few samples that deliberately aren't: the same Java/Pulumi infra pattern
provisioning a Python Lambda, a Node service, and — furthest from Java — a Clojure service over
Datomic. The point of those is proving the IaC doesn't care what language the workload is
written in, not becoming a tutorial in each language.

The sample domains share a light, made-up health-insurance theme (claims, plans, care tasks) —
just enough to read as *something real* instead of another `hello-world`/`orders` tutorial demo,
without leaning on any actual company, product, or industry-specific detail. Not the point of
the repo; just a coherent backdrop for the Java/Pulumi/cloud-emulation patterns that are.

## What's here

- [`samples/hello-api`](samples/hello-api) — a Lambda behind an HTTP API Gateway, returns a
  JSON greeting. Smallest possible slice through the AWS-shaped path: Java service → Java
  Pulumi program → Floci. Kept deliberately generic — the neutral quickstart template.
- [`samples/task-api`](samples/task-api) — a small Spring Boot REST API (member care tasks, a
  handful of endpoints, a real service layer with one business rule), containerized and run
  via Pulumi's Docker provider. Closer to deploying an ASP.NET Core Web API than to a serverless
  function — no Floci here, see that sample's README for why.
- [`samples/search-api`](samples/search-api) — full-text search over a real OpenSearch engine
  (indexing health-guidance articles), via Spring's `RestClient` (no client library needed).
  Also documents a real Floci limitation found while building it.
- [`samples/catalog-api`](samples/catalog-api) — CRUD over a real DynamoDB table (via Floci) —
  an insurance plan catalog — using the AWS SDK v2 "enhanced" client for annotation-driven
  object mapping.
- [`samples/claims-api`](samples/claims-api) — Spring Data JPA + a real Postgres, relational
  modeling with a `@OneToMany` claim/line-item aggregate. Documents two real bugs found and
  fixed while building it (a classic Hibernate lazy-loading trap, and a Pulumi Docker gotcha).
- [`samples/events-api`](samples/events-api) — the async messaging pattern: an SNS topic fanning
  out to an SQS queue, a publisher endpoint, and an independent background consumer.
- [`samples/claims-intake-api`](samples/claims-intake-api) — the classic "process a large CSV
  under limited resources" problem, with a real decision tree: small uploads are streamed and
  transformed synchronously, large ones are validated and handed to a Kinesis stream for an
  independent consumer to write out. Documents a real Floci performance limitation found while
  building it (per-record cost inside `PutRecords`, not per-call).
- [`samples/python-api`](samples/python-api) — the same Lambda + API Gateway shape as
  `hello-api`, deployed to Floci, but with a Python 3.12 handler instead of a Java one —
  confirms Floci genuinely runs non-Java Lambda runtimes, not just Java.
- [`samples/node-api`](samples/node-api) — the same CRUD-plus-one-business-rule shape as
  `task-api` (member benefit notices), but Express instead of Spring Boot, containerized the
  same way via Pulumi's Docker provider. Version-pinned with volta/`.nvmrc`.
- [`samples/clojure-datomic-api`](samples/clojure-datomic-api) — a care-provider directory over
  Datomic's free, fully local `dev-local` peer library — a genuine Datalog query, not a SQL/JPQL
  one. The first sample with neither Floci nor Docker: `dev-local` is embedded and in-process,
  nothing to provision at all.

More samples get added here as they're built.

## Guided tour

If you're using [Claude Code](https://claude.com/claude-code), this repo ships a `tour` skill
(`.claude/skills/tour/`) — a guided walkthrough of every sample: what problem each pattern
solves, a suggested reading order, and pointers straight to the more interesting comments and
real bugs found while building it (with file:line references, not paraphrased). Ask for a tour,
or invoke `/tour` directly.

## Layout

Every sample under `samples/` is fully self-contained: its own Maven module(s), its own Pulumi
project/stack, its own `deploy.sh`/`destroy.sh`. Nothing is shared at the repo root — see
`CLAUDE.md` for the exact structure and the recipe for adding a new one.

## Quickstart

```
cd samples/<sample-name>
./deploy.sh
./destroy.sh
```

Each sample's README has the specifics — what it deploys to, what to expect, what it's showing off.

## Prerequisites

- JDK 21, Maven
- [Pulumi CLI](https://www.pulumi.com/docs/install/)
- Docker (samples here assume Colima on macOS, but any local Docker daemon works)
- [Floci CLI](https://floci.io) — only needed for the AWS-shaped samples (`hello-api`,
  `catalog-api`, `events-api`, `claims-intake-api`, `python-api`)
- Only needed for the polyglot samples, one each: Node ([volta](https://volta.sh) or nvm, for
  `node-api`), Python 3 (`python-api` — used only to zip/package, any Lambda-compatible version
  works locally), the [Clojure CLI](https://clojure.org/guides/install_clojure) (`clojure-datomic-api`)
