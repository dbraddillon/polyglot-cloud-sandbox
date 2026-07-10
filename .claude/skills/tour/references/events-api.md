# events-api

## ELI5

Sometimes you don't want the caller to wait for work to finish — you want to hand it off and let
them move on, like dropping a letter in a mailbox instead of standing at the counter until it's
delivered. This sample is that pattern: an SNS topic fans out to an SQS queue, a REST endpoint
publishes and immediately returns, and a completely independent background poller consumes the
queue at its own pace. `POST /events` returns `202 Accepted`, not `201 Created` — there's nothing
to point a `Location` header at yet, since the thing being pointed at might not exist for another
second or two.

This is the queue-shaped half of "async messaging" in this repo; `claims-intake-api` is the
stream-shaped half — worth reading both back to back if messaging patterns are the thing of
interest, since they're deliberately built to contrast (queue vs. stream, `ReceiveMessage` vs.
shard iterators, dead-letter queue vs. no-checkpoint replay).

## Deploy shape, and why

Deploys to Floci — SNS and SQS are both AWS-shaped concepts Floci emulates well, no gotchas found
building this one (contrast with `search-api`/`claims-api`, where the AWS-shaped emulation had
real gaps).

## Reading order

1. `app/src/main/java/.../domain/EventMessage.java`, `ProcessedEvent.java` — the shape of a
   message and what "processed" looks like.
2. `app/src/main/java/.../service/EventPublisher.java` — publish, fire-and-forget.
3. `app/src/main/java/.../service/EventConsumer.java` — the `@Scheduled` poller; this is the
   one file worth spending the most time on.
4. `infra/src/main/java/.../App.java` — topic, queue, dead-letter queue, subscription, policy.

## Don't miss these

- `EventsApiApplication.java:8` and `EventConsumer.java:22` — `@Scheduled` + `@EnableScheduling`
  is Spring's version of registering a `BackgroundService`/`IHostedService` in .NET — except
  here it's one annotation on one method, not a whole class implementing an interface and
  overriding `ExecuteAsync`.
- `EventConsumer.java` — the delete-only-on-success logic is the most important few lines in the
  file: `sqs.deleteMessage(...)` only runs inside the `try`, after `store.save(...)` succeeds.
  SQS is at-least-once delivery — deleting unconditionally (including from a catch block, or
  after one) would mean a message that failed to process is gone for good instead of becoming
  visible again for a retry once its visibility timeout expires. This was a real bug, found and
  fixed: it used to delete unconditionally regardless of whether processing succeeded.
- `EventConsumer.java` — the SNS-envelope unwrap: a message delivered via an SNS→SQS
  subscription isn't your raw payload. SNS wraps it in its own JSON
  (`{"Type":"Notification","Message":"<your actual body>",...}`), and the real payload is a
  *string* sitting inside that `Message` field, not a nested object. A queue fed directly by a
  producer (no SNS in front) wouldn't need this unwrap step at all.
- `EventPublisher.java:35` — a checked exception forcing a `try`/`catch` around a JSON
  serialization that can't realistically fail — a concept C# doesn't have at all (no exception
  in .NET is "checked," the compiler never forces a catch based on a method signature).
- `infra/.../App.java` — the dead-letter queue: a message that fails processing
  `maxReceiveCount` times lands there instead of retrying forever or silently vanishing. Nothing
  in this sample actually reads the DLQ — the point is demonstrating that a redrive target
  exists at all, same as you'd want on any real at-least-once consumer.

## Eventual consistency is real, not simulated

`GET /events/{id}` immediately after `POST /events` can 404 if the poller hasn't run yet.
`deploy.sh` retries for exactly this reason — it's the actual behavior of the pattern, not a
sandbox artifact. This is worth calling out explicitly if anyone's surprised by it: "asynchronous
messaging" *means* there's a real window where the caller's own view is stale.

## Running it

```
./deploy.sh    # provision the topic/queue, publish an event, poll until consumed
./destroy.sh   # tear down the topic/queue; kills the app process too
```

Runs on `http://localhost:8084`.
