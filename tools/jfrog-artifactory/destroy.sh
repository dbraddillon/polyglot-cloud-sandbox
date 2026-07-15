#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

docker rm -f polyglot-sandbox-artifactory polyglot-sandbox-artifactory-db >/dev/null 2>&1 || true
docker network rm polyglot-sandbox-artifactory-net >/dev/null 2>&1 || true
rm -rf var
echo "Artifactory, its Postgres, and all local state removed."
