#!/usr/bin/env bash
# Install deps + run the test suite, then build+run the container locally via Pulumi's Docker
# provider. No Floci/AWS emulation here - same reasoning as task-api.
set -euo pipefail
cd "$(dirname "$0")"

echo "Installing dependencies and running tests..."
npm --prefix app install
npm --prefix app test

mkdir -p infra/.pulumi-state

export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Building the image and starting the container with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

echo
echo "Waiting for the service to come up..."
for i in $(seq 1 20); do
  if curl -sf http://localhost:8086/notices >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "node-api running at http://localhost:8086/notices"
echo
echo "Creating a notice:"
CREATED=$(curl -s -X POST http://localhost:8086/notices -H "Content-Type: application/json" \
  -d '{"message":"Annual wellness visit reminder"}')
echo "$CREATED"
NOTICE_ID=$(echo "$CREATED" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo
echo "Sending it:"
curl -s -X PATCH "http://localhost:8086/notices/${NOTICE_ID}/send"
echo

echo
echo "Listing notices:"
curl -s http://localhost:8086/notices
echo
