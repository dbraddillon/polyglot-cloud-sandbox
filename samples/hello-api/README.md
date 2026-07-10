# hello-api

The smallest possible slice through the AWS-shaped path this repo demonstrates: a Lambda behind
an HTTP API Gateway, returning a JSON greeting. No business logic, no persistence, nothing to
model — the point is the shape of the pipeline (Java service → Java Pulumi program → Floci), not
the feature. Kept deliberately neutral, unlike the rest of the samples' light health-insurance
theme — this is the quickstart template new samples get copied from, so it stays generic on
purpose.

## Endpoint

| Method | Path            | Notes                                              |
|--------|-----------------|-------------------------------------------------------|
| GET    | `/hello?name=`  | `name` optional, defaults to `"world"` — returns a JSON greeting with a timestamp and request id |

## Structure

```
app/    Handler.java - the whole Lambda, implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>
infra/  Pulumi program: IAM role, the Lambda function, an HTTP API (Api/Integration/Route/Stage),
        and the permission letting API Gateway invoke it
```

## Why Floci

Lambda and API Gateway are AWS-specific concepts — there's no "just run it locally" for a Lambda
the way there is for a plain Spring Boot app (see `task-api` for that contrast). This is the one
sample where Floci is doing exactly what it's for, with no caveats or workarounds needed.

## A Floci gotcha worth knowing

The Pulumi program's `apiUrl` output renders as a real-looking
`https://<api-id>.execute-api.<region>.amazonaws.com/hello` hostname, but Floci doesn't do
subdomain-based API Gateway routing — that URL isn't directly reachable. The actual local invoke
route is path-based instead:
`http://localhost:4566/restapis/<api-id>/$default/_user_request_/hello`. `deploy.sh` derives this
from the Pulumi output and curls it directly, rather than the (non-working) `apiUrl` value.

## A harmless, unfixed quirk

`pulumi up` reports a perpetual no-op diff on `aws:lambda:Function` (`-environment`) on every
run, even though nothing in this program sets an `Environment` block. Floci's Lambda emulator
appears to always report one back regardless. Doesn't affect the deploy; just noise to expect.

## Running it

```
./deploy.sh    # build the jar, provision the Lambda + API Gateway, curl it
./destroy.sh   # tear both down
```

No fixed local port — unlike the containerized samples, this one's invoked through Floci's API
Gateway emulation at the path above, not a directly-run process.
