#!/usr/bin/env bash
# Provision a DynamoDB table via Floci, then run the Spring Boot app directly against it.
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
# Both region vars, not just one: the AWS CLI and Pulumi's provider read AWS_DEFAULT_REGION,
# but the AWS SDK for Java v2's default region provider chain specifically wants AWS_REGION -
# without it, DynamoDbClient.create() fails to build at Spring Boot startup even though the
# CLI/Pulumi steps above worked fine with only AWS_DEFAULT_REGION set.
export AWS_DEFAULT_REGION="us-east-1"
export AWS_REGION="us-east-1"
export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Provisioning the DynamoDB table with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

TABLE_NAME=$(pulumi -C infra stack output tableName)

echo "Starting the Spring Boot app..."
CATALOG_TABLE_NAME="$TABLE_NAME" "$JAVA_BIN" -jar app/target/app.jar > .run/app.log 2>&1 &
echo $! > .run/app.pid

echo "Waiting for the service to come up..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8082/products >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "Creating a product:"
curl -s -X POST http://localhost:8082/products -H "Content-Type: application/json" -d '{"name":"Keyboard","price":49.99}'
echo
echo "Listing products:"
curl -s http://localhost:8082/products
echo
