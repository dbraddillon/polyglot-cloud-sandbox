# JFrog Artifactory (local artifact repository)

A local JFrog Artifactory OSS + Postgres, not a "sample" — like `tools/jenkins/`, this exercises
an existing sample (`task-api`) rather than being one itself. `deploy.sh` builds task-api's real
jar, publishes it to Artifactory via `mvn deploy:deploy-file`, then pulls it back down and
compares SHA-256 against the locally built jar to prove the round-trip is genuine, not just "the
HTTP calls didn't error."

```
./deploy.sh    # postgres + artifactory, wait for boot, build+publish+verify task-api's jar
./destroy.sh   # remove both containers, the network, and all local state
```

Artifactory runs at `http://localhost:8082/artifactory` (user `admin`, password `password` — see
below for why that's hardcoded rather than configurable).

## Gotchas found building this

- **Artifactory OSS (current release, 7.111.x) will not start on its own embedded Derby
  database at all anymore.** First boot attempt without a real database failed outright:
  `IllegalStateException: Cannot start the application with a database other than PostgreSQL`.
  This isn't a "Derby is deprecated but still limps along" situation - it's a hard startup
  failure. `deploy.sh` runs a real `postgres:16` container on a shared Docker network
  (`polyglot-sandbox-artifactory-net`) and points Artifactory at it via
  `JF_SHARED_DATABASE_*` env vars, same "container-name DNS via a user-defined network" pattern
  as `claims-api`'s Postgres.
- **Artifactory refuses to boot at all without a pre-generated master key.** First boot without
  one failed with `Failed resolving MasterKey key; Missing MasterKey key`. `deploy.sh` generates
  one fresh each run (`openssl rand -hex 16`) into `var/etc/security/master.key` before starting
  the container - matching this tool's "destroy.sh wipes everything, deploy.sh starts clean"
  approach (no stale, mismatched key living between runs).
- **A named/bind-mounted directory only gets seeded from the image's baked-in content if it's
  genuinely empty on first use.** Combined with the master-key requirement, this is why
  `deploy.sh` does a full `rm -rf var` before each run rather than trying to reuse state between
  deploys.
- **OSS's repository-management REST API is Pro-gated, not just missing features.** Every
  attempt to create or reconfigure a repo via `PUT`/`POST /api/repositories/<key>` - and even the
  legacy full-config-import endpoint - 400'd with `"This REST API is available only in
  Artifactory Pro"`. Confirmed this isn't a usage mistake: the identical call against the
  pre-existing default repo (`example-repo-local`) to *read* its config works fine, only
  create/update is blocked. Rather than fight a wall that has no code-side fix in the OSS tier,
  `deploy.sh` publishes to `example-repo-local` as-is. It's typed "generic" instead of "maven" in
  Artifactory's own UI/metadata sense, but that only affects browsing/search niceties - a real
  Maven-layout `PUT` (what `mvn deploy` actually does on the wire) is accepted and served back
  identically either way, which is exactly what this tool verifies end to end.
- **The default admin password (`password`) is fixed, not something `deploy.sh` generates.**
  Unlike `tools/jenkins/`'s `JENKINS_ADMIN_PASSWORD`, Artifactory OSS doesn't expose a documented
  way to set the initial admin password at container boot the way Jenkins' JCasC does - it ships
  with a known default and expects the (Pro-gated, UI-only) onboarding flow to change it. Left as
  the shipped default since this is a local, throwaway, never-exposed sandbox instance; not a
  setting to carry into anything real.
