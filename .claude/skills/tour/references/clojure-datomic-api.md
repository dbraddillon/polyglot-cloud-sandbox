# clojure-datomic-api

## ELI5

A small care-provider directory — Clojure + Ring/Compojure over Datomic's free, fully local
`dev-local` peer library. Previously deferred in this repo as "too small and unusual a corner to
build a dedicated sample for" — built once it was explicitly worth touching. The furthest sample
from Java in the whole repo: no classes, no interfaces, a genuinely different query language
(Datalog, not SQL/JPQL), and — a first — no infrastructure to provision at all.

## Deploy shape, and why (the third shape in this repo)

Neither Floci nor Docker. Datomic's `dev-local` is an embedded, in-process database — there's no
server to provision, nothing to containerize, nothing for Pulumi to point at. `deploy.sh` just
runs the service directly, the same way `search-api`/`catalog-api`/`events-api` run their own
app processes directly, just with nothing backing it but an in-memory database. Uses `:mem`
storage specifically (not `dev-local`'s other, persisted-to-disk mode) — the whole database
vanishes when the JVM process exits, matching this repo's disposable-Pulumi-state philosophy
more directly than a persisted option would.

## Reading order

1. `app/deps.edn` — the dependency manifest (Clojure's Maven-equivalent, no `pom.xml`).
2. `app/src/.../core.clj` — the whole service: schema, Datomic queries, Ring/Compojure routes.
3. `app/test/.../core_test.clj` — direct function tests, plus a full-middleware-stack test.
4. `app/test/.../runner.clj` — a tiny hand-rolled test runner (see below for why).
5. This sample's own `README.md` for the full story on both real findings below.

## Don't miss these

- No `infra/` directory at all — the one thing to point out before anything else. Grep the repo
  for `infra/` and this is the only sample missing one.
- `core.clj`, top of file — no classes, no objects, at all. A "Provider" is never reified as a
  type anywhere in this sample, just a plain map (`{:id .. :name .. :specialty ..}`) passed
  around and returned as-is. There's no compiler holding any caller accountable to that shape —
  the closest thing to a compile-time contract would be a spec/schema library layered on top,
  deliberately skipped here to keep the sample small.
- The Datalog queries (`find-by-external-id`, `list-all`, `search-by-specialty`) — a genuinely
  different query shape from SQL/JPQL: `:find`/`:where` clauses over entity/attribute/value
  facts, not tables and joins. `pull` is doing roughly what a JPA entity's field access or an EF
  Core projection does — picking which attributes come back.
- **The best "found a real bug by actually running the tests" story outside claims-intake-api**:
  this sample's README's "A real gotcha found building this" section. A `use-fixtures :each`
  that called `init-db!` before every test looked like it gave each one a fresh, empty database.
  It didn't — `create-database` is a no-op if the database already exists, and `connect` just
  reconnects to what's there, even with `:mem` storage (the client is a `defonce`, so the
  database persists for the whole JVM process's lifetime). Every test silently shared one
  ever-growing database until a count assertion caught it (expected 2, got 5). Fixed with an
  explicit `reset-db!` (delete, then recreate) — and the fix was verified by reverting it and
  confirming the test failed the old way again before restoring it, same rigor as the regression
  fixes in claims-intake-api.
- `runner.clj` — `clojure.test/run-tests` returns a summary map, not a boolean or an exception on
  failure, so something has to translate that into a process exit code — the same job JUnit's
  Maven Surefire plugin does automatically for every Java sample's tests.

## Getting the Clojure CLI installed

Homebrew's `clojure` formula can fail here with "Your Command Line Tools are too outdated" — a
stale Xcode CLT issue unrelated to Clojure itself (its `rlwrap`/`libptytty` native dependencies
need compilation tooling the core CLI doesn't). Worked around by installing directly from the
official tarball into `~/.local/` by hand, sidestepping Homebrew entirely — full command in this
sample's own README. `deploy.sh` checks for `clojure` on `PATH` first and falls back to
`~/.local/bin/clojure`.

## Running it

```
./deploy.sh    # run the tests, start the service, create/get/list/search
./destroy.sh   # stop the process - no other state to clean up (see :mem storage, above)
```

Runs on `http://localhost:8087`.
