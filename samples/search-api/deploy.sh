#!/usr/bin/env bash
# Run OpenSearch as a plain container via Pulumi's Docker provider, then run the Spring Boot
# app directly against it. No Floci here - see the infra README for why.
set -euo pipefail
cd "$(dirname "$0")"

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

export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Starting OpenSearch with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

BASE_URL=$(pulumi -C infra stack output baseUrl)

echo "Waiting for OpenSearch to accept requests..."
for i in $(seq 1 60); do
  if curl -sf "$BASE_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "Starting the Spring Boot app..."
OPENSEARCH_BASE_URL="$BASE_URL" "$JAVA_BIN" -jar app/target/app.jar > .run/app.log 2>&1 &
echo $! > .run/app.pid

echo "Waiting for the service to come up..."
for i in $(seq 1 30); do
  if curl -sf "http://localhost:8081/documents/search?q=x" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "Indexing a document:"
curl -s -X POST http://localhost:8081/documents -H "Content-Type: application/json" \
  -d '{"title":"Managing Type 2 Diabetes","body":"Guidance on diet, medication, and monitoring blood sugar"}'
echo
sleep 1
echo "Searching for it:"
curl -s "http://localhost:8081/documents/search?q=diabetes"
echo
