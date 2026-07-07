# polyglot-cloud-sandbox

A hands-on lab for picking up Java — and the cloud-native tooling around it — coming from
~18 years of C#/.NET. Each sample builds something as if it were shipping to AWS (a Lambda,
an API, eventually something backed by Redis), but runs entirely on a laptop:

- **Java** for the service code itself
- **[Pulumi](https://www.pulumi.com/)**, written in Java, for the infrastructure — no YAML/HCL,
  the IaC is a real Java program
- **[Floci](https://floci.io)**, a local AWS/Azure/GCP emulator, as the deploy target — no real
  cloud account, no cost, fully disposable

Where it's useful, the Java code carries lightweight inline comments pointing out the C#/.NET
parallel for a given construct — an interface with a default method, a builder replacing C#'s
object initializers, `Optional` vs. nullable references, that kind of thing. If you know C#
and are curious what Java (or Pulumi, or a LocalStack-style local cloud emulator) looks like
day to day, this repo is written with you in mind too.

## What's here

- [`samples/hello-api`](samples/hello-api) — a Lambda behind an HTTP API Gateway, returns a
  JSON greeting. The reference sample: smallest possible slice through the whole stack
  (Java service → Java Pulumi program → Floci), and the template for new samples.

More samples get added here as they're built — the plan includes something Redis-backed next,
plus whatever else turns out to be a useful, small, self-contained example of the stack.

## Layout

Every sample under `samples/` is fully self-contained: its own Maven module(s), its own Pulumi
project/stack, its own `deploy.sh`/`destroy.sh`. Nothing is shared at the repo root — see
`CLAUDE.md` for the exact structure and the recipe for adding a new one.

## Quickstart

```
cd samples/hello-api
./deploy.sh    # builds the jar, starts Floci if needed, pulumi up, curls the endpoint
./destroy.sh   # tears the sample's stack down
```

## Prerequisites

- JDK 21, Maven
- [Pulumi CLI](https://www.pulumi.com/docs/install/)
- [Floci CLI](https://floci.io) + Docker (samples here assume Colima on macOS, but Floci itself
  just needs a working Docker daemon)
