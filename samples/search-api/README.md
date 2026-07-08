# search-api

A Spring Boot search API over a real OpenSearch engine — index documents, fetch one by id,
full-text search across title and body. No OpenSearch client library: `RestClient`
(Spring Framework 6.1+) talks straight to OpenSearch's plain HTTP/JSON REST API, the same way a
lot of real projects do instead of pulling in the official client.

## Endpoints

| Method | Path                 | Notes                                    |
|--------|----------------------|-------------------------------------------|
| POST   | `/documents`         | `{"title": "...", "body": "..."}`, 400 if either is blank |
| GET    | `/documents/{id}`    | 404 if missing                            |
| GET    | `/documents/search?q=` | `multi_match` across title + body       |

## Why this doesn't use Floci (and originally tried to)

This sample's infra originally provisioned a real `aws.opensearch.Domain` via Pulumi against
Floci — and that mostly worked: Floci genuinely pulls and runs the real
`opensearchproject/opensearch` image per domain, not a control-plane stub. The engine itself was
healthy (`cluster_health: green`) within about two minutes.

The problem: Floci's AWS OpenSearch Service emulation never flips the domain's `Created` status
to `true`, and Pulumi's AWS provider waits on exactly that field before considering the resource
created. `pulumi up` hung for over 45 minutes with a perfectly healthy cluster sitting right
there the whole time. Confirmed via `aws opensearch describe-domain` directly — `Processing`
stayed `true` indefinitely. That's a real limitation in Floci v1.5.31, not a Pulumi or Java
issue, and not something a `CustomTimeouts` override actually fixes (it would just make the
`pulumi up` fail faster, not succeed).

Rather than fight it, this sample runs the identical OpenSearch image directly via Pulumi's
**Docker provider** instead — the same move `orders-api` already makes for Postgres instead of
Floci's RDS emulation (which has its own, different limitation: it works, but keeps its
container off the host network entirely). Same engine, same REST API, no AWS-shaped
control-plane in the way.

## A gotcha unrelated to Floci: `java` on macOS

`deploy.sh` runs the Spring Boot app directly (not containerized) so it can reach OpenSearch's
host-published port without fighting Docker network boundaries. Plain `java -jar` can hit a
wall on macOS if a Homebrew-installed JDK isn't linked as the system default - Apple ships a
stub `/usr/bin/java` that just prints "install Java" instead of running anything, even though
`mvn` finds a real JDK fine through its own resolution. `deploy.sh` checks for this and falls
back to `/opt/homebrew/opt/openjdk@21/bin/java` (or `$JAVA_HOME`) if bare `java` doesn't work.

## Running it

```
./deploy.sh    # start OpenSearch, run the app, index a document, search for it
./destroy.sh   # stop the OpenSearch container; kills the app process too
```

Runs on `http://localhost:8081`; OpenSearch itself is published on `9201` if you want to hit it
directly (`curl localhost:9201/_cluster/health?pretty`).
