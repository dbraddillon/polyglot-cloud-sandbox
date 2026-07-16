#!/usr/bin/env bash
# Deploy a Python Lambda behind an HTTP API Gateway - same Java/Pulumi infra shape as hello-api,
# proving the IaC doesn't care what language the workload is written in.
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

mkdir -p infra/.pulumi-state

export AWS_ENDPOINT_URL="http://localhost:4566"
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="us-east-1"
export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

echo "Deploying with Pulumi (zips app/ directly - Python is interpreted, no build step)..."
pulumi -C infra stack select dev --create
pulumi -C infra up --yes

API_URL=$(pulumi -C infra stack output apiUrl)
API_ID=$(echo "$API_URL" | sed -E 's#https://([^.]+)\..*#\1#')
LOCAL_URL="http://localhost:4566/restapis/${API_ID}/\$default/_user_request_/hello"

echo
echo "Pulumi output apiUrl (not resolvable locally): ${API_URL}"
echo "Local invoke URL:                              ${LOCAL_URL}"
echo
echo "Response:"
curl -s "${LOCAL_URL}?name=Python"
echo
