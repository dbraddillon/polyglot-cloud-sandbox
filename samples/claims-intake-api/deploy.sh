#!/usr/bin/env bash
# Provision a Kinesis stream via Floci, then run the Spring Boot app (a CSV batch-intake
# endpoint that picks between synchronous streaming and a Kinesis producer/consumer pipeline
# based on upload size) and exercise both branches of that decision.
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

echo "Provisioning the Kinesis stream with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

STREAM_NAME=$(pulumi -C infra stack output streamName)

echo "Generating demo CSVs (one under the streaming threshold, one over it)..."
python3 - <<'PYEOF'
import csv
import datetime
import random

random.seed(42)

descriptions = [
    "Annual wellness visit", "Physical therapy session", "Lab panel - metabolic",
    "Specialist consultation", "Urgent care visit", "Prescription refill",
    "Imaging - X-ray", "Preventive screening", "Telehealth visit", "Emergency room visit",
]


def valid_row(claim_num):
    service_date = datetime.date.today() - datetime.timedelta(days=random.randint(0, 90))
    return [
        f"CLM-{claim_num:06d}",
        f"MBR-{random.randint(1, 200):04d}",
        service_date.isoformat(),
        random.choice(descriptions),
        f"{random.uniform(25, 1200):.2f}",
    ]


def write_batch(path, valid_count, invalid_rows):
    rows = [valid_row(i) for i in range(1, valid_count + 1)]
    for pos, bad_row in invalid_rows:
        rows.insert(min(pos, len(rows)), bad_row)
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["claimId", "memberId", "serviceDate", "serviceDescription", "billedAmount"])
        w.writerows(rows)


# Well under the 102400-byte default threshold -> StreamingBatchProcessor.
write_batch(".run/small.csv", 15, [
    (5, ["CLM-BAD01", "", "2026-01-01", "Missing member id", "50.00"]),
])

# Comfortably over it -> KinesisBatchProducer/KinesisBatchConsumer. Kept to a few hundred rows,
# not the tens of thousands a "large CSV" would suggest - see the README on Floci's Kinesis
# PutRecords emulation costing real per-record time, which makes a bigger demo file take minutes
# rather than seconds without demonstrating anything the row count alone doesn't already show.
write_batch(".run/large.csv", 400, [
    (10, ["CLM-BAD02", "MBR-0001", "2026-01-01", "Negative amount", "-10.00"]),
    (150, ["CLM-BAD03", "MBR-0002", "not-a-date", "Malformed date", "75.00"]),
    (300, ["CLM-BAD04", "MBR-0003", "2026-01-01", "Amount too large", "999999.00"]),
])
PYEOF

SMALL_SIZE=$(wc -c < .run/small.csv | tr -d ' ')
LARGE_SIZE=$(wc -c < .run/large.csv | tr -d ' ')
echo "small.csv: ${SMALL_SIZE} bytes, large.csv: ${LARGE_SIZE} bytes (streaming-threshold-bytes default: 20000)"

echo "Starting the Spring Boot app..."
CLAIMS_INTAKE_STREAM_NAME="$STREAM_NAME" "$JAVA_BIN" -jar app/target/app.jar > .run/app.log 2>&1 &
echo $! > .run/app.pid

echo "Waiting for the service to come up..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8085/claims-intake >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "=== Streaming mode: small.csv (under the threshold) ==="
STREAMED=$(curl -s -X POST http://localhost:8085/claims-intake -F "file=@.run/small.csv")
echo "$STREAMED"

echo
echo "=== Queue mode: large.csv (over the threshold) ==="
QUEUED=$(curl -s -X POST http://localhost:8085/claims-intake -F "file=@.run/large.csv")
echo "$QUEUED"
BATCH_ID=$(echo "$QUEUED" | python3 -c "import json,sys; print(json.load(sys.stdin)['batchId'])")

echo
echo "Polling until the Kinesis consumer finishes writing it out (this can take a few seconds)..."
for i in $(seq 1 60); do
  STATUS=$(curl -s "http://localhost:8085/claims-intake/${BATCH_ID}" | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "")
  if [ "$STATUS" = "COMPLETE" ]; then
    break
  fi
  sleep 1
done
curl -s -w " [%{http_code}]\n" "http://localhost:8085/claims-intake/${BATCH_ID}"
echo
echo "Output files:"
ls -la .run/output/
