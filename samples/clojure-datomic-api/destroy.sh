#!/usr/bin/env bash
# Stop the running service. No containers, no Floci resources, no persisted Datomic storage to
# clean up - :mem storage means the database itself vanishes with the JVM process.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .run/app.pid ]; then
  kill "$(cat .run/app.pid)" 2>/dev/null || true
  rm -f .run/app.pid
fi
