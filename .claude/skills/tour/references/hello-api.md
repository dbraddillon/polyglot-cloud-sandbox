# hello-api

## ELI5

Before any of the other samples make sense, you need the smallest possible answer to "how does
Java code end up running in AWS, deployed by Java code?" This sample is that answer, stripped
to almost nothing: one Lambda function, one API Gateway route in front of it, one Pulumi program
that creates both. Say hello, get a JSON greeting back. That's the whole feature.

It's also the one sample kept deliberately neutral (no health-insurance theming) — the
quickstart template everything else was copied from.

## Deploy shape, and why

Deploys to Floci. Lambda + API Gateway are AWS-specific concepts — there's no "just run it
locally" for a Lambda the way there is for a Spring Boot app, so it needs an actual AWS emulator
standing in. This is the one sample where Floci is doing exactly what it's for, no caveats.

## Reading order

1. `app/src/main/java/.../Handler.java` — the whole Lambda, ~40 lines.
2. `infra/src/main/java/.../App.java` — the whole Pulumi program: the Lambda function, the API
   Gateway route, wired together.
3. `deploy.sh` — note the Floci-specific routing gotcha before running it (see below).

## Don't miss these

- `Handler.java:11` — a Java rule with no C# equivalent at all: the public class name must
  match the file name exactly (`Handler.java` → `class Handler`). Worth pausing on since it's
  the kind of thing that only becomes obvious the first time you rename a file and the build
  breaks for a reason that looks like nothing.
- `Handler.java:17` — `@Override` is an annotation the compiler reads, not a keyword like C#'s
  `override` — and it's optional. Leaving it off compiles fine; it's a style convention, not
  enforcement.
- `Handler.java:21` — no null-conditional operator (`?.`) in Java; the explicit null check here
  is the normal idiom, not a workaround.
- `Handler.java:33` — Java has no object-initializer syntax (`new Foo { X = 1 }`); the builder
  pattern (`APIGatewayV2HTTPResponse.builder()...build()`) is what fills that gap, and it's a
  shape you'll see in almost every other sample too (AWS SDK requests/responses, Pulumi resource
  args).
- `infra/.../App.java:25-28` — a fun one: Pulumi's .NET SDK and its Java SDK are nearly
  identical method-for-method (`Output<T>.Apply`, `ctx.Export`/`ctx.export`) — just PascalCase
  vs. camelCase, which is the general Java/C# method-naming split.

## A real Floci quirk worth knowing

AWS-shaped outputs from Floci *look* real but aren't directly reachable — `apiUrl` renders as an
actual `*.execute-api.*.amazonaws.com`-style hostname, but Floci doesn't do subdomain routing.
The real route is path-based:
`http://localhost:4566/restapis/<api-id>/$default/_user_request_/<route>`. `deploy.sh` already
handles this; worth knowing before assuming the printed URL just works with a plain `curl`.

## Running it

```
./deploy.sh    # build the jar, provision the Lambda + API Gateway, curl it
./destroy.sh   # tear both down
```
