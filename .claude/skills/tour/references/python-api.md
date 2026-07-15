# python-api

## ELI5

The other half of `hello-api`'s point: the Java/Pulumi infra program doesn't care what language
the Lambda's actual workload is written in. Same infra shape (IAM role, Lambda, HTTP API
Gateway) provisioned by the exact same kind of Java Pulumi program — just pointed at a Python
3.12 runtime and handler instead of a Java one. Confirmed against Floci directly: the emulator
genuinely runs the Python runtime, not just a stub that only understands Java.

Best toured immediately after (or right alongside) `hello-api` — same shape, same gotchas, one
variable changed.

## Deploy shape, and why

Deploys to Floci, same reasoning as `hello-api` (Lambda + API Gateway need an AWS emulator). This
sample exists specifically to confirm that holds for a non-Java runtime too: `pulumi up`
provisions cleanly and the deployed function returns a real response computed by an actual
Python 3.12 interpreter running inside Floci.

## Reading order

1. `app/handler.py` — the whole Lambda, ~20 lines, no interface to implement.
2. `infra/src/main/java/.../App.java` — identical shape to `hello-api`'s, just
   `Runtime.Python3d12` and `.handler("handler.handler")` instead of a Java class reference.

## Don't miss these

- No `app/pom.xml` here, unlike every Java sample — there's nothing to compile. `FileArchive` in
  `infra/.../App.java` zips the `app/` directory directly at deploy time; Python being
  interpreted means there's no build step standing between "source" and "what actually runs."
- `handler.py:5-8` — no interface to implement, unlike Java's `Handler implements
  RequestHandler<In, Out>` in `hello-api`. AWS Lambda's Python runtime just calls whatever
  `module.function_name` string is configured on the function — no base class required.
- `handler.py:15-17` — a real trap: the Lambda `context` argument is a plain object with
  attributes (`context.aws_request_id`), not a dict — easy to trip over since the `event`
  argument sitting right next to it *is* a plain dict.
- Same two Floci quirks as `hello-api`: `apiUrl` isn't directly resolvable (path-based route
  instead), and a second `pulumi up` with zero code changes reports a no-op `[diff:
  -environment]` on the Lambda — confirmed here too, so it's a general Floci Lambda-emulator
  behavior, not something specific to a Java-runtime function.

## Running it

```
./deploy.sh    # provision the Lambda + API Gateway, curl it
./destroy.sh   # tear both down
```

No fixed local port — invoked through Floci's API Gateway emulation, same as `hello-api`.
