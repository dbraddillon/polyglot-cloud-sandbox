# node-api

A small Express REST API — member benefit notices, the same CRUD-plus-one-business-rule shape
as `task-api` (create, list, get, delete, and one status transition: a notice can only be sent
once), just in Node instead of Java. Reading `task-api` and this sample side by side is the
fastest way to feel what actually differs between a Spring Boot service and an Express one, and
what doesn't (both: in-memory store behind the same four-ish endpoints, one small business rule
guarding a state transition, containerized the same way).

## Endpoints

| Method | Path                  | Notes                                                      |
|--------|-----------------------|-----------------------------------------------------------|
| GET    | `/notices`            | list all                                                   |
| GET    | `/notices/:id`        | 404 if missing                                             |
| POST   | `/notices`            | `{"message": "..."}`, 400 if blank; 201 + `Location` header |
| PATCH  | `/notices/:id/send`   | `PENDING` → `SENT`; 409 if already `SENT`                  |
| DELETE | `/notices/:id`        | 204                                                         |

## Structure

```
app/
  index.js          the whole service - routes, in-memory store, the one business rule
  test/*.test.js     node:test (built in) + supertest, run via `npm test`
  Dockerfile         single-stage - npm install doesn't need a separate build toolchain the way
                     a JDK does, so no multi-stage split like task-api's
  package.json       "volta": { "node": "22.11.0" } - see the version-pinning note below
  .nvmrc             the nvm equivalent of the same pin
infra/               Pulumi program (Java): build the image, run the container - identical shape
                     to task-api's, just a different Dockerfile underneath
```

## Why this deploy shape

Same reasoning as `task-api`: this is just a container listening on a port, not an AWS-shaped
resource — no Floci involved, Pulumi's Docker provider builds the image and runs it straight
against the local Docker daemon.

## Version pinning: volta vs. nvm, and a real caveat found setting this up

Both `package.json`'s `"volta"` field and `.nvmrc` pin Node 22.11.0 for local development
outside the container (the Dockerfile pins the same version independently, for the same reason
a real project keeps the container's runtime in sync with local tooling rather than assuming
they'll drift together correctly).

`volta pin node@22.11.0` (the command that produced the `package.json` entry) worked
immediately — no project existed yet, so `npm init` came first. But actually *switching* to that
pinned version when `cd`-ing into this directory only works if Volta's shims
(`~/.volta/bin`) come before any other Node install in `PATH`. On this machine, a Homebrew-
installed Node resolves first, so plain `node --version` here still reports the system Node, not
2.11.0 - confirmed directly, not assumed. Fixing that is a one-time, machine-wide shell/PATH
change (or `brew unlink node`), not something this sample's `deploy.sh` should do on a
developer's behalf. Doesn't affect the actual deploy either way: the container always gets the
Dockerfile's pinned version regardless of what the host shell resolves `node` to.

## Running it

```
./deploy.sh    # npm install + test, then build the image and run the container
./destroy.sh   # stop and remove the container (the built image itself persists locally,
               # same as task-api's - reclaim the space with `docker image prune` if wanted)
```

Runs on `http://localhost:8086/notices`.
