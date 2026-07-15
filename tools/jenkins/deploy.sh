#!/usr/bin/env bash
# Builds and runs a local Jenkins with a real pipeline job pre-seeded via Configuration as Code
# (JCasC) + Job DSL - no setup wizard, no manual job creation. The job clones the actual public
# polyglot-cloud-sandbox GitHub repo and runs task-api's JUnit suite, the same as a real CI
# pipeline would. This is a local tool setup, not a "sample" - there's no app being built here,
# just Jenkins itself exercising one of this repo's existing samples.
set -euo pipefail
cd "$(dirname "$0")"

CONTAINER_NAME="polyglot-sandbox-jenkins"
VOLUME_NAME="polyglot-sandbox-jenkins-home"
ADMIN_PASSWORD="${JENKINS_ADMIN_PASSWORD:-sandbox-admin}"

docker build -t polyglot-sandbox-jenkins:local .

docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker volume rm "$VOLUME_NAME" >/dev/null 2>&1 || true
docker volume create "$VOLUME_NAME" >/dev/null

docker run -d --name "$CONTAINER_NAME" \
  -p 8080:8080 \
  -v "$VOLUME_NAME":/var/jenkins_home \
  -e JENKINS_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  polyglot-sandbox-jenkins:local >/dev/null

echo "Waiting for Jenkins to come up..."
until curl -sf http://localhost:8080/login >/dev/null 2>&1; do sleep 2; done

echo "Waiting for the JCasC-seeded task-api-ci job to register..."
until curl -sf -u "admin:$ADMIN_PASSWORD" http://localhost:8080/job/task-api-ci/api/json >/dev/null 2>&1; do
  sleep 2
done

echo "Jenkins running at http://localhost:8080 (user: admin / password: $ADMIN_PASSWORD)"
echo "Triggering task-api-ci build..."
curl -s -u "admin:$ADMIN_PASSWORD" -X POST http://localhost:8080/job/task-api-ci/build >/dev/null

# Jenkins queues a build before it gets a real build number - poll the queue first, then the job.
echo "Waiting for the build to start..."
BUILD_URL=""
for _ in $(seq 1 30); do
  LAST_BUILD=$(curl -s -u "admin:$ADMIN_PASSWORD" \
    "http://localhost:8080/job/task-api-ci/lastBuild/api/json" 2>/dev/null || true)
  if [ -n "$LAST_BUILD" ] && echo "$LAST_BUILD" | grep -q '"number"'; then
    BUILD_URL="http://localhost:8080/job/task-api-ci/lastBuild"
    break
  fi
  sleep 2
done

if [ -z "$BUILD_URL" ]; then
  echo "Build never started - check http://localhost:8080/job/task-api-ci/ manually." >&2
  exit 1
fi

echo "Waiting for the build to finish..."
for _ in $(seq 1 60); do
  BUILDING=$(curl -s -u "admin:$ADMIN_PASSWORD" "$BUILD_URL/api/json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["building"])')
  [ "$BUILDING" = "False" ] && break
  sleep 3
done

RESULT=$(curl -s -u "admin:$ADMIN_PASSWORD" "$BUILD_URL/api/json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["result"])')
echo "Build result: $RESULT"
echo "Console log: $BUILD_URL/consoleText"
