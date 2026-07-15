# clojure-datomic-api

A small care-provider directory — Clojure + Ring/Compojure over Datomic's free, fully local
`dev-local` peer library. Previously deferred in this repo as "a small, unusual corner, not worth
building a dedicated sample for" — built now because it's specifically on the list of tools worth
touching for an upcoming role. The point isn't a deep Clojure/Datomic tutorial; it's a real,
working, tested example to read and run, the same bar as every other sample here.

## Endpoints

| Method | Path                       | Notes                                                |
|--------|----------------------------|-------------------------------------------------------|
| GET    | `/providers`               | list all                                               |
| GET    | `/providers/:id`           | 404 if missing                                         |
| GET    | `/providers/search?specialty=` | a real Datalog query, not a filter over the full list |
| POST   | `/providers`                | `{"name": "...", "specialty": "..."}`, 400 if name blank |

## Structure

```
app/
  deps.edn           dependencies (Clojure's Maven-equivalent manifest - no pom.xml here)
  src/.../core.clj    the whole service: schema, Datomic queries, Ring/Compojure routes
  test/.../core_test.clj   clojure.test - direct function tests + full-middleware-stack tests
  test/.../runner.clj      a tiny hand-rolled test runner (see "A gotcha" below)
```

No `infra/` directory at all — the first sample in this repo with neither Floci nor Docker
involved. Datomic's `dev-local` is an embedded, in-process database: there's no server to
provision, nothing to containerize, nothing for Pulumi to point at. `deploy.sh` just runs the
service directly, the same way `search-api`/`catalog-api`/`events-api` do for their own app
processes (just with nothing backing it but an in-memory database instead of a real one).

## Why `:mem` storage

`dev-local` supports a real on-disk mode (persists between runs under `~/.datomic`) and a fully
in-memory `:mem` mode. This sample uses `:mem` deliberately — it matches the rest of this repo's
disposable-state philosophy (the same reason Pulumi state is local and throwaway) more directly
than a persisted option would: the whole database vanishes the moment the JVM process exits, no
cleanup step needed in `destroy.sh` at all.

## Datalog vs. SQL/JPQL, briefly

The `/providers/search` endpoint runs a real Datalog query (`:find`/`:where` clauses over
entity/attribute/value facts), not a linear scan over `list-all`'s results — the closest parallel
to `claims-api`'s JPQL-via-Spring-Data or `catalog-api`'s DynamoDB partition queries, but a
genuinely different query shape: no tables, no joins in the SQL sense, just pattern-matching
against facts. `pull` inside the query is doing roughly what a JPA entity's field access or an EF
Core projection does — picking which attributes come back.

## A real gotcha found building this: shared state between tests

An early version of this sample's `use-fixtures :each` called `init-db!` before every test,
assuming that gave each test a fresh, empty database. It didn't: `create-database` is a no-op if
the named database already exists, and `connect` just reconnects to whatever's already there —
even with `:mem` storage, the database persists for as long as the Datomic client object does
(a `defonce`, so the whole JVM process's lifetime). Every test was silently sharing one
ever-growing database. Caught by actually running the suite and seeing a count assertion fail
with 5 instead of 2, not by inspecting the code and assuming it was fine. Fixed with an explicit
`reset-db!` (delete, then recreate) called from the fixture instead of `init-db!` alone -
verified the fix by reverting it and confirming the test fails again the old way before
restoring it, same rigor as every other regression fix in this repo.

## Getting the Clojure CLI installed

`brew install clojure/tools/clojure` may fail here with "Your Command Line Tools are too
outdated" — a stale Xcode CLT issue unrelated to Clojure itself (the formula's `rlwrap`/
`libptytty` dependencies need it for compilation; the core `clojure`/`clj` scripts don't).
Worked around by installing directly from the official tarball
(`https://download.clojure.org/install/clojure-tools-<version>.tar.gz`) into `~/.local/`
by hand, sidestepping Homebrew entirely. `deploy.sh` checks for `clojure` on `PATH` first and
falls back to `~/.local/bin/clojure`, the same "don't assume, check and fall back" pattern as
this repo's "Apple's stub `java`" handling elsewhere.

## Running it

```
./deploy.sh    # run the tests, start the service, create/get/list/search
./destroy.sh   # stop the process - no other state to clean up
```

Runs on `http://localhost:8087`.
