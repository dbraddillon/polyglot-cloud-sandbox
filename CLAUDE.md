# floci-java-sandbox

Sandbox for a Java-only local AWS workflow: a Lambda behind API Gateway HTTP API,
infrastructure written in Java with Pulumi, deployed to [Floci](https://floci.io)
(a local AWS emulator, similar in spirit to LocalStack) instead of real AWS.

Not a git repo yet (`git init` not run) — ask before doing that; it's a real decision
about remotes/naming, not just plumbing.

## Structure

- `app/` — the Lambda. `Handler.java` implements `RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>`,
  returns `{"message": "hello, <name>", ...}`. Built into a shaded jar (`maven-shade-plugin`)
  at `app/target/app.jar` — that's the artifact Pulumi deploys.
- `infra/` — Pulumi program in Java (`App.java`). Provisions IAM role, the Lambda function
  (pointing at `../app/target/app.jar`), and an HTTP API Gateway (`GET /hello`) wired to it.

## Deploying

```
./deploy.sh    # build the jar, start Floci if needed, pulumi up, curl the endpoint
./destroy.sh   # tear down the Pulumi stack
```

Single-command by design — don't hand-run the underlying `mvn`/`pulumi`/`curl` steps unless
debugging; `deploy.sh` is the source of truth for the env vars this all depends on.

## How this differs from a real AWS deploy

- **Fake creds, local endpoint.** Floci exposes a unified endpoint at `http://localhost:4566`.
  The Pulumi AWS provider (v7.x here) picks up `AWS_ENDPOINT_URL` / `AWS_ACCESS_KEY_ID=test` /
  `AWS_SECRET_ACCESS_KEY=test` from the environment automatically — no `Provider`/`endpoints`
  block needed in `App.java`. `deploy.sh` sets these; nothing else to configure.
- **Pulumi state is local, not Pulumi Cloud.** `PULUMI_BACKEND_URL=file://infra/.pulumi-state`
  with an empty `PULUMI_CONFIG_PASSPHRASE`, set per-command in the scripts — deliberately *not*
  `pulumi login`, which would change the global CLI default backend for every other Pulumi
  project on this machine. The `dev` stack lives entirely in `infra/.pulumi-state/` (gitignored).
- **The `apiUrl` Pulumi output is a lie, locally.** It renders as a real-looking
  `https://<api-id>.execute-api.us-east-1.amazonaws.com/hello` URL, but Floci doesn't do
  subdomain-based API Gateway routing, so that host doesn't resolve to anything useful.
  The actual local invoke pattern is path-based:
  ```
  http://localhost:4566/restapis/<api-id>/$default/_user_request_/hello
  ```
  `deploy.sh` extracts the api-id from the Pulumi output and curls the real local URL for you.

## Floci itself

- CLI installed via Homebrew (`floci`), backed by a Docker container on Colima.
- This project doesn't manage the Floci container lifecycle directly — `~/floci-sandbox/start.sh`
  and `stop.sh` do that (start Colima if needed, link the docker socket, `floci start --persist
  ~/floci-sandbox/data`). `deploy.sh` calls `start.sh` automatically if Floci isn't already running.
- `~/floci-sandbox/data/` holds Floci's persisted emulator state (S3, IAM, Lambda, API Gateway,
  RDS, etc., as JSON) — shared across whatever other Floci-based sandboxes exist on this machine,
  not specific to this repo. Reset by stopping Floci and editing/clearing those files.
- Useful commands: `floci status`, `floci doctor`, `floci aws env` (prints the env vars above),
  `aws --profile floci <service> <command>` (profile is preconfigured in `~/.aws/config`/`credentials`).

## Prerequisites

JDK 21, Maven, Pulumi CLI, Floci CLI, Docker (via Colima) — all already installed/configured
on this machine as of 2026-07-07.
