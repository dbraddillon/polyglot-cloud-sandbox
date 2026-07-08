# events-api

A Spring Boot service demonstrating the async messaging pattern called out explicitly in the
target job posting: an SNS topic fanning out to an SQS queue, a REST endpoint that publishes,
and a background poller that consumes independently. No request ever waits on processing.

## Endpoints

| Method | Path           | Notes                                                          |
|--------|----------------|------------------------------------------------------------------|
| POST   | `/events`      | `{"type": "...", "payload": "..."}` → 202 Accepted, not 201       |
| GET    | `/events`      | processed events only (not yet-queued ones - see below)          |
| GET    | `/events/{id}` | 404 if not found *or* not processed yet                          |

## The pipeline

```
POST /events → EventPublisher → SNS topic → (fanout) → SQS queue → EventConsumer (poller) → ProcessedEventStore
```

- `EventPublisher` serializes an `EventMessage` and publishes it to SNS - fire and forget.
- `EventConsumer` is a `@Scheduled` poller (needs `@EnableScheduling` on the application class -
  see `EventsApiApplication`) that long-polls SQS, unwraps each message, and stores it.
- **The SNS envelope gotcha**: a message that arrives via an SNS→SQS subscription isn't your
  payload directly. SNS wraps it in its own JSON (`{"Type":"Notification","Message":"<your
  actual body>",...}`), and you have to parse that wrapper and pull the inner `Message` field
  out before you get back what you published. A queue fed directly by a producer (no SNS in
  front of it) wouldn't need this unwrap step at all - see `EventConsumer.SnsEnvelope`.
- **Eventual consistency is real, not simulated**: `GET /events/{id}` immediately after
  `POST /events` can 404 if the poller hasn't run yet. `deploy.sh` retries for this reason. This
  is the actual behavior of the pattern, not a sandbox artifact - it's exactly what "asynchronous
  messaging" in the job posting means in practice.

## Infra: real IaC even where Floci might not need it

The Pulumi program includes an `aws.sqs.QueuePolicy` explicitly granting the SNS topic
permission to deliver to the queue. Floci may not actually enforce this (most local emulators
skip IAM policy checks entirely) - but leaving it out would be *wrong* IaC for real AWS, where a
missing queue policy means publishes silently never arrive. Written the way it has to work in
production, not the minimum that happens to work locally.

## Running it

```
./deploy.sh    # provision the topic/queue, run the app, publish an event, poll until consumed
./destroy.sh   # tear down the topic/queue; kills the app process too
```

Runs on `http://localhost:8084`. Same "Apple's stub `java`" and `AWS_REGION` vs.
`AWS_DEFAULT_REGION` caveats as catalog-api/search-api apply here too.
