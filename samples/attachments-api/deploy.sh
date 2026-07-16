#!/usr/bin/env bash
# Provision an S3 bucket via Floci, then run the Spring Boot app directly against it.
set -euo pipefail
cd "$(dirname "$0")"

# floci status always exits 0 regardless of whether the container is actually reachable - the
# -o json "reachable" field is the only reliable signal; confirmed directly after this silently
# skipped starting Floci on a stopped container and Pulumi failed downstream with a confusing
# "unable to validate AWS credentials" error instead.
if ! floci status -o json 2>/dev/null | grep -q '"reachable" : true'; then
  echo "Floci not running, starting it..."
  "../../floci/start.sh"
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
    # Apple Silicon Homebrew prefix.
    JAVA_BIN=/opt/homebrew/opt/openjdk@21/bin/java
  elif [ -x /usr/local/opt/openjdk@21/bin/java ]; then
    # Intel Mac Homebrew prefix - easy to forget since Apple Silicon is the common case now,
    # but /usr/local is still where Homebrew installs on x86_64 Macs.
    JAVA_BIN=/usr/local/opt/openjdk@21/bin/java
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
export AWS_DEFAULT_REGION="us-east-1"
export AWS_REGION="us-east-1"
export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Provisioning the S3 bucket with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

BUCKET_NAME=$(pulumi -C infra stack output bucketName)

echo "Starting the Spring Boot app..."
ATTACHMENTS_BUCKET_NAME="$BUCKET_NAME" "$JAVA_BIN" -jar app/target/app.jar > .run/app.log 2>&1 &
echo $! > .run/app.pid

echo "Waiting for the service to come up..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8088/attachments >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "Uploading an attachment:"
echo "a demo claim attachment" > .run/demo-attachment.txt
UPLOADED=$(curl -s -X POST http://localhost:8088/attachments -F "file=@.run/demo-attachment.txt;type=text/plain")
echo "$UPLOADED"
KEY=$(echo "$UPLOADED" | python3 -c "import json,sys; print(json.load(sys.stdin)['key'])")

echo
echo "Listing attachments:"
curl -s http://localhost:8088/attachments
echo

echo
echo "Downloading it back:"
curl -s -D - -o .run/downloaded-attachment.txt "http://localhost:8088/attachments/${KEY}"
echo "Downloaded content:"
cat .run/downloaded-attachment.txt

echo
echo "Deleting it:"
curl -s -w " [%{http_code}]\n" -X DELETE "http://localhost:8088/attachments/${KEY}"
