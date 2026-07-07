# floci-java-sandbox

A sandbox for trying out Java microservice + Infrastructure-as-Code patterns, deployed
locally against [Floci](https://floci.io) — a local AWS/Azure/GCP emulator — instead of a
real cloud account. Infra is written in Java too, via [Pulumi](https://www.pulumi.com/).

Each idea lives as its own self-contained sample under `samples/`, with its own service
code, its own Pulumi program, and its own `deploy.sh`/`destroy.sh`.

## Samples

- [`hello-api`](samples/hello-api) — Lambda behind an HTTP API Gateway, returns a JSON
  greeting. The reference sample / template for new ones.

## Quickstart

```
cd samples/hello-api
./deploy.sh    # builds the jar, starts Floci if needed, pulumi up, curls the endpoint
./destroy.sh   # tears the sample's stack down
```

## Prerequisites

- JDK 21, Maven
- [Pulumi CLI](https://www.pulumi.com/docs/install/)
- [Floci CLI](https://floci.io) + Docker (this repo assumes Colima on macOS)

See `CLAUDE.md` for the full layout, the recipe for adding a new sample, and gotchas
specific to running AWS-shaped code against a local emulator.
