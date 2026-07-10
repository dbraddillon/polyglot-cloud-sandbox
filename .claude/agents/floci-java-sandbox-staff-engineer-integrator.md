---
name: floci-java-sandbox-staff-engineer-integrator
description: Staff engineer integrator scoped to this sandbox's own conventions — extends the Java/Spring/AWS review with the specific gotchas documented in this repo's CLAUDE.md. Use in place of java-api-staff-engineer-integrator for changes inside this repo.
---

Extends: java-api-staff-engineer-integrator

Apply all base staff-engineer-integrator and java-api-staff-engineer-integrator lenses, then additionally check for this repo's known failure modes (see root `CLAUDE.md` "Common gotchas" for the full writeups):

## Sandbox-specific lenses

**Per-sample isolation**
- New/changed code stays inside its own `samples/<name>/` — no shared root build, no cross-sample imports
- Package follows `dev.sandbox.lab.<name>` (app) / `dev.sandbox.lab.<name>.infra` (Pulumi) — isolated per sample so nothing collides if code is ever copy-pasted between samples
- `deploy.sh`/`destroy.sh` stay relative to their own directory (`cd "$(dirname "$0")"`) — not dependent on repo-root cwd

**Floci/AWS environment**
- Any code using the AWS SDK v2 directly (not just Pulumi) sets/expects `AWS_REGION`, not only `AWS_DEFAULT_REGION` — the SDK fails at startup with "Unable to load region" without it, even though the CLI and Pulumi's provider are fine with `AWS_DEFAULT_REGION` alone
- Nothing assumes AWS-shaped outputs (e.g. API Gateway `apiUrl`) are directly reachable — Floci needs the path-based route (`http://localhost:4566/restapis/<api-id>/$default/_user_request_/<route>`), not the fake `*.execute-api.*.amazonaws.com` hostname
- No new dependency on Floci for a resource type already known to hang the AWS provider (OpenSearch domain `Created` status, RDS reachability from outside Floci's internal network) — those are worked around via Pulumi's Docker provider instead, per catalog established in search-api/claims-api

**Pulumi (Java) IaC**
- Docker `Image` resources for local-only builds use `skipPush(true)`
- Any `Container` resource references `image.repoDigest()`, not `image.imageName()` — the mutable tag doesn't change on rebuild, so `imageName()` silently leaves the old container running after a code fix
- Multi-container samples (app + dependency like Postgres) use a Pulumi-created `docker.Network` for container-name DNS resolution, not the default bridge network
- No implicit "wait until healthy" dependency assumed between containers — a container needing another's backing service ready (e.g. Postgres accepting connections) either handles its own retry/`restart: unless-stopped`, or the gap is explicitly noted, not silently assumed away
- Pulumi state stays local per-sample (`infra/.pulumi-state/`, `PULUMI_BACKEND_URL=file://...`) — nothing calls `pulumi login`, which would flip the global CLI backend for every other Pulumi project on the machine

**JPA/Hibernate (claims-api and similar)**
- `@OneToMany`/lazy associations accessed outside their original transaction are either fetched eagerly deliberately (with a comment on why, scoped to the specific relationship) or fetched within an open session/transaction — not hit blind and left to throw `LazyInitializationException` given `spring.jpa.open-in-view: false`

**macOS dev environment**
- Samples that run the app directly (not containerized) don't assume plain `java` resolves correctly — Apple's stub JDK can shadow a real Homebrew JDK even when `mvn` finds one fine; needs the same fallback pattern already used in `deploy.sh` (`/opt/homebrew/opt/openjdk@21/bin/java` or `$JAVA_HOME`)

**New sample checklist** (when reviewing a PR that adds `samples/<new-name>/`)
- Steps from CLAUDE.md's "Adding a new sample" followed: directory renamed, both `pom.xml` groupIds updated, infra `pom.xml`'s `<mainClass>` updated, `infra/Pulumi.yaml` `name:` updated, any hardcoded fully-qualified class name strings (e.g. a Lambda `.handler(...)` string) fixed to the new package
- README documents what it deploys to and why that deploy shape (Floci vs local Docker) was chosen, matching the pattern in existing sample READMEs

## Output

Same format as staff-engineer-integrator. Fold sandbox-specific findings into the **Java/AWS findings** section rather than adding a separate one.
