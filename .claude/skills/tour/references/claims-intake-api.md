# claims-intake-api

## ELI5

The classic interview question: "how would you process a huge CSV file if you had limited
resources?" This sample answers it as an actual decision tree instead of picking one approach
and hand-waving the rest: a small upload gets read, validated, and written out right there in
the request — fast enough that nobody needs to wait around for something better. A large upload
gets validated and handed off to a stream (Kinesis) for a separate consumer to write out at its
own pace, so the caller's HTTP request isn't held hostage by a slow write. Same bounded-memory
CSV read either way (never loads the whole file into memory) — the only thing the size threshold
changes is *when* the slow part happens, before or after the response.

This is also the most heavily reviewed and tested sample in the repo — it went through a real
multi-lens review pass (security/performance/architecture/test-coverage + a holistic pass) that
found three genuine bugs, all fixed, all with regression tests that were verified to actually
fail against the pre-fix code before the fix was restored. If "what does taking review findings
seriously actually look like in practice" is the thing of interest, this sample is the answer.

## Deploy shape, and why

Deploys to Floci — the first sample in the repo to use Kinesis Data Streams rather than SQS
(`events-api` already covers queues; this one's the stream-shaped counterpart). Verified directly
against Floci (`PutRecords` and `GetRecords` via the AWS CLI) *before* writing any of the rest of
the sample, given this repo's track record of AWS-shaped resources with real Floci gaps
(OpenSearch, RDS). Stream creation itself is fine and fast. What isn't fine: Floci's `PutRecords`
costs real per-record time internally (~35-60ms *per record inside a batch*, not a fixed per-call
cost — measured directly, not guessed) — the demo data and default threshold are sized down
accordingly, since there's no code-level fix for an emulator being slow. Full writeup in the
sample's own README.

## Reading order

1. `app/src/main/java/.../domain/ClaimRow.java`, `BatchSummary.java`, `BatchMode.java`,
   `BatchStatus.java` — the shape of a row and a batch's lifecycle.
2. `app/src/main/java/.../service/ClaimRowParser.java` — validation, and a real security fix.
3. `app/src/main/java/.../service/CsvRowReader.java` — the shared read-loop both processing
   modes use (added during the review-fixes pass, to remove duplication).
4. `app/src/main/java/.../service/StreamingBatchProcessor.java` — the small-file path.
5. `app/src/main/java/.../service/KinesisBatchProducer.java` +
   `service/KinesisBatchConsumer.java` — the large-file path, producer and consumer.
6. `app/src/main/java/.../service/BatchIngestService.java` — the decision tree itself, one `if`.
7. `app/src/main/java/.../web/BatchController.java` + `ApiExceptionHandler.java`.
8. The test suite (`app/src/test/.../service/`) — see "the tests are worth reading too," below.

## Don't miss these

- `ClaimsIntakeApiApplication.java:8` — same `@Scheduled`/`@EnableScheduling` ⇄
  `BackgroundService` parallel as `events-api`, on the consumer side of this sample's own
  producer/consumer pair.
- `service/ClaimRowParser.java:10-13` — a non-instantiable holder class for one static method.
  C#'s `static class` modifier enforces this at the language level (no instance members allowed
  at all); Java has no such modifier, so the convention is a `final` class with a `private`
  no-arg constructor to block `new ClaimRowParser()` by hand.
- `service/ClaimRowParser.java` (the `billedAmount` validation) — a real, reproduced security
  bug: a crafted value like `"1e-2147483600"` parsed successfully and even passed the sanity
  range check (`BigDecimal.compareTo`'s fast path doesn't need to materialize the value), but
  threw an unhandled `ArithmeticException` the moment it hit `.add()` against a running total —
  a 500 that lost the whole batch, no auth needed, tiny payload. Fixed by bounding scale/
  precision at the same validation boundary as every other check. There's a test
  (`ClaimRowParserTest.pathologicalScaleThatWouldOverflowLaterArithmeticIsRejected`) that was
  verified to fail against the unguarded parser before the fix was restored.
