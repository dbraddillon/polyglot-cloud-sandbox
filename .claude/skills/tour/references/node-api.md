# node-api

## ELI5

A small Express REST API — member benefit notices, the exact same CRUD-plus-one-business-rule
shape as `task-api` (create, list, get, delete, and one status transition: a notice can only be
sent once), just in Node instead of Java. Best toured right after `task-api` — reading them
side by side is the fastest way to feel what actually differs between a Spring Boot service and
an Express one, and what doesn't.

## Deploy shape, and why

Plain Docker, same reasoning as `task-api`: this is just a container listening on a port, not an
AWS-shaped resource. Pulumi's Docker provider builds the image and runs it straight against the
local Docker daemon, no Floci involved.

## Reading order

1. `app/index.js` — the whole service: routes, in-memory store, the one business rule.
2. `app/test/notices.test.js` — `node:test` (built in) + supertest.
3. `app/package.json` — note the `"volta"` field, and the version-pinning caveat below.
4. `app/Dockerfile` — single-stage, unlike `task-api`'s multi-stage Maven build.
5. `infra/src/main/java/.../App.java` — identical shape to `task-api`'s Docker-provider infra.

## Don't miss these

- `index.js` — no real enum type for the one status field (`PENDING`/`SENT`) — a plain frozen
  object of string constants (`Object.freeze({...})`) is the idiomatic amount of type safety to
  reach for here, since there's no compiler to hold any call site accountable the way Java's
  `TaskStatus` enum is in the sibling `task-api` sample.
- `index.js` — `require.main === module` gates the `app.listen(...)` call so importing this file
  from the test suite doesn't also start a real server — Node's version of Python's `if
  __name__ == "__main__":`, or C#'s `Main()` only running for the actual entry assembly.
- **A real, empirically-found caveat, not a code bug**: `volta pin node@22.11.0` correctly wrote
  `package.json`'s `"volta"` field, but actually switching to that version when `cd`-ing into
  this directory only works if Volta's shims (`~/.volta/bin`) come before other Node installs
  in `PATH` — confirmed directly with `node --version` here still reporting the system Node.
  Doesn't affect the deploy either way: the Dockerfile pins its own Node version independently.
- `Dockerfile` — pinned to the exact same version as `package.json`'s `"volta"` field and
  `.nvmrc`, on purpose: three separate places asserting the same fact (local dev tooling, the
  container), not three independent choices that could silently drift apart.

## Running it

```
./deploy.sh    # npm install + test, then build the image and run the container
./destroy.sh   # stop and remove the container (the built image persists locally, same as
               # task-api's - reclaim the space with `docker image prune` if wanted)
```

Runs on `http://localhost:8086/notices`.
