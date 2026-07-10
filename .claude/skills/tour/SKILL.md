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
  hello-api/          Lambda + API Gateway, deployed to Floci        — the quickstart template
  task-api/            Spring Boot REST API, plain Docker             — no Floci, no database
  search-api/          Spring Boot + OpenSearch, plain Docker         — Floci gotcha #1
  catalog-api/         Spring Boot + DynamoDB, deployed to Floci      — NoSQL
  claims-api/          Spring Boot + JPA + Postgres, plain Docker     — SQL, Floci gotcha #2
  events-api/          Spring Boot + SNS/SQS, deployed to Floci       — async messaging (queue)
  claims-intake-api/   Spring Boot + Kinesis, deployed to Floci       — async messaging (stream)
```

**Two deploy shapes, chosen per-sample, not by convention:** a sample deploys to Floci when it's
genuinely AWS-shaped (Lambda, DynamoDB, SNS/SQS, Kinesis) and Floci handles that resource well.
It deploys via Pulumi's Docker provider straight to the local Docker daemon instead when it's
"just a container" (task-api) or when Floci's own emulation of that specific AWS resource has a
real gap (search-api/OpenSearch, claims-api/Postgres-via-RDS — both documented, both genuine
findings, not assumptions). That split is itself worth walking someone through — it's a real
design decision, not boilerplate.

**Suggested order** (roughly increasing complexity, each one adds one new idea on top of the
last): `hello-api` → `task-api` → `catalog-api` → `claims-api` → `search-api` → `events-api` →
`claims-intake-api`.

## Looking for a specific pattern?

| Interested in... | Go to |
|---|---|
| Serverless / Lambda | `hello-api` |
| "Just a container," no database | `task-api` |
| A NoSQL database | `catalog-api` (DynamoDB) |
| A SQL database / ORM | `claims-api` (Postgres + JPA/Hibernate) |
| Full-text search | `search-api` (OpenSearch) |
| Async messaging, queue-shaped (fan-out, retry, dead-letter) | `events-api` (SNS → SQS) |
| Async messaging, stream-shaped (ordered, replayable, high-throughput) | `claims-intake-api` (Kinesis) |
| A decision tree / picking a strategy at runtime | `claims-intake-api` (small vs. large upload) |
| Infrastructure as Code in Java (Pulumi) | any sample's `infra/` — `hello-api`'s is the simplest |
| A real bug that got found and fixed while building this | `claims-api` (Hibernate lazy-loading), `catalog-api` (a TOCTOU race), `events-api` (a message deleted before it was actually processed), `claims-intake-api` (three: a stuck-forever state bug, an ordering race, a BigDecimal crash) |
| Real emulator limitations, not assumptions | `search-api`/`claims-api` (Floci's OpenSearch/RDS gaps), `claims-intake-api` (Kinesis `PutRecords` is real but slow per-record) |

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

## A note on tests

Most samples have real unit tests (JUnit 5 + Mockito + AssertJ) — `hello-api` and `search-api`
are the two without any. `claims-intake-api` has by far the deepest set (34 tests across 5
classes), written specifically to lock in real bugs found during a multi-lens review pass — each
regression test was verified to actually fail against the pre-fix code before the fix was
restored, not just assumed to catch the bug. If "what does a good regression test look like"
comes up, `KinesisBatchConsumerTest`'s zero-valid-rows test and `BatchIngestServiceTest`'s
ordering test (`InOrder` + `ArgumentCaptor`) are the two best examples to point at.