- `domain/BatchSummary.java:7-10` — named static factories instead of a public canonical
  constructor read clearer at call sites than `new BatchSummary(id, mode, status, ..., null,
  null)`. C# doesn't really have Java's convention of hiding the canonical constructor in favor
  of named creators the way this does.
- `domain/ClaimRow.java:6-9` — a plain record round-trips through Jackson field-for-field with
  zero annotations, the same way a C# record round-trips through `System.Text.Json` — which is
  exactly why this one type doubles as the REST response shape, the Kinesis wire format, *and*
  the output-file format.
- `service/ClaimStreamMessage.java:5-11` — this could be a sealed interface with two record
  implementations and an exhaustive `switch` (Java 21's closest thing to a C# discriminated
  union), but it's deliberately a flat tagged record instead, because that maps straight onto
  Jackson's default (de)serialization with no custom subtype configuration — a real tradeoff,
  spelled out in the comment, not an oversight.
- `service/CsvRowReader.java:22-24` — a functional interface just for one callback. C#'s closest
  equivalent is a delegate type (`Action<ClaimRow>`), except Java has nothing built in that
  declares a checked exception, which this one needs (`ClaimOutputWriter.writeRow` does real
  file I/O and can throw `IOException`).
- `service/KinesisBatchProducer.java` — the batching-boundary comment: a naive one-`PutRecord`-
  per-row producer made a 6000-row upload take *minutes*; switched to batching up to 500 records
  per `PutRecords` call. `KinesisBatchProducerTest.flushesAtExactlyFiveHundredRecordsNotBeforeOrAfter`
  locks in that exact boundary — and was verified to actually fail if the boundary constant
  changes, not just pass by coincidence.
- `service/KinesisBatchConsumer.java` — two real bugs, both found by the review pass, both
  fixed, both regression-tested:
  1. A batch where every row fails validation never gets a `ROW` message, so there's nothing to
     complete when the end marker arrives — it used to get stuck in `PROCESSING` forever.
     Fixed to treat a zero-row end marker as a legitimate (empty) completion.
  2. The batch used to get registered in `BatchStore` only *after* the whole Kinesis publish
     finished, racing this independent consumer, which could reach the end marker first and
     throw `BatchNotFoundException` — silently swallowed, batch stuck forever again, this time
     from a timing race instead of bad content. Fixed by registering a placeholder before
     publishing a single record.

## The tests are worth reading too

Five test classes, 34 tests, added specifically to lock in real bugs — not written first and
assumed correct, but each regression test was verified to actually *fail* against the pre-fix
code, then the fix was restored, before being called done:

- `ClaimRowParserTest` — 16 cases, pure-function validation logic, no mocking needed.
- `BatchIngestServiceTest` — the ordering-race regression test uses Mockito's `InOrder` +
  `ArgumentCaptor` together to prove the placeholder is saved *before* `publish()` is ever
  called, not just that both happen eventually. Good example to point at for "what does a
  precise ordering test look like."
- `ClaimStreamMessageTest` — includes a test proving Jackson has no built-in `java.time` support
  and throws `InvalidDefinitionException` without `JavaTimeModule` registered — confirmed
  empirically what exception and message it actually throws, not assumed. A C# dev coming from
  `System.Text.Json` (which handles `DateOnly`/`DateTime` out of the box) wouldn't expect this.
- `KinesisBatchProducerTest` / `KinesisBatchConsumerTest` — mocked `KinesisClient`/`BatchStore`,
  plus a real `@TempDir` for the consumer's actual file writes rather than mocking the
  filesystem too. Good example of "mock the boundary you don't own, use the real thing for the
  boundary you do."

## Running it

```
./deploy.sh    # provision the stream, generate demo CSVs, upload one small + one large, poll
./destroy.sh   # tear the stream down; kills the app process too
```

Runs on `http://localhost:8085`.
