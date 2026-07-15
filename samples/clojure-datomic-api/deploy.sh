#!/usr/bin/env bash
# Run the test suite, then run the Clojure/Datomic service directly (no container, no Floci -
# see the README on why this sample needs neither). Exercises create/list/search/get.
set -euo pipefail
cd "$(dirname "$0")"

# The Clojure CLI isn't always on PATH by default (this sandbox's own install went to
# ~/.local/bin specifically to sidestep a stale-Xcode-Command-Line-Tools issue with Homebrew's
# clojure formula - see the README). Same class of fallback as the "Apple's stub java" checks
# elsewhere in this repo: try the plain command first, fall back to a known install location.
CLOJURE_BIN="clojure"
if ! command -v clojure >/dev/null 2>&1; then
  if [ -x "$HOME/.local/bin/clojure" ]; then
    CLOJURE_BIN="$HOME/.local/bin/clojure"
  else
    echo "No working 'clojure' found. Install the Clojure CLI (https://clojure.org/guides/install_clojure) or set it up per this sample's README." >&2
    exit 1
  fi
fi

mkdir -p .run

echo "Running the test suite..."
(cd app && "$CLOJURE_BIN" -M:test)

echo
echo "Starting the service..."
(cd app && "$CLOJURE_BIN" -M -m dev.sandbox.lab.clojure-datomic-api.core) > .run/app.log 2>&1 &
echo $! > .run/app.pid

echo "Waiting for the service to come up..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:8087/providers >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo
echo "Creating providers:"
FIRST=$(curl -s -X POST http://localhost:8087/providers -H "Content-Type: application/json" \
  -d '{"name":"Dr. Alvarez","specialty":"Cardiology"}')
echo "$FIRST"
curl -s -X POST http://localhost:8087/providers -H "Content-Type: application/json" \
  -d '{"name":"Dr. Chen","specialty":"Cardiology"}' >/dev/null
curl -s -X POST http://localhost:8087/providers -H "Content-Type: application/json" \
  -d '{"name":"Dr. Osei","specialty":"Dermatology"}' >/dev/null
PROVIDER_ID=$(echo "$FIRST" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo
echo "Getting one by id:"
curl -s -w " [%{http_code}]\n" "http://localhost:8087/providers/${PROVIDER_ID}"

echo
echo "Listing all:"
curl -s http://localhost:8087/providers
echo

echo
echo "Searching by specialty (Datalog query, not a full scan):"
curl -s "http://localhost:8087/providers/search?specialty=Cardiology"
echo
