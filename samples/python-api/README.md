# python-api

The other half of `hello-api`'s point: the Java/Pulumi infra program doesn't care what language
the Lambda's actual workload is written in. Same infra shape (IAM role, Lambda, HTTP API
Gateway) provisioned by the exact same kind of Java Pulumi program — just pointed at a Python
3.12 runtime and a Python handler instead of a Java one. Confirmed against Floci directly: the
emulator genuinely runs the Python runtime, not just a stub that only understands Java.

## Endpoint

| Method | Path            | Notes                                                          |
|--------|-----------------|--------------------------------------------------------------------|
| GET    | `/hello?name=`  | `name` optional, defaults to `"world"` — same shape as `hello-api`'s response |

## Structure

```
app/    handler.py - the whole Lambda, a plain module-level function (no interface/base class
        to implement, unlike Java's Handler implements RequestHandler<In, Out>)
        requirements.txt - empty (stdlib only), present as the idiomatic spot for real deps
infra/  Pulumi program (Java) - identical shape to hello-api's: IAM role, Lambda function,
        HTTP API (Api/Integration/Route/Stage), the permission letting API Gateway invoke it
```

No `app/pom.xml`, unlike every other sample — there's nothing to compile. `FileArchive` in
`infra/.../App.java` zips the `app/` directory directly at deploy time; Python being interpreted
means there's no build step standing between "source" and "what actually runs."

## Why Floci

Lambda + API Gateway need an AWS emulator the same way `hello-api` does — this sample exists
specifically to confirm that holds true for a non-Java runtime too, not just to repeat
`hello-api`. It does: `pulumi up` provisions cleanly and the deployed function returns a real
response computed by an actual Python 3.12 interpreter running inside Floci.

## Same Floci gotchas as hello-api

- `apiUrl` renders as a real-looking `*.execute-api.*.amazonaws.com` hostname that isn't
  directly reachable — Floci doesn't do subdomain-based routing. `deploy.sh` derives the real
  path-based invoke URL: `http://localhost:4566/restapis/<api-id>/$default/_user_request_/hello`.
- The same perpetual no-op Pulumi diff on `aws:lambda:Function` (`-environment`) shows up here
  too on a second `pulumi up` with zero code changes — confirms this is a general Floci Lambda-
  emulator quirk, not something specific to a Java-runtime function.

## Running it

```
./deploy.sh    # provision the Lambda + API Gateway, curl it
./destroy.sh   # tear both down
```

No fixed local port — invoked through Floci's API Gateway emulation at the path above, same as
`hello-api`.
