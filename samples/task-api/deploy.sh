#!/usr/bin/env bash
# Package the Spring Boot service, then build+run its container locally via Pulumi's Docker
# provider. No Floci/AWS emulation involved here - see the README for why.
set -euo pipefail
cd "$(dirname "$0")"

echo "Building the jar..."
mvn -q -f app/pom.xml -DskipTests clean package

mkdir -p infra/.pulumi-state

export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Building the image and starting the container with Pulumi..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

echo
echo "Waiting for the service to come up..."
for i in $(seq 1 20); do
  if curl -sf http://localhost:8080/tasks >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "Task API running at http://localhost:8080/tasks"
echo
echo "Creating a task:"
curl -s -X POST http://localhost:8080/tasks -H "Content-Type: application/json" -d '{"title":"try the sample"}'
echo
echo "Listing tasks:"
curl -s http://localhost:8080/tasks
echo
