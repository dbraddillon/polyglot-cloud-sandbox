#!/usr/bin/env bash
# Build the app image and stand up both containers (Postgres + the app) via Pulumi's Docker
# provider. No Floci here - this is the plain-container story, same as task-api.
set -euo pipefail
cd "$(dirname "$0")"

echo "Building the jar..."
mvn -q -f app/pom.xml -DskipTests clean package

mkdir -p infra/.pulumi-state

export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Building the image and starting both containers with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

echo
echo "Waiting for the service to come up (Postgres takes a few seconds; the app may crash and"
echo "restart once if it beats Postgres to ready - see the infra README for why)..."
for i in $(seq 1 60); do
  if curl -sf http://localhost:8083/orders >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "Creating an order:"
ORDER=$(curl -s -X POST http://localhost:8083/orders -H "Content-Type: application/json" -d '{
  "customerName": "Ada Lovelace",
  "lineItems": [
    {"productName": "Mechanical keyboard", "quantity": 1, "unitPrice": 89.99},
    {"productName": "USB-C cable", "quantity": 2, "unitPrice": 7.50}
  ]
}')
echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo
echo "Marking it paid:"
curl -s -X POST "http://localhost:8083/orders/${ORDER_ID}/pay"
echo
