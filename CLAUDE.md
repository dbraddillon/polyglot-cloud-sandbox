# polyglot-cloud-sandbox

General sandbox for Java microservice + IaC experiments, deployed locally instead of to real
cloud accounts. Each experiment is a self-contained **sample**; nothing here is meant to run in
production. Two deploy shapes exist side by side, chosen per-sample based on what's realistic:

- **AWS-shaped samples** (Lambda, API Gateway, etc.) deploy to [Floci](https://floci.io), a
  local AWS/Azure/GCP emulator — see `samples/hello-api`.
- **Plain services** (a Spring Boot app, anything that's just "a container that listens on a
  port") deploy straight to the local Docker daemon via Pulumi's Docker provider — no AWS
  emulation needed, no AWS-shaped resources to fake. See `samples/task-api`. A real Kubernetes
  target is the natural next step for this shape once a local cluster exists (none does yet on
  the author's machine — no kubectl/kind/minikube/k8s-enabled Colima profile).

The author is a longtime C#/.NET engineer picking up Java for a new role — see "Java comment
policy" below. If that's also your background, the inline comments in the Java code are meant
to make this navigable without needing to learn Java from scratch first.

## Layout

```
samples/
  hello-api/          # Lambda + HTTP API Gateway, deployed to Floci
    app/               Java service code (its own Maven module)
    infra/             Pulumi program in Java (AWS provider) provisioning that service
    deploy.sh          build + pulumi up + curl the result
    destroy.sh         pulumi destroy
    README.md          sample-specific notes/gotchas
  task-api/           # Spring Boot REST API, containerized and run via local Docker
    app/               Spring Boot service (controller/service/repository/domain layers)
    infra/             Pulumi program in Java (Docker provider): build image, run container
    deploy.sh
    destroy.sh
    README.md
  <next-sample>/       same shape - pick whichever deploy story actually fits what you're building
```

Every sample is independent: its own Maven module(s), its own Pulumi project/stack, its
own local Pulumi state (`infra/.pulumi-state/`, gitignored), its own `deploy.sh`/`destroy.sh`.
No shared root build — run each sample from inside its own directory. This is deliberate:
the point of this repo is trying different Java patterns side by side without them tangling
into one growing app.

## Adding a new sample

Copy whichever existing sample is the closer match (`hello-api` for anything AWS-shaped,
`task-api` for a plain containerized service), then:
1. Rename the sample directory (`samples/<new-name>/`).
2. Repackage the Java code: `dev.sandbox.lab.<newname>` for the app,
   `dev.sandbox.lab.<newname>.infra` for the Pulumi program — keeps every sample's
   package space isolated so nothing collides if code ever gets copy-pasted between them.
3. Update both `pom.xml` groupIds and the infra `pom.xml`'s `<mainClass>` to match.
4. Update `infra/Pulumi.yaml` `name:` to the new sample name (each sample = its own Pulumi
   project) and fix any hardcoded fully-qualified class name strings (e.g. a Lambda
   `.handler(...)` string) to the new package.
5. `deploy.sh`/`destroy.sh` need no path changes if copied as-is — they're written relative
   to their own directory (`cd "$(dirname "$0")"`), not the repo root.

## Common gotchas

**Floci-based samples (hello-api):**
- **Floci gives fake creds + one unified endpoint.** `AWS_ENDPOINT_URL=http://localhost:4566`,
  `AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test`, `AWS_DEFAULT_REGION=us-east-1`. The
  Pulumi AWS provider (v7.x) picks these up from the environment automatically — no
  `Provider`/`endpoints` block needed in the Pulumi program.
- **AWS-shaped outputs lie about being resolvable locally.** Things like API Gateway's
  `apiUrl` render as real `*.execute-api.*.amazonaws.com` hostnames, but Floci doesn't do
  subdomain-based routing — you need the emulator's path-based route instead (for API
  Gateway: `http://localhost:4566/restapis/<api-id>/$default/_user_request_/<route>`; other
  services likely have their own local-invoke pattern worth checking similarly).
- **Floci's container lifecycle is machine-wide, not per-sample.** A separate pair of scripts
  (`~/floci-sandbox/start.sh` / `stop.sh` on the author's machine — adjust the path in
  `deploy.sh` if you're setting this up elsewhere) manage the actual Colima + Floci container
  and its persisted emulator state, shared across every project on that machine that uses
  Floci. `deploy.sh` calls `start.sh` automatically if Floci isn't already running.

**Docker-based samples (task-api):**
- **Pulumi's Docker `Image` resource needs `skipPush(true)` for a local-only build.** Without
  a registry configured, the provider assumes you're pushing somewhere; `skipPush(true)` is
  what makes it build straight into the local Docker daemon instead (confirmed against the
  provider's own source — it runs `docker inspect` on the built tag afterward rather than
  attempting registry auth). No Floci involved at all for this shape.
- **`ImageArgs.builder()` has two same-named `.build(...)` methods.** One (takes a
  `DockerBuildArgs`) sets the build config; the other (no args) finalizes the builder itself.
  Resolved by Java overloading, but it reads strangely the first time.

**Every sample:**
- **Pulumi state is local per-sample, not Pulumi Cloud.** `PULUMI_BACKEND_URL=file://.../infra/.pulumi-state`
  with an empty `PULUMI_CONFIG_PASSPHRASE`, set per-command in `deploy.sh`/`destroy.sh` —
  deliberately *not* `pulumi login`, which would flip the global CLI default backend for every
  other Pulumi project on the machine running this. The state dir doesn't exist until first
  deploy; `deploy.sh` `mkdir -p`s it.

## Java comment policy

Java code in this repo carries light inline comments calling out the C#/.NET parallel for a
given construct where it's genuinely useful — an interface default method, a builder replacing
C#'s object initializers, `Optional` vs. nullable references, checked exceptions, Spring's
`@Component`-family annotations vs. explicit DI registration, that kind of thing. Not every
line, and not where the parallel is trivial (`if`, `for`) — just the spots where Java (or a
framework like Spring) diverges enough from C#/ASP.NET Core to trip someone up.

## Prerequisites

JDK 21, Maven, Pulumi CLI, Docker (Colima, Docker Desktop, whatever — samples assume Colima on
macOS specifically for the Floci-lifecycle scripts). [Floci CLI](https://floci.io) only needed
for AWS-shaped samples like `hello-api`.
