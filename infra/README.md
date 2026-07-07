# infra

Pulumi program (Java) that provisions the sandbox's Lambda + HTTP API Gateway.

This is deployed against [Floci](https://floci.io) — a local AWS emulator — not real AWS.
See the root `CLAUDE.md` for the full picture (build, deploy, gotchas). Use `../deploy.sh`
and `../destroy.sh` rather than running `pulumi` directly; they set the env vars this
project depends on (local state backend, Floci endpoint, fake credentials).

## Resources
- `aws.iam.Role` / `aws.iam.RolePolicyAttachment` — Lambda execution role
- `aws.lambda.Function` — runs `../app/target/app.jar` (built by `app`'s Maven module)
- `aws.apigatewayv2.Api` / `Integration` / `Route` / `Stage` — HTTP API, `GET /hello`
- `aws.lambda.Permission` — lets API Gateway invoke the function

## Output
- `apiUrl` — looks like a real AWS URL but is **not directly resolvable against Floci**.
  Floci doesn't do subdomain-based API Gateway routing. `deploy.sh` derives the working
  local invoke URL and curls it for you; see root `CLAUDE.md` for the URL pattern.
