#!/usr/bin/env bash
# Provision an SNS topic fanning out to an SQS queue via Floci, then run the Spring Boot app
# (a publisher endpoint + a background poller consuming from the queue) directly against it.
set -euo pipefail
cd "$(dirname "$0")"

if ! floci status >/dev/null 2>&1; then
  echo "Floci not running, starting it..."
  "$HOME/floci-sandbox/start.sh"
fi

echo "Building the jar..."
mvn -q -f app/pom.xml -DskipTests clean package

mkdir -p infra/.pulumi-state .run

# Plain `java` on macOS can resolve to Apple's stub (which just prints an "install Java"
# message) if a Homebrew-installed JDK isn't linked as the system default - Maven finds a real
# JDK through its own resolution, so `mvn` works fine even when bare `java` doesn't.
JAVA_BIN="java"
if ! java -version >/dev/null 2>&1; then
  if [ -x /opt/homebrew/opt/openjdk@21/bin/java ]; then
    JAVA_BIN=/opt/homebrew/opt/openjdk@21/bin/java
  elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  else
    echo "No working 'java' found. Install a JDK 21 (e.g. 'brew install openjdk@21') or set JAVA_HOME." >&2
    exit 1
  fi
fi

export AWS_ENDPOINT_URL="http://localhost:4566"
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
# Both region vars: the AWS CLI/Pulumi read AWS_DEFAULT_REGION, the AWS SDK for Java v2 wants
# AWS_REGION specifically (see catalog-api's README for how this one was found).
export AWS_DEFAULT_REGION="us-east-1"
export AWS_REGION="us-east-1"
export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Provisioning the SNS topic + SQS queue with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

TOPIC_ARN=$(pulumi -C infra stack output topicArn)
QUEUE_URL=$(pulumi -C infra stack output queueUrl)

echo "Starting the Spring Boot app..."
EVENTS_TOPIC_ARN="$TOPIC_ARN" EVENTS_QUEUE_URL="$QUEUE_URL" "$JAVA_BIN" -jar app/target/app.jar > .run/app.log 2>&1 &
echo $! > .run/app.pid

echo "Waiting for the service to come up..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8084/events >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "Publishing an event:"
PUBLISHED=$(curl -s -X POST http://localhost:8084/events -H "Content-Type: application/json" \
  -d '{"type":"claim.submitted","payload":"claim-123"}')
echo "$PUBLISHED"
EVENT_ID=$(echo "$PUBLISHED" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo
echo "Polling for it to be consumed (the poller runs on a ~1-3s cycle, this can take a few seconds)..."
for i in $(seq 1 20); do
  if curl -sf "http://localhost:8084/events/${EVENT_ID}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -s -w " [%{http_code}]\n" "http://localhost:8084/events/${EVENT_ID}"
