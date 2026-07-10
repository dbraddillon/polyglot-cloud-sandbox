# search-api

## ELI5

Sooner or later a real product needs "let me search for text across a bunch of documents," and a
SQL `LIKE '%...%'` doesn't scale to that. This sample is full-text search over a real OpenSearch
engine: index a document (title + body), fetch it by id, or search across both fields. It's also
notable for what it *doesn't* use — no OpenSearch client library, just Spring's `RestClient`
talking straight to OpenSearch's plain HTTP/JSON REST API, the way a lot of real projects
actually do it instead of pulling in an official SDK.

## Deploy shape, and why (this one's a good story)

This sample's infra originally provisioned OpenSearch through Floci like every other AWS-shaped
resource — and it *mostly* worked. Floci genuinely pulls and runs a real
`opensearchproject/opensearch` container, healthy within about two minutes. The catch: Floci's
emulation of AWS's OpenSearch Service never flips the domain's `Created` status to `true`, and
Pulumi's AWS provider waits on exactly that field. Result: `pulumi up` hung for 45+ minutes
against a perfectly healthy cluster — confirmed directly with `aws opensearch describe-domain`,
not assumed. That's a real Floci limitation, not a Pulumi or Java issue.

The fix: run the identical OpenSearch image directly via Pulumi's **Docker provider** instead —
same engine, same REST API, no AWS control-plane standing in the way. `claims-api` makes the
same move for Postgres, for a different reason (Floci's RDS emulation works, but keeps its
container off the host network entirely).

## Reading order

1. `app/src/main/java/.../config/OpenSearchClientConfig.java` — the whole client setup, ~10 lines.
2. `app/src/main/java/.../domain/Document.java` and the `service`/`web` layers — plain, since
   search itself is domain-agnostic.
3. `infra/src/main/java/.../App.java` — Docker provider running the OpenSearch image directly.

## Don't miss these

- `config/OpenSearchClientConfig.java:8-11` — `RestClient` (Spring Framework 6.1+) is Spring's
  modern synchronous HTTP client, conceptually close to .NET's `HttpClient` with a more fluent,
  `WebClient`-flavored builder API. `@Configuration` + `@Bean` here is the Spring equivalent of
  registering a typed client in `Program.cs`.
- The sample's README (`samples/search-api/README.md`) has the full OpenSearch/Floci story with
  exact timings and the CLI commands used to confirm it — worth reading in full if the "found a
  real emulator limitation, didn't just assume it" story is interesting.

## A gotcha unrelated to Floci

`deploy.sh` runs the Spring Boot app directly, not containerized (so it can reach OpenSearch's
host-published port without fighting Docker network boundaries). On macOS, plain `java -jar` can
hit Apple's stub `/usr/bin/java` (which just prints "install Java") if a Homebrew JDK isn't
linked as the system default — even though `mvn` finds a real JDK fine through its own
resolution. `deploy.sh` checks for this and falls back to
`/opt/homebrew/opt/openjdk@21/bin/java` or `$JAVA_HOME`. The same pattern shows up in
`catalog-api`'s and `events-api`'s `deploy.sh` too.

## Running it

```
./deploy.sh    # start OpenSearch, run the app, index a document, search for it
./destroy.sh   # stop the OpenSearch container; kills the app process too
```

Runs on `http://localhost:8081`; OpenSearch itself is published on `9201` if you want to hit it
directly (`curl localhost:9201/_cluster/health?pretty`).
