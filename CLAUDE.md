# polyglot-cloud-sandbox

General sandbox for Java microservice + IaC experiments, deployed locally instead of to real
cloud accounts. Each experiment is a self-contained **sample**; nothing here is meant to run in
production. Three deploy shapes exist side by side, chosen per-sample based on what's realistic:

- **AWS-shaped samples** deploy to [Floci](https://floci.io), a local AWS/Azure/GCP emulator —
  `hello-api` (Lambda + API Gateway), `catalog-api` (DynamoDB), `events-api` (SNS + SQS),
  `claims-intake-api` (Kinesis), `python-api` (Lambda + API Gateway again, Python runtime),
  `attachments-api` (S3).
- **Plain services** (a Spring Boot/Express app, anything that's just "a container that listens
  on a port," or a piece of infra Floci can't reliably emulate) deploy via Pulumi's Docker
  provider straight to the local Docker daemon — `task-api`, `node-api`, `claims-api` (Postgres),
  `search-api` (OpenSearch — Floci can run this too, but has a bug that makes Pulumi's AWS
  provider hang forever, see the gotchas below).
- **No infra at all** — for a sample whose "backing service" is itself embedded/in-process, with
  nothing to provision or containerize. `clojure-datomic-api` (Datomic `dev-local`, `:mem`
  storage) is the only one so far; `deploy.sh` just runs the app directly.

Most samples are Java; a few (`python-api`, `node-api`, `clojure-datomic-api`) deliberately
aren't, to prove the same Java/Pulumi infra pattern works regardless of the workload's language -
see "Java comment policy" below for how the inline-comment convention adapts per language.

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
  task-api/     Spring Boot REST API (member care tasks), local Docker (no Floci), Datadog metrics
  search-api/   Spring Boot + OpenSearch (health articles), local Docker (Floci's emulation hangs)
  catalog-api/  Spring Boot + DynamoDB (insurance plans), deployed to Floci
  claims-api/   Spring Boot + Spring Data JPA + Postgres (insurance claims), local Docker
  events-api/   Spring Boot + SNS/SQS async messaging (claim/appointment events), deployed to Floci
  claims-intake-api/  Large CSV batch ingest: streaming vs. Kinesis decision tree, deployed to Floci
  python-api/   Python 3.12 Lambda + HTTP API Gateway (same Java/Pulumi infra as hello-api), Floci
  node-api/     Express REST API (member benefit notices), local Docker (no Floci)
  clojure-datomic-api/  Clojure + Ring/Compojure over Datomic dev-local - no infra/ dir at all
  attachments-api/  Claim attachments over S3 (upload/list/download/delete), deployed to Floci
  <next>/       same shape - pick whichever deploy story actually fits what you're building

floci/          start.sh/stop.sh - Colima + Floci lifecycle, shared by every Floci-based sample
tools/          Local dev-tool setups that aren't a "sample" of a deploy pattern - see below
  jenkins/      Local Jenkins running a real pipeline against task-api's JUnit suite
  jfrog-artifactory/  Local Artifactory OSS + Postgres, publishes+verifies task-api's real jar
```

Every sample has roughly the same internal shape - `app/` (the service), `infra/` (a Pulumi
program in Java, unless the sample needs none at all - see `clojure-datomic-api`),
`deploy.sh`/`destroy.sh`, `README.md`. The **workload** language varies (`python-api`'s and
`node-api`'s and `clojure-datomic-api`'s `app/` isn't Java, and has no `pom.xml`), but the
**infra program** provisioning it is always Java/Pulumi, same as every other sample:
```
app/            Service code (its own Maven module, unless the workload isn't Java - see above)
                - controller/service/repository/domain layers, a Dockerfile if containerized
infra/          Pulumi program in Java provisioning whatever the sample needs (skipped entirely
                if there's nothing to provision - an embedded/in-process backing service)
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

Steps 2-3 assume the sample's `app/` is Java, which is the common case. For a non-Java workload
(see python-api, node-api, clojure-datomic-api), only the **infra program** needs the
`dev.sandbox.lab.<newname>.infra` repackaging and `pom.xml`/`mainClass` update — `app/` follows
that language's own conventions instead (a `deps.edn`, a `package.json`, whatever's idiomatic),
with no `pom.xml` of its own at all.

## Local tool setups (`tools/`)

Not every item worth demonstrating is a deploy-pattern "sample." Some are dev tooling that
exercises an *existing* sample rather than being one itself — `tools/jenkins/` and
`tools/jfrog-artifactory/` are both this shape, with no app/infra of their own. These get their
own `deploy.sh`/`destroy.sh` (same single-command bar as every sample) but live under `tools/`,
not `samples/`, since there's no deploy-shape decision being made — just a tool standing itself
up.

Datadog metrics went the *other* way on purpose: it needed genuine code changes inside task-api
itself (a Micrometer dependency, actual instrumentation, a co-located agent container in
task-api's own `infra/App.java`), not something observing the sample from outside - so it lives
in `samples/task-api/` directly rather than a `tools/datadog/`. Use this as the rule of thumb for
where a new tool-list item belongs: does it need to change the sample's own code/infra, or just
watch/exercise it from the sidelines?

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
- **AWS Batch is a pure control-plane stub on Floci — no sample built against it for this
  reason.** Unlike OpenSearch/RDS (which run a real backing engine with a narrower gap) or
  Lambda (which genuinely executes the given runtime), Batch fakes the whole job lifecycle:
  `create-compute-environment`/`create-job-queue`/`register-job-definition` all succeed and
  report healthy status immediately, and `submit-job` reports `SUCCEEDED` in ~12ms — but
  confirmed directly that no container was ever created on the Docker daemon (`docker ps -a`
  showed nothing) and no `/aws/batch/job` CloudWatch log group exists at all. The job's declared
  command never actually runs; the API just reports success unconditionally. On top of that,
  `update-job-queue`/`delete-job-queue`/`delete-compute-environment` are separately broken,
  returning an S3-flavored `InvalidArgument: POST requires either ?uploads, ?uploadId...` error
  regardless of correct CLI usage — confirmed this isn't a usage mistake by checking the CLI's
  own `help` output first. A real batch-processing sample would need actual container execution
  to be worth building at all (the whole point of AWS Batch is running your job's container),
  so this one isn't a Docker-provider-workaround candidate the way OpenSearch/RDS were — there's
  nothing genuine underneath to fall back to on Floci's side. Skipped rather than built as a
  misleading "works" sample.
- **Floci's container lifecycle is machine-wide, not per-sample, but the scripts are bundled in
  this repo.** `floci/start.sh` / `stop.sh` (repo root) manage the actual Colima + Floci
  container; every Floci-based sample's `deploy.sh` calls `../../floci/start.sh` (relative to
  the sample directory) automatically if Floci isn't already running. Persisted emulator state
  still lives at `~/floci-sandbox/data`, a machine-level location outside any one repo's
  checkout, since that state is genuinely shared across every project on the machine that uses
  Floci — only the *scripts* needed bundling, not the state directory itself. (These used to
  live only at `~/floci-sandbox/start.sh`, hand-authored per-machine with no copy in the repo at
  all — a real gap for anyone cloning this repo fresh, since nothing told a new machine what to
  put there. Bundled and fixed 2026-07-16.)
- **`floci status`'s exit code is always 0, even when the container is stopped and unreachable**
  — confirmed directly, not assumed. Every sample's `deploy.sh` used to check
  `if ! floci status >/dev/null 2>&1`, which therefore *never* detected a stopped Floci and never
  triggered the auto-start — silently skipped, with Pulumi then failing downstream with a
  confusing `unable to validate AWS credentials` error that looked like a credentials problem,
  not a "Floci was never started" one. Fixed by checking the `reachable` field from
  `floci status -o json` instead, the only field that actually reflects real state. This had
  been broken since the check was first written; it only went unnoticed because Floci happened
  to already be running across most of this repo's development.
- **Kinesis works against Floci, but `PutRecords` has a real per-record cost, not per-call.**
  Stream creation itself is fine (~20-24s, same as any other Pulumi resource here) and both
  `PutRecords`/`GetRecords` function correctly — no OpenSearch/RDS-style hang. But a single
  `PutRecords` call with 500 tiny records measured at **~31 seconds** against Floci (20 records:
  ~1s, 200 records: ~7s) — roughly 35-60ms spent per record *inside* the batch, so batching
  fewer/larger calls doesn't buy back much the way it would against real AWS (where the cost is
  per-call HTTP overhead, not per-record). Found building claims-intake-api: a naive one-
  `PutRecord`-per-row producer made a 6000-row CSV take minutes; switching to batched
  `PutRecords` barely helped since the bottleneck lives inside Floci's per-record processing.
  Worked around by sizing that sample's demo data and default thresholds down (a few hundred
  rows, not thousands) rather than fighting the emulator — there's no code-side fix for this one.
- Harmless, not fixed: a perpetual no-op Pulumi diff on `aws:lambda:Function` (`-environment`)
  in hello-api — every `pulumi up` after the first reports `~1 to update [diff: -environment]`
  even though nothing in the program sets an `Environment` block. Confirmed by running it twice
  in a row with zero code changes between. Floci's Lambda emulator seems to always report an
  `Environment` block back even when none was set. Also confirmed in python-api — it's a general
  Floci Lambda-emulator quirk, not specific to a Java-runtime function (see "Polyglot samples"
  below for more on python-api specifically).

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
- **Colima's UDP port-forwarding from host to container is unreliable; TCP forwarding isn't.**
  Confirmed directly building task-api's Datadog metrics wiring: identical packets sent from the
  host to a container's UDP port published via `docker run -p` never arrived, while the same
  packets sent container-to-container over a shared Docker network worked immediately. Anything
  UDP-based (DogStatsD, other statsd-family protocols) needs to be reached via container-name DNS
  on a shared network, not a host-published port, on this setup.

**Java/Hibernate:**
- **`@OneToMany` defaults to `FetchType.LAZY`, and `spring.jpa.open-in-view: false` means the
  Hibernate session is gone by the time a controller serializes the response.** Accessing a
  lazy collection outside its original transaction throws `LazyInitializationException`. Fixed
  in claims-api with `fetch = FetchType.EAGER` on the specific relationship that's always small
  and always needed (not a blanket fix for every `@OneToMany`). EF Core's default is the
  opposite failure mode: unpopulated navigation properties are just silently empty, no
  exception, no lazy proxy, no session — arguably more dangerous since it doesn't crash.

**Spring Boot:**
- **`management.metrics.export.statsd.*` silently no-ops in Spring Boot 3.x** — deprecated at
  *error* level in favor of the top-level `management.statsd.metrics.export.*` namespace, but
  nothing logs a warning or fails; metrics just get recorded internally (visible at
  `/actuator/metrics`) and never leave the process. Found on task-api's Datadog wiring by
  checking Spring's own `spring-configuration-metadata.json` after "it deployed clean but zero
  metrics ever arrived" pointed at config rather than network. Worth checking for in any
  Micrometer-registry config that looks copied from an older tutorial.

**Polyglot samples (python-api, node-api, clojure-datomic-api):**
- **Floci genuinely runs non-Java Lambda runtimes, not just Java.** Confirmed with python-api
  (Python 3.12): the same Java/Pulumi infra shape as hello-api, pointed at a Python handler
  instead, deploys cleanly and returns a real response computed by an actual Python interpreter
  inside Floci. `FileArchive` zips a plain source directory on the fly for a non-compiled
  runtime like Python; no build step needed the way the Java samples need `mvn package` first.
- **`volta pin` writes the config correctly, but only takes effect if Volta's shims come before
  other Node installs in `PATH`.** On a machine with a Homebrew-installed Node already ahead of
  `~/.volta/bin`, `cd`-ing into a volta-pinned project still resolves the system Node, not the
  pinned version — confirmed directly with `node --version` in node-api's directory, not
  assumed. A one-time, machine-wide `PATH`/`brew unlink node` fix, not something a sample's
  `deploy.sh` should do on a developer's behalf. Doesn't affect the actual deploy either way -
  the Dockerfile pins its own Node version independently of what the host shell resolves.
- **Homebrew's `clojure` formula can fail with "Your Command Line Tools are too outdated"** even
  when nothing else on the machine is affected — its `rlwrap`/`libptytty` native dependencies
  need compilation tooling the core `clojure`/`clj` CLI itself doesn't. Worked around by
  installing directly from the official tarball
  (`https://download.clojure.org/install/clojure-tools-<version>.tar.gz`) into `~/.local/` by
  hand instead, sidestepping Homebrew (and the stale-CLT problem) entirely.
- **Datomic `dev-local`'s database persists for the life of the client object, even with
  `:storage-dir :mem`.** `create-database` is a no-op if the named database already exists, and
  `connect` just reconnects to whatever's already there - calling both again does *not* give you
  a fresh, empty database. A `use-fixtures :each` that only called this "init" step assumed
  per-test isolation it didn't actually have, and every test in clojure-datomic-api's suite
  silently shared one ever-growing database until an assertion caught it (expected count 2, got
  5). Fixed with an explicit delete-then-recreate (`reset-db!`) instead.

**macOS:**
- **Plain `java` can resolve to Apple's stub** ("please install Java") if a Homebrew JDK isn't
  linked as the system default, even though `mvn` finds a real JDK fine through its own
  resolution. Samples that run the app directly (not containerized — search-api, catalog-api,
  events-api) check for this in `deploy.sh` and fall back to
  `/opt/homebrew/opt/openjdk@21/bin/java` or `$JAVA_HOME`.
- **Homebrew's plain `ruby` formula (4.x) can't build native gem extensions under current Apple
  clang (16.x).** Ruby 3.4+ added a C23 `stdckdint.h` compatibility shim to its own headers
  (`ruby/internal/stdckdint.h`) that checks `__has_include(<stdckdint.h>)` — under this clang
  version that check self-matches the shim's own file via quote-style fallback, then the literal
  `#include <stdckdint.h>` fails ("file not found with <angled> include; use "quotes" instead").
  Breaks *any* native-extension gem (`bigdecimal`, pulled in transitively by `cucumber`, is just
  the first one task-api's `service-tests/` hit), not something specific to one gem. `ruby@3.3`
  (`brew install ruby@3.3`, keg-only, doesn't fight the `ruby` formula) predates the shim and
  isn't affected — `service-tests/run.sh` looks for it specifically.

**Local tool setups (`tools/`):**
- **Jenkins rejects state-changing REST calls (like triggering a build) without a CSRF crumb by
  default**, and forwarding a fetched crumb via `/crumbIssuer/api/json` or `/crumbIssuer/api/xml`
  still 403'd here. `tools/jenkins/` disables CSRF checking outright
  (`-Dhudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION=true`) rather
  than keep chasing crumb/session semantics — reasonable for a Jenkins that's local-only and never
  internet-exposed, not a setting to carry into anything real.
- **A named Docker volume mounted over a directory that already has content in the image gets
  seeded from that content on first use.** This is what makes `tools/jenkins/`'s baked-in
  `casc.yaml` actually land in `/var/jenkins_home` on first boot — and why its `destroy.sh` removes
  the volume, not just the container, so every `deploy.sh` run is a genuinely clean, reproducible
  boot instead of silently reusing the previous run's `JENKINS_HOME` state.
- **JFrog Artifactory OSS (current release, 7.111.x) refuses to start on its own embedded Derby
  database at all anymore** — a hard `IllegalStateException: Cannot start the application with a
  database other than PostgreSQL`, not a soft deprecation warning. `tools/jfrog-artifactory/`
  runs a real `postgres:16` alongside it on a shared network, same container-name-DNS pattern as
  `claims-api`. It also refuses to boot without a pre-generated master key
  (`Failed resolving MasterKey key`) — generated fresh each `deploy.sh` run via `openssl rand`.
- **Artifactory OSS's repository-management REST API is Pro-gated, not just missing features.**
  Every `PUT`/`POST /api/repositories/<key>` call — create or update, and even the legacy
  full-config-import endpoint — 400s with `"This REST API is available only in Artifactory
  Pro"`, confirmed directly (reading an existing repo's config works fine, only create/update is
  blocked). `tools/jfrog-artifactory/` publishes to the repo OSS ships with by default
  (`example-repo-local`) instead of fighting that wall — it's typed "generic" rather than
  "maven" in Artifactory's own UI sense, but a real Maven-layout deploy/retrieve (what `mvn
  deploy` actually does on the wire) works identically either way.

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

The non-Java samples (`python-api`, `node-api`, `clojure-datomic-api`) apply the same policy at
one remove: light comments calling out where that language's idiom diverges from Java's, since
Java is this repo's other constant and most readers' anchor point (e.g. Python's plain-function
Lambda handler vs. Java's `RequestHandler<In, Out>` interface, Node's lack of a real enum type,
Clojure having no class/object concept at all). Same restraint applies - only genuine divergence
points, not a comment on every line.

## Real AWS as an opt-in path (not yet built)

Floci is the default and stays the default — every AWS-shaped sample here runs against it, no
real account needed. A real account only becomes worth reaching for when a future sample needs
an AWS service that genuinely has no local emulation path at all (Bedrock is the concrete
example; unlike DynamoDB/S3/SQS/Kinesis, there's no meaningful way to fake an actual foundation
model locally). If/when such a sample gets built, the convention to follow:

- **Opt-in, never default.** A `deploy.sh` checks an environment variable (e.g.
  `SANDBOX_TARGET=aws`) before doing anything AWS-real; unset, it behaves exactly as every
  existing sample does today (Floci, fake creds, zero real cost). Never flip this based on
  what's merely *available* in the environment — an explicit opt-in only.
- **Profile-based auth, nothing hardcoded.** Reference `AWS_PROFILE` (a standard AWS CLI/SDK
  env var, not a sandbox-specific invention) so this stays genuinely generic — works for anyone
  with their own AWS account and their own profile name, no assumption about what that profile
  is called or which account it points to.
- **No secrets, account IDs, or ARNs ever committed** — not in code, not in a README example,
  not in a commit message. If a real deploy run's output needs to be pasted anywhere for
  documentation purposes, redact identifiers first.
- **Don't assume `default` is a sandbox-safe profile.** A real AWS account frequently has a
  `default` profile already pointed at production/personal resources; a sandbox path should
  never silently target whatever `aws configure` last set up. Encourage (in whatever README
  covers this) pointing at a profile the person deploying has deliberately set aside for this,
  ideally a genuinely separate account (AWS Organizations makes a new member account free and
  fast) rather than just a differently-scoped IAM user in the same account, though the latter is
  an acceptable fallback if full account separation isn't set up yet.
- **Cost-check every candidate service before adding real-AWS support for it.** Near-zero/
  free-tier-friendly for a quick create-verify-destroy cycle: S3, Lambda + API Gateway, DynamoDB,
  SNS/SQS. Genuinely risky if left running even briefly: Kinesis (per-shard-hour billing
  regardless of usage), OpenSearch Service and RDS (already Docker-only in this repo for exactly
  this reason - see the Floci OpenSearch/RDS findings above). Don't add a real-AWS path for a
  service in the second bucket without calling out the cost risk explicitly.

## Prerequisites

JDK 21, Maven, Pulumi CLI, Docker, **Python 3**. [Floci CLI](https://floci.io) needed for the
AWS-shaped samples (see Layout above). Node ([volta](https://volta.sh) or nvm) and the
[Clojure CLI](https://clojure.org/guides/install_clojure) are needed only for their one
respective sample each (`node-api`, `clojure-datomic-api`).

**Python 3 is a de facto repo-wide dependency, not just `python-api`'s** — despite older docs
here implying otherwise. Six other samples' `deploy.sh` (`attachments-api`, `claims-api`,
`claims-intake-api`, `events-api`, `clojure-datomic-api`, `node-api`) use `python3 -c '...json...'`
as a lightweight `jq` stand-in to pull an id/key back out of a curl JSON response for the demo
output — confirmed directly via grep, not assumed. Ironically, `python-api`'s own `deploy.sh`
doesn't invoke `python3` on the host at all (Floci runs the actual Python interpreter inside its
own container; the host side just zips a source directory via Pulumi's `FileArchive`). Found
during a final holistic review pass, 2026-07-16 — worth double-checking this claim again if any
future sample's `deploy.sh` drops or adds a `python3` call.

**macOS is the only platform this repo has actually been built and run on.** Every `deploy.sh`
uses a bash shebang and Unix-y assumptions throughout (no PowerShell/cmd equivalents exist) —
**Windows needs WSL2** to run any of this at all; there's no native-Windows path, and Colima
itself doesn't support Windows regardless (it's a macOS/Linux tool). Everything here should work
unmodified inside WSL2 or on native Linux (same bash, same Docker daemon, same JDK/Maven/Pulumi
CLI story) — just not verified hands-on in this repo, so treat that combination as "should work,
not confirmed" rather than "tested."

**Docker runtime: this repo's `floci/start.sh` bundles Colima specifically** (the assumed default
on macOS), but nothing in the samples themselves is Colima-specific — every `deploy.sh` and
Pulumi program just talks to whatever Docker daemon is on the standard socket. **Rancher Desktop
and Podman Desktop are plausible alternatives** (both can expose a Docker-compatible socket) but
**neither has been verified against this repo** — no machine with either installed was available
to confirm hands-on, so don't present that flexibility as tested. Swapping in one of them would
mean writing an equivalent `floci/start.sh` for that tool's own start/status commands (Colima's
`colima start`/`colima status` and the macOS-specific `docker.sock` symlink step are the only
parts that would need replacing) — everything downstream of "a Docker daemon exists at the
standard socket" should be unaffected.

**Real AWS vs. Floci: Floci is the tested, recommended default for everyone**, not just a
convenience for this repo's own author — every sample here runs against it with zero cost and
nothing to clean up beyond `./destroy.sh`. A real AWS account is a legitimate, flexible
alternative for someone who'd rather point at their own account (e.g. to go further than Floci
can, like Bedrock — see "Real AWS as an opt-in path" above), but that path is deliberately
opt-in and not yet built for any sample; don't imply it's a flip-a-switch option today.
