# claims-intake-api

The classic "process a large CSV under limited resources" interview problem, plus the decision
of *when* limited resources actually force your hand: below a size threshold, read/validate/
write the upload directly on the request thread; at or above it, validate and hand rows to
Kinesis so a separate consumer does the actual write-out at its own pace. Same bounded-memory
CSV read either way - the only thing the threshold changes is whether the slow part happens
before or after the HTTP response.

## Endpoints

| Method | Path                  | Notes                                                    |
|--------|-----------------------|-------------------------------------------------------------|
| POST   | `/claims-intake`      | multipart `file` (CSV) → 200 if streamed, 202 if queued      |
| GET    | `/claims-intake/{id}` | current `BatchSummary`; 404 if unknown                       |
| GET    | `/claims-intake`      | all batches seen so far                                      |

## The decision tree

```
upload (MultipartFile.getSize())
  │
  ├─ ≤ streaming-threshold-bytes ──▶ StreamingBatchProcessor
  │                                  read → validate → write JSON Lines, all on the request
  │                                  thread. Returns 200, already COMPLETE.
  │
  └─ > streaming-threshold-bytes ──▶ KinesisBatchProducer
                                     read → validate → batch onto Kinesis (PutRecords, ≤500/call)
                                     + an end-of-batch marker. Returns 202, PROCESSING.
                                     KinesisBatchConsumer (a @Scheduled poller) drains the
                                     stream independently, writes JSON Lines as rows arrive,
                                     flips the batch to COMPLETE on the end marker.
```

The split is on **upload size**, decided *before* reading a single byte of the file - the whole
point is picking a strategy without paying the cost of a full scan first, and byte size is the
one thing already known for free from the multipart upload. Both branches use the exact same
`ClaimRowParser` and stream the file with a plain `BufferedReader`, one line at a time - memory
use is flat either way, whether the file is 15 rows or 15 million. What genuinely differs:

- **Streaming mode** does the whole job - read, validate, write - synchronously, so it's only
  appropriate while that's still *fast*, not just low-memory. A caller's HTTP request blocks
  until it returns.
- **Queue mode** does the cheap part (validate, serialize, batch-publish) synchronously and
  defers the part whose cost doesn't scale as nicely - here, writing the transformed output -
  to an independent consumer. That's the actual reason to reach for a queue: not "the file is
  big," but "the downstream write is slow/rate-limited/expensive enough that you don't want the
  caller's request hostage to it." A batch that would take 30 seconds to fully write out returns
  in milliseconds instead, at the cost of `GET /claims-intake/{id}` needing to be polled for the
  real completion.

## Why Kinesis and not SQS

events-api already covers SQS (a queue). This sample uses Kinesis Data Streams instead - a
better fit for "a stream of CSV rows," and genuinely different in shape: no `ReceiveMessage`,
just a shard iterator you keep exchanging for the next one via `GetRecords`. Verified directly
against Floci (both `PutRecords` and `GetRecords`) before writing any of the rest of this sample,
given this repo's track record of AWS-shaped services that don't fully work against Floci
(OpenSearch, RDS - see the root `CLAUDE.md`). Stream creation itself is fine and fast (~20-24s,
same order as any other Pulumi resource here).

**What isn't fine: Floci's `PutRecords` costs real per-record time.** A single `PutRecords` call
with 500 tiny records took **~31 seconds** against Floci (measured directly with the AWS CLI,
not through this app) - roughly 35-60ms *per record inside the batch*, not a fixed per-call cost
(20 records: ~1s, 200 records: ~7s). A naive one-`PutRecord`-per-row producer made this obvious
immediately: uploading a 6000-row CSV took minutes. Switching to batched `PutRecords` (≤500
records/call, `KinesisBatchProducer`) cut the round-trip count from 6000 to ~12, which helps on
a real AWS account but barely helps against Floci specifically, since the cost lives inside each
record processed, not in per-call HTTP overhead. There's no code-level fix for an emulator being
slow - the demo CSVs (`deploy.sh`) are sized to a few hundred rows accordingly, and
`streaming-threshold-bytes` defaults to a deliberately small 20KB rather than something more
realistic, so the queue-mode branch is reachable without a multi-minute upload. A real AWS
account has no such per-record tax; a production threshold would reasonably be much higher.

## Other simplifications, on purpose

- **Single shard only.** Kinesis has no SQS-style `ReceiveMessage` - a consumer holds a shard
  iterator and keeps exchanging it for the next one. Multiple shards would need one iterator per
  shard plus a checkpoint store (this is exactly what the Kinesis Client Library / DynamoDB
  checkpointing exists to solve), which is real added complexity a demo CSV firehose doesn't
  need.
- **No checkpointing.** `KinesisBatchConsumer` always starts from `TRIM_HORIZON` (the oldest
  untrimmed record) on startup rather than persisting its position - a restart mid-batch
  reprocesses from the start of the stream's retention window. Fine for a sandbox, not how a
  production consumer should behave.
- **Naive CSV parsing.** `ClaimRowParser` is a `split(",")`, not a real CSV library - no quoted-
  field/embedded-comma support. Fine because the only CSVs it ever sees are ones `deploy.sh`
  generated itself. A real intake endpoint reading arbitrary uploads would want something like
  Apache Commons CSV instead.
- **JSON Lines, not Parquet.** Both processing modes write one JSON object per line (NDJSON) -
  proves out the same bounded-memory read/write story a columnar format would, without pulling
  in a Parquet library purely for the output-format demo. A real analytics pipeline would
  likely want Parquet at this step (columnar, compressed, schema-carrying).
- **Partial `PutRecords` failures are logged, not retried.** `failedRecordCount` on the response
  tells you how many of a batch were rejected (e.g. throttling); a production producer would
  retry just those entries. This sandbox only logs a warning.

## Running it

```
./deploy.sh    # provision the stream, generate demo CSVs, upload one small + one large, poll
./destroy.sh   # tear the stream down; kills the app process too
```

Runs on `http://localhost:8085`. Same "Apple's stub `java`" and `AWS_REGION` vs.
`AWS_DEFAULT_REGION` caveats as the other Floci-based samples apply here too.
