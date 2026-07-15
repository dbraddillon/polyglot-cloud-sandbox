# Jenkins (local CI)

A local Jenkins, not a "sample" — there's no app being built here, just Jenkins itself standing
up and running a real pipeline against one of this repo's existing samples (`task-api`). This is
one of the manager-provided tool-list items that's a dev-environment/tooling setup rather than a
new deploy-shape pattern, so it lives under `tools/` instead of `samples/`.

## What it does

`deploy.sh` builds a custom Jenkins image (`jenkins/jenkins:lts-jdk21` + Maven), runs it, and
triggers a build of a pre-seeded pipeline job (`task-api-ci`) that:

1. Clones the real, public `polyglot-cloud-sandbox` GitHub repo (not a local bind mount — this
   is meant to behave like an actual CI setup would).
2. Runs `mvn clean test` against `samples/task-api/app`.
3. Publishes the JUnit results.

No manual setup wizard, no hand-clicked job — Configuration as Code (JCasC) + the Job DSL plugin
create the admin user and seed the pipeline job at boot, so `deploy.sh` alone produces a fully
working, already-green Jenkins. Same "single command, no manual last-mile step" bar as every other
sample's `deploy.sh` in this repo.

```
./deploy.sh    # build image, run container, wait for boot, trigger + wait on a build, print the result
./destroy.sh   # remove the container and its volume
```

Jenkins runs at `http://localhost:8080` (user `admin`, password `sandbox-admin` unless
`JENKINS_ADMIN_PASSWORD` is set before running `deploy.sh`).

## Why CircleCI didn't get the same treatment

CircleCI is also on the manager's tool list, but it's a cloud-only service — there's no
meaningful local-execution story the way Jenkins-in-Docker gives you a fully real, running
instance. Jenkins covers the "CI/CD workflow" item from that list on its own; CircleCI would need
a real account to demonstrate anything beyond reading its config-file syntax.

## Gotchas found building this

- **CSRF crumb-checking rejects the REST API build trigger by default.** Jenkins requires a
  `Jenkins-Crumb` header on state-changing requests; several attempts at fetching and forwarding
  one via `/crumbIssuer/api/json` and `/crumbIssuer/api/xml` both still 403'd. Rather than keep
  chasing crumb/session semantics for a Jenkins that's local-only and never internet-exposed,
  disabled CSRF protection outright via
  `-Dhudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION=true` in
  `JAVA_OPTS`. Not a setting to carry into anything internet-facing.
- **A named Docker volume mounted over a directory that already has content in the image gets
  seeded from that content on first use** — this is what makes the baked-in `casc.yaml` actually
  show up in `/var/jenkins_home` the first time the container starts against a fresh volume.
  `destroy.sh` removes the volume (not just the container) so every `deploy.sh` run is a genuinely
  clean, reproducible boot — otherwise a second deploy would silently keep the first run's
  `JENKINS_HOME` state, admin user included.
- **The pipeline job's Jenkinsfile has to already exist on the branch it clones** — `deploy.sh`
  triggering a build before `tools/jenkins/Jenkinsfile` was pushed to GitHub failed with
  `Jenkinsfile not found`, not some Jenkins config bug. Obvious in hindsight, but worth noting:
  this whole setup only works against a real pushed commit, not local uncommitted changes, since
  the job clones the actual GitHub remote rather than a local bind mount.
