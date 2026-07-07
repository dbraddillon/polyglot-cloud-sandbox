# floci-java-sandbox

General sandbox for Java microservice + IaC experiments, deployed locally against
[Floci](https://floci.io) (a local AWS/Azure/GCP emulator) instead of real cloud accounts.
Each experiment is a self-contained **sample**; nothing here is meant to run in production.

## Layout

```
samples/
  hello-api/          # first sample — Lambda + HTTP API Gateway
    app/               Java service code (its own Maven module)
    infra/             Pulumi program in Java provisioning that service
    deploy.sh          build + pulumi up + curl the result
    destroy.sh         pulumi destroy
    README.md          sample-specific notes/gotchas
  <next-sample>/       same shape
```

Every sample is independent: its own Maven module(s), its own Pulumi project/stack, its
own local Pulumi state (`infra/.pulumi-state/`, gitignored), its own `deploy.sh`/`destroy.sh`.
No shared root build — run each sample from inside its own directory. This is deliberate:
the point of this repo is trying different Java-on-AWS patterns side by side without them
tangling into one growing app.

## Adding a new sample

Copy `samples/hello-api/` as the template, then:
1. Rename the sample directory (`samples/<new-name>/`).
2. Repackage the Java code: `dev.dillon.sandbox.<newname>` for the app,
   `dev.dillon.sandbox.<newname>.infra` for the Pulumi program — keeps every sample's
   package space isolated so nothing collides if code ever gets copy-pasted between them.
3. Update both `pom.xml` groupIds and the infra `pom.xml`'s `<mainClass>` to match.
4. Update `infra/Pulumi.yaml` `name:` to the new sample name (each sample = its own Pulumi
   project) and fix the `.handler(...)` string in `App.java` to the new package.
5. `deploy.sh`/`destroy.sh` need no path changes if copied as-is — they're written relative
   to their own directory (`cd "$(dirname "$0")"`), not the repo root.

## Common gotchas across every sample (from building `hello-api`)

- **Floci gives fake creds + one unified endpoint.** `AWS_ENDPOINT_URL=http://localhost:4566`,
  `AWS_ACCESS_KEY_ID=test`, `AWS_SECRET_ACCESS_KEY=test`, `AWS_DEFAULT_REGION=us-east-1`. The
  Pulumi AWS provider (v7.x) picks these up from the environment automatically — no
  `Provider`/`endpoints` block needed in the Pulumi program.
- **Pulumi state is local per-sample, not Pulumi Cloud.** `PULUMI_BACKEND_URL=file://.../infra/.pulumi-state`
  with an empty `PULUMI_CONFIG_PASSPHRASE`, set per-command in `deploy.sh`/`destroy.sh` —
  deliberately *not* `pulumi login`, which would flip the global CLI default backend for every
  other Pulumi project on this machine (otherwise logged into Pulumi Cloud as `braddillon`).
  The state dir doesn't exist until first deploy; `deploy.sh` `mkdir -p`s it.
- **AWS-shaped outputs lie about being resolvable locally.** Things like API Gateway's
  `apiUrl` render as real `*.execute-api.*.amazonaws.com` hostnames, but Floci doesn't do
  subdomain-based routing — you need the emulator's path-based route instead (for API
  Gateway: `http://localhost:4566/restapis/<api-id>/$default/_user_request_/<route>`; other
  services likely have their own local-invoke pattern worth checking similarly).
- **Floci's container lifecycle is machine-wide, not per-sample.** `~/floci-sandbox/start.sh` /
  `stop.sh` manage the actual Colima + Floci container and its persisted emulator state
  (`~/floci-sandbox/data/`) — shared across every sample/project on this machine that uses
  Floci. `deploy.sh` calls `start.sh` automatically if Floci isn't already running.

## Java comment policy

See the "Java Learning Mode" section in the global `~/.claude/CLAUDE.md` — light inline
comments calling out C#/.NET parallels apply to all Java code in this repo while that's active.

## Prerequisites

JDK 21, Maven, Pulumi CLI, Floci CLI, Docker (via Colima) — all already installed/configured
on this machine as of 2026-07-07.
