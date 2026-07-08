#!/usr/bin/env bash
# Stop and remove the container/image via Pulumi.
set -euo pipefail
cd "$(dirname "$0")"

export PULUMI_BACKEND_URL="file://$(pwd)/infra/.pulumi-state"
export PULUMI_CONFIG_PASSPHRASE=""

pulumi -C infra destroy --yes
