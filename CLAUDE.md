# polyglot-cloud-sandbox

General sandbox for Java microservice + IaC experiments, deployed locally instead of to real
cloud accounts. Each experiment is a self-contained **sample**; nothing here is meant to run in
production. Two deploy shapes exist side by side, chosen per-sample based on what's realistic:

- **AWS-shaped samples** deploy to [Floci](https://floci.io), a local AWS/Azure/GCP emulator —
  `hello-api` (Lambda + API Gateway), `catalog-api` (DynamoDB), `events-api` (SNS + SQS).
- **Plain services** (a Spring Boot app, anything that's just "a container that listens on a
  port," or a piece of infra Floci can't reliably emulate) deploy via Pulumi's Docker provider
  straight to the local Docker daemon — `task-api`, `claims-api` (Postgres), `search-api`
  (OpenSearch — Floci can run this too, but has a bug that makes Pulumi's AWS provider hang
  forever, see the gotchas below).

The author is a longtime C#/.NET engineer picking up Java for a new role — see "Java comment
policy" below. If that's also your background, the inline comments in the Java code are meant
to make this navigable without needing to learn Java from scratch first.

**Sample domains share a light health-insurance theme** (claims, plans, care tasks, benefits
articles) — deliberately generic, no real company/product/industry specifics, just enough to
read as something tangible instead of another `hello-world`/`orders` tutorial demo. `hello-api`
is the one exception, kept fully neutral as the template/quickstart sample. When adding a new
sample, a light nod to the theme in the domain naming is nice-to-have, not required — don't
force it if the tech pattern being demonstrated doesn't lend itself to it.

## Layout

```
samples/
  hello-api/    Lambda + HTTP API Gateway, deployed to Floci (kept theme-neutral)
  task-api/     Spring Boot REST API (member care tasks), local Docker (no Floci)
  search-api/   Spring Boot + OpenSearch (health articles), local Docker (Floci's emulation hangs)
  catalog-api/  Spring Boot + DynamoDB (insurance plans), deployed to Floci
  claims-api/   Spring Boot + Spring Data JPA + Postgres (insurance claims), local Docker
  events-api/   Spring Boot + SNS/SQS async messaging (claim/appointment events), deployed to Floci
  <next>/       same shape - pick whichever deploy story actually fits what you're building
```

Every sample has the same internal shape:
```
app/            Java service code (its own Maven module) - controller/service/repository/domain
                layers, a Dockerfile if it gets containerized
infra/          Pulumi program in Java provisioning whatever the sample needs
deploy.sh       build + provision + curl/exercise the result
destroy.sh      tear down
README.md       sample-specific notes/gotchas, why it's built the way it is
```

Every sample is independent: its own Maven module(s), its own Pulumi project/stack, its
own local Pulumi state (`infra/.pulumi-state/`, gitignored), its own `deploy.sh`/`destroy.sh`.
No shared root build — run each sample from inside its own directory. This is deliberate:
the point of this repo is trying different Java patterns side by side without them tangling
into one growing app.

## Adding a new sample

Copy whichever existing sample is the closer match, then:
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

**Floci-based samples:**
- **Floci gives fake creds + one unified endpoint.** `AWS_ENDPOINT_URL=http://localhost:4566`,
  `AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test`, `AWS_DEFAULT_REGION=us-east-1`. The
  Pulumi AWS provider (v7.x) and the AWS CLI pick these up from the environment automatically.
- **The AWS SDK for Java v2 wants `AWS_REGION`, not `AWS_DEFAULT_REGION`.** The CLI and Pulumi's
  provider are happy with `AWS_DEFAULT_REGION`; a Java app using the SDK directly
  (`DynamoDbClient.create()`, `SnsClient.create()`, etc.) fails at startup with "Unable to load
  region" unless `AWS_REGION` is *also* set. Found via catalog-api; every Floci-based sample's
  `deploy.sh` sets both now.
- **AWS-shaped outputs lie about being resolvable locally.** Things like API Gateway's
  `apiUrl` render as real `*.execute-api.*.amazonaws.com` hostnames, but Floci doesn't do
  subdomain-based routing — you need the emulator's path-based route instead (for API
  Gateway: `http://localhost:4566/restapis/<api-id>/$default/_user_request_/<route>`).
- **Floci actually runs real backing engines for stateful services, not just control-plane
  stubs** — confirmed for both OpenSearch (a genuine `opensearchproject/opensearch` container
  per domain) and RDS (a genuine Postgres container per instance). But each has its own gap:
  OpenSearch's domain `Created` status never flips to `true`, which hangs Pulumi's AWS provider
  indefinitely since it waits on exactly that (search-api hit this after 45+ minutes with a
  perfectly healthy cluster sitting there the whole time — worked around by running the same
  OpenSearch image via Pulumi's Docker provider instead, bypassing Floci for that resource
  entirely). RDS's Postgres container is real and healthy but kept off the host network
  entirely by design, unreachable from anything not on Floci's internal Docker network
  (claims-api works around this the same way — plain Postgres via Docker, no Floci).
- **Floci's container lifecycle is machine-wide, not per-sample.** A separate pair of scripts
  (`~/floci-sandbox/start.sh` / `stop.sh` on the author's machine — adjust the path in
  `deploy.sh` if you're setting this up elsewhere) manage the actual Colima + Floci container
  and its persisted emulator state, shared across every project on that machine that uses
  Floci. `deploy.sh` calls `start.sh` automatically if Floci isn't already running.

**Docker-based samples:**
- **Pulumi's Docker `Image` resource needs `skipPush(true)` for a local-only build.** Without
  a registry configured, the provider assumes you're pushing somewhere; `skipPush(true)` is
  what makes it build straight into the local Docker daemon instead (confirmed against the
  provider's own source — it runs `docker inspect` on the built tag afterward rather than
  attempting registry auth).
- **Reference `image.repoDigest()`, not `image.imageName()`, on the `Container` that runs it.**
  `imageName()` is the mutable tag (`"task-api:local"`) — rebuilding produces new content under
  the same tag, so Pulumi sees no diff on that field and leaves the old container running
  untouched. `repoDigest()` changes whenever the content does, so a rebuild correctly triggers a
  container replace. Found the hard way on claims-api: fixed a real bug, rebuilt, redeployed,
  and kept hitting the *old* code until this was fixed. `docker restart` doesn't help either —
  it reuses the already-extracted container, not the newly built image.
- **`ImageArgs.builder()` has two same-named `.build(...)` methods.** One (takes a
  `DockerBuildArgs`) sets the build config; the other (no args) finalizes the builder itself.
  Resolved by Java overloading, but it reads strangely the first time.
- **A user-defined `docker.Network` gives container-name DNS resolution; the default "bridge"
  network doesn't.** claims-api's app reaches its Postgres container at hostname `claims-db`
  only because both are attached to a Pulumi-created network — on the default network they'd
  only be reachable by IP.
- **No "wait until healthy" dependency between containers.** The Terraform-bridged Docker
  provider Pulumi uses has nothing like docker-compose's `depends_on: condition:
  service_healthy`. A dependent container (e.g. claims-api's app, which needs Postgres actually
  accepting connections, not just started) may crash on first boot and rely on `restart:
  unless-stopped` to self-heal a few seconds later. Same class of problem Kubernetes readiness
  probes exist to solve — left as-is and documented rather than engineered around, since it's a
  genuinely common real-world container-orchestration lesson.

**Java/Hibernate:**
- **`@OneToMany` defaults to `FetchType.LAZY`, and `spring.jpa.open-in-view: false` means the
  Hibernate session is gone by the time a controller serializes the response.** Accessing a
  lazy collection outside its original transaction throws `LazyInitializationException`. Fixed
  in claims-api with `fetch = FetchType.EAGER` on the specific relationship that's always small
  and always needed (not a blanket fix for every `@OneToMany`). EF Core's default is the
  opposite failure mode: unpopulated navigation properties are just silently empty, no
  exception, no lazy proxy, no session — arguably more dangerous since it doesn't crash.

**macOS:**
- **Plain `java` can resolve to Apple's stub** ("please install Java") if a Homebrew JDK isn't
  linked as the system default, even though `mvn` finds a real JDK fine through its own
  resolution. Samples that run the app directly (not containerized — search-api, catalog-api,
  events-api) check for this in `deploy.sh` and fall back to
  `/opt/homebrew/opt/openjdk@21/bin/java` or `$JAVA_HOME`.

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
`@Component`-family annotations vs. explicit DI registration, Hibernate's lazy-loading defaults
vs. EF Core's, that kind of thing. Not every line, and not where the parallel is trivial (`if`,
`for`) — just the spots where Java (or a framework like Spring/Hibernate) diverges enough from
C#/.NET/EF Core/ASP.NET Core to trip someone up.

## Prerequisites

JDK 21, Maven, Pulumi CLI, Docker (Colima, Docker Desktop, whatever — samples assume Colima on
macOS specifically for the Floci-lifecycle scripts). [Floci CLI](https://floci.io) needed for
the AWS-shaped samples (see Layout above).
