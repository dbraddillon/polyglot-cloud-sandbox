#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .run/app.pid ]; then
  kill "$(cat .run/app.pid)" 2>/dev/null || true
  rm -f .run/app.pid
fi

export AWS_ENDPOINT_URL="http://localhost:4566"
export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="us-east-1"
export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

pulumi -C infra destroy --yes
