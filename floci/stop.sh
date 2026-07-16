#!/usr/bin/env bash
set -euo pipefail

floci stop || true

if [ "${1:-}" = "--all" ]; then
  echo "Stopping Colima VM too..."
  colima stop
fi
