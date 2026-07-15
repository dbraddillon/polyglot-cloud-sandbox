---
name: tour
description: Use when someone wants a guided walkthrough, overview, or ELI5 explanation of this repo — new to the codebase, asking "where do I start", "give me a tour", "explain this repo", "what is this project", or invoking /tour explicitly. Also use when someone wants to understand a specific tech pattern (messaging, a database choice, IaC, Lambda) without knowing which sample demonstrates it, or wants the highlights of the Java/C#-.NET comparison comments scattered through the code.
---

# Repository tour

This repo is a set of small, independent samples, each one a self-contained slice through a
different Java/Spring/AWS pattern, deployed locally (never to a real cloud account) via
[Floci](https://floci.io) (an AWS emulator) or plain Docker. The code carries inline comments
comparing Java/Spring constructs to their closest C#/.NET equivalents throughout — useful if
you're coming from that background, harmless if you're not.

This skill conducts a guided walkthrough. It is not a substitute for reading the code — it's a
map and a set of pointers into the good parts, so a first pass through an unfamiliar sample
takes ten minutes instead of an hour.

## How to conduct the tour

Ask what they want, don't just launch into a monologue. Options, roughly:

1. **The whole tour, end to end** — walk every sample in the suggested order below, one at a
   time, pausing between each for questions before moving on.
2. **A specific sample** — jump straight to it.
3. **A specific tech pattern** ("show me how messaging works here", "what's the database
   story") — use the pattern index below to find the right sample(s), then tour those.
4. **Just the interesting parts** — skip straight to the "highlight reel" below, or to a
   sample's "aha" comments without the full walkthrough.

For whichever sample(s) are in scope, read that sample's reference file at
`.claude/skills/tour/references/<sample-name>.md` and use it to structure the walkthrough:

- Start with the ELI5 — the problem this pattern solves, in plain language, before any code.
- Then the deploy shape (Floci vs. Docker) and *why* that one, not the other.
- Then open the actual files in the suggested reading order (use the Read tool — quote real
  code, don't paraphrase from memory) and narrate the 3-4 "don't miss this" moments called out
  in the reference file, with their real file:line locations so the reader can jump straight
  there themselves afterward.
- Don't re-explain what the sample's own README already says well — point to it
  (`samples/<name>/README.md`) rather than duplicating it. This tour adds the connective
  narrative and the "why," not a restatement.
- End each sample with how to actually run it (`./deploy.sh` / `./destroy.sh`) and ask if they
  want to see it running live, move to the next sample, or dig deeper into this one.

Keep the tone conversational and light — this is meant to be a friendly onboarding, not a spec
review. Skip ceremony; get to the interesting part fast.

## The big picture

```
samples/
  hello-api/           Lambda + API Gateway, deployed to Floci        — the quickstart template
  task-api/            Spring Boot REST API, plain Docker             — no Floci, no database
  search-api/          Spring Boot + OpenSearch, plain Docker         — Floci gotcha #1
  catalog-api/         Spring Boot + DynamoDB, deployed to Floci      — NoSQL
  claims-api/          Spring Boot + JPA + Postgres, plain Docker     — SQL, Floci gotcha #2
  events-api/          Spring Boot + SNS/SQS, deployed to Floci       — async messaging (queue)
  claims-intake-api/   Spring Boot + Kinesis, deployed to Floci       — async messaging (stream)
  python-api/          Python Lambda + API Gateway, deployed to Floci — same infra, Python workload
  node-api/            Express REST API, plain Docker                — same shape as task-api, Node
  clojure-datomic-api/ Clojure + Datomic dev-local, no infra at all   — furthest from Java
  attachments-api/     Spring Boot + S3, deployed to Floci            — the most direct AWS-SDK sample
```

**Three deploy shapes, chosen per-sample, not by convention:** a sample deploys to Floci when
it's genuinely AWS-shaped (Lambda, DynamoDB, SNS/SQS, Kinesis) and Floci handles that resource
well. It deploys via Pulumi's Docker provider straight to the local Docker daemon instead when
it's "just a container" (task-api, node-api) or when Floci's own emulation of that specific AWS
resource has a real gap (search-api/OpenSearch, claims-api/Postgres-via-RDS — both documented,
both genuine findings, not assumptions). And a sample uses no infra at all when its backing
service is itself embedded/in-process with nothing to provision (clojure-datomic-api's Datomic
`dev-local`) — the newest and rarest of the three. That split is itself worth walking someone
through — it's a real design decision, not boilerplate.

**Most samples are Java; three deliberately aren't** (python-api, node-api,
clojure-datomic-api) — same Java/Pulumi infra pattern throughout, proving the IaC doesn't care
what language the workload is written in. Worth touring back-to-back with their closest Java
sibling (python-api ↔ hello-api, node-api ↔ task-api) to feel what actually differs.

**Suggested order** (roughly increasing complexity, each one adds one new idea on top of the
last): `hello-api` → `task-api` → `attachments-api` → `catalog-api` → `claims-api` →
`search-api` → `events-api` → `claims-intake-api` → `python-api` → `node-api` →
`clojure-datomic-api`.

## Looking for a specific pattern?

| Interested in... | Go to |
|---|---|
| Serverless / Lambda | `hello-api` (Java), `python-api` (Python, same infra) |
| "Just a container," no database | `task-api` (Java), `node-api` (Node, same shape) |
| A NoSQL database | `catalog-api` (DynamoDB), `clojure-datomic-api` (Datomic, a genuinely different query model) |
| A SQL database / ORM | `claims-api` (Postgres + JPA/Hibernate) |
| Object storage (S3) | `attachments-api` |
| Java Streams / functional style, deliberately | `attachments-api`'s `findAll()` (`.stream().map(...).toList()`) |
| Full-text search | `search-api` (OpenSearch) |
| Async messaging, queue-shaped (fan-out, retry, dead-letter) | `events-api` (SNS → SQS) |
| Async messaging, stream-shaped (ordered, replayable, high-throughput) | `claims-intake-api` (Kinesis) |
| A decision tree / picking a strategy at runtime | `claims-intake-api` (small vs. large upload) |
| Infrastructure as Code in Java (Pulumi) | any sample's `infra/` — `hello-api`'s is the simplest |
| The same IaC provisioning a non-Java workload | `python-api`, `node-api` |
| A language with no infra/container/emulator involved at all | `clojure-datomic-api` |
| A real bug that got found and fixed while building this | `claims-api` (Hibernate lazy-loading), `catalog-api` (a TOCTOU race), `events-api` (a message deleted before it was actually processed), `claims-intake-api` (three: a stuck-forever state bug, an ordering race, a BigDecimal crash), `clojure-datomic-api` (a shared-test-state bug) |
| Real emulator limitations, not assumptions | `search-api`/`claims-api` (Floci's OpenSearch/RDS gaps), `claims-intake-api` (Kinesis `PutRecords` is real but slow per-record) |
| A local dev-tool gotcha, not an emulator one | `node-api` (volta shim ordering), `clojure-datomic-api` (Homebrew's Clojure formula vs. stale Xcode CLT) |

## Highlight reel: the best Java ⇄ C#/.NET moments

For "just show me the interesting comments" - a curated top list, spanning the whole repo. Open
each file and quote the real surrounding lines rather than just reading this list aloud.

- **A rule that has no C# equivalent at all**: `samples/hello-api/app/.../Handler.java:11` — the
  public class name must match the file name; C# has no such constraint.
- **Optional vs. nullable references**: `samples/task-api/app/.../InMemoryTaskRepository.java:26`
  — `Optional<T>` is a hard wrapper the caller must unwrap, closer to a real type than C#'s
  nullable-reference-types compiler hint.
- **No auto-properties**: `samples/task-api/app/.../domain/Task.java:24` — explicit get/set
  methods are the idiom Java uses where C# just writes `{ get; set; }`.
- **Two different ORMs, two different entity-mapping stories**: `samples/catalog-api/app/.../domain/Plan.java:8`
  (DynamoDB's enhanced client vs. JPA `@Entity`) and `samples/claims-api/app/.../domain/Claim.java:20`
  (JPA/Hibernate vs. EF Core directly).
- **The best "bug found in production-quality code" comment in the repo**:
  `samples/claims-api/app/.../domain/Claim.java:29-38` — Hibernate's lazy-loading default threw
  a real `LazyInitializationException` while building this sample, contrasted with EF Core's
  opposite (and arguably more dangerous) failure mode: silently empty data, no exception at all.
- **Checked exceptions, a concept C# doesn't have**: `samples/events-api/app/.../EventPublisher.java:35`
  — a compiler-enforced `catch` for a JSON serialization that can't realistically fail.
  Same pattern again in `claims-intake-api`'s `KinesisBatchProducer`.
- **`@Scheduled` vs. `IHostedService`/`BackgroundService`**: appears in both `events-api` and
  `claims-intake-api` application classes — a one-annotation background poller vs. a whole class
  implementing an interface in .NET.
- **Java's closest thing to a C# discriminated union**: `samples/claims-intake-api/app/.../ClaimStreamMessage.java:5-11`
  — deliberately *not* used here (a flat tagged record instead, to stay Jackson-friendly), with
  the tradeoff spelled out in the comment.
- **A trap that would never bite a C# dev**: `samples/claims-intake-api/app/src/test/.../ClaimStreamMessageTest.java:69-74`
  — Jackson has no built-in `java.time` support and throws without `JavaTimeModule` registered;
  `System.Text.Json` just handles `DateOnly`/`DateTime` out of the box. Confirmed the exact
  exception empirically rather than assumed, if that level of detail comes up.
- **Streams as LINQ's closest Java equivalent**: `samples/attachments-api/app/.../S3AttachmentRepository.java`'s
  `findAll()` — `.stream().map(...).toList()` projects raw S3 SDK results into this API's own
  shape in one pass, the same idea as `.Select(...).ToList()`. `.toList()` itself (Java 16+) is
  a shortcut for `.collect(Collectors.toList())`, returning an immutable list rather than a
  hand-built mutable `ArrayList`.

## The three non-Java samples, briefly

Not a Java/C# comparison this time — these compare back to Java instead, since that's this
repo's other constant:

- **No interface to implement at all**: `samples/python-api/app/handler.py` — AWS Lambda's
  Python runtime just calls whatever `module.function_name` string is configured (see
  `infra/.../App.java`'s `.handler("handler.handler")`), no base class or interface the way
  Java's `RequestHandler<In, Out>` requires.
- **No real enum type**: `samples/node-api/app/index.js` — the one status field (`PENDING`/
  `SENT`) is a plain frozen object of string constants, not a type the compiler holds anyone
  accountable to at every call site the way Java's `TaskStatus` enum is in the sibling
  `task-api` sample.
- **No classes, no objects, at all**: `samples/clojure-datomic-api/app/src/.../core.clj` — a
  "Provider" is never reified as a type anywhere, just a plain map passed around. The closest
  thing to a compile-time contract would be a spec/schema library layered on top, deliberately
  skipped to keep the sample small.
- **A real, empirically-found bug in the Clojure sample worth walking through**:
  `samples/clojure-datomic-api/README.md`'s "A real gotcha found building this" section — a
  `use-fixtures :each` that looked like it gave every test a fresh database, but didn't
  (`create-database`/`connect` just reconnect to whatever already exists). Caught by an
  assertion failing with the wrong count, not by code review.

## A note on tests

Most samples have real unit tests (JUnit 5 + Mockito + AssertJ, or that language's closest
equivalent — `node:test` + supertest for node-api, `clojure.test` for clojure-datomic-api).
`hello-api` and `search-api` are the two without any (both Java); `python-api` has none either,
matching `hello-api`'s equally-trivial shape. `claims-intake-api` has by far the deepest set (34
tests across 5 classes), written specifically to lock in real bugs found during a multi-lens
review pass — each regression test was verified to actually fail against the pre-fix code before
the fix was restored, not just assumed to catch the bug. Same discipline shows up in
clojure-datomic-api's test-isolation fix. If "what does a good regression test look like" comes
up, `KinesisBatchConsumerTest`'s zero-valid-rows test and `BatchIngestServiceTest`'s ordering
test (`InOrder` + `ArgumentCaptor`) are the two best Java examples; clojure-datomic-api's
`use-fixtures` story is the best non-Java one. `attachments-api` has 5 straightforward Mockito
tests over its service layer, same shape as `catalog-api`'s.

`task-api` is the one sample tested from two directions at once: JUnit from the inside, plus a
black-box Ruby/Cucumber suite (`task-api/service-tests/`) driving the same behavior purely over
HTTP — point someone here for what a service-level (as opposed to unit) test suite looks like, or
if they know SpecFlow from .NET and want the closest analogue. Building it surfaced a genuine,
non-obvious macOS toolchain bug (Ruby 4.x's C23 header shim breaking native gem extensions under
current Apple clang — see CLAUDE.md's macOS gotchas), diagnosed and worked around the same
evidence-first way as every other bug in this repo: read the actual compiler log, don't guess.

## A request collection, and what "real AWS" would look like

`postman/` has a Postman/Insomnia collection covering every sample's endpoints - point someone
there if they'd rather click through requests in a GUI than read curl commands in a README.
Every AWS-shaped sample runs against Floci only; there's no real-AWS path built yet anywhere in
this repo. If asked about using a real AWS account (e.g. for something Floci fundamentally can't
emulate, like Bedrock), the answer lives in CLAUDE.md's "Real AWS as an opt-in path" section -
the intended shape (opt-in env var, profile-based auth, never a hardcoded account) is documented
there, but nothing has been built against it yet. Don't imply otherwise.

`tools/jenkins/` is the first thing in this repo that isn't a "sample" at all - a local Jenkins,
seeded fully via Configuration as Code + Job DSL (no setup wizard, no hand-clicked job), running
a real pipeline that clones the actual public GitHub repo and runs `task-api`'s JUnit suite. Point
someone here for "what does a CI pipeline look like running against one of these samples," or if
they're wondering why CircleCI (also on the manager's original tool list) doesn't have a matching
setup - it's cloud-only with no meaningful local-execution story, so Jenkins alone covers that
list item.

`tools/jfrog-artifactory/` is the same kind of tool setup, one layer deeper: local Artifactory OSS
+ a real Postgres (current Artifactory refuses to even boot on its embedded Derby DB anymore),
building `task-api`'s actual jar, publishing it via `mvn deploy:deploy-file`, then pulling it back
down and comparing SHA-256 to prove the round-trip is real. Worth pointing to for the OSS-tier
gotcha found along the way: Artifactory's own repository-*management* REST API (creating or
reconfiguring a repo) is Pro-gated, confirmed directly rather than assumed - so this publishes to
the repo OSS ships with out of the box instead of fighting that wall, which still fully
demonstrates the "publish and retrieve a real artifact" capability the tool is there to show.

Datadog metrics went a different route on purpose - not a `tools/` entry, but real changes inside
`samples/task-api/` itself (a Micrometer StatsD dependency, `application.yml` config, a co-located
agent container in `infra/App.java`), since it needed to change the sample's own code/infra rather
than just observe it from outside. Worth pointing to for two things: the "prove it, don't assume
it" bar applied even without a real Datadog account (a dummy API key still gets a fully working
local pipeline - confirmed by watching the agent's own packet/byte counters climb in response to
real HTTP traffic), and a genuinely sneaky Spring Boot 3.x gotcha where the *old*, still-commonly-
tutorialed StatsD config property silently no-ops with zero error output. See task-api's README
"Metrics (Datadog)" section for the full story, including the Colima UDP-forwarding gotcha this
also surfaced.
