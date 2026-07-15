#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

docker rm -f polyglot-sandbox-jenkins >/dev/null 2>&1 || true
docker volume rm polyglot-sandbox-jenkins-home >/dev/null 2>&1 || true
echo "Jenkins container and volume removed."
