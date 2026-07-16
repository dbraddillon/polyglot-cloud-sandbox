#!/usr/bin/env bash
# Starts (or confirms already-running) the Colima VM and the Floci container on it, with
# persisted emulator state so tables/streams/buckets survive between deploy.sh runs. Every
# Floci-based sample's deploy.sh calls this - see root CLAUDE.md's "Floci's container lifecycle
# is machine-wide, not per-sample" note for why this lives here once instead of copy-pasted into
# every sample.
#
# macOS + Colima specifically (see root CLAUDE.md's Prerequisites for why: Colima doesn't run on
# Windows at all, and this repo hasn't verified Rancher Desktop/Podman as alternatives here yet).
set -euo pipefail

if ! colima status >/dev/null 2>&1; then
  echo "Starting Colima VM..."
  colima start --cpu 4 --memory 8 --disk 60 --vm-type vz --mount-type virtiofs
fi

if [ ! -L /var/run/docker.sock ]; then
  echo "Linking Colima's docker socket to /var/run/docker.sock (admin prompt)..."
  osascript -e 'do shell script "ln -sf '"$HOME"'/.colima/default/docker.sock /var/run/docker.sock" with administrator privileges'
fi

echo "Starting Floci..."
floci start --persist "$HOME/floci-sandbox/data"

echo
floci status
echo
echo "AWS CLI is ready:  aws --profile floci s3 ls"
echo "Or export for this shell:  export AWS_PROFILE=floci"
