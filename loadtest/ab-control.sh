#!/usr/bin/env bash
# Interleaved A/B of two backend images under identical conditions:
#
#   loadtest/ab-control.sh fitnesshub-backend:baseline fitnesshub-backend:indexed "50 100" 2
#
# For each round, each image gets a freshly seeded database, a restart and a
# warm-up, then the read mix at each level. Alternating A, B, A, B... means a
# drift in the machine (thermal state, power mode, background load) affects
# both sides equally instead of masquerading as a code change.
set -euo pipefail
A="$1"; B="$2"; LEVELS="${3:-50 100}"; ROUNDS="${4:-2}"
HERE="$(cd "$(dirname "$0")" && pwd)"
compose() { docker compose -f "$HERE/docker-compose.loadtest.yml" "$@"; }
ORIGINAL="$(docker image inspect fitnesshub-loadtest-backend:latest --format '{{.Id}}')"
restore() { docker tag "$ORIGINAL" fitnesshub-loadtest-backend:latest; }
trap restore EXIT

for round in $(seq 1 "$ROUNDS"); do
  for image in "$A" "$B"; do
    label="ab-$(echo "$image" | tr ':/' '--')-r$round"
    echo "=== round $round: $image ==="
    docker tag "$image" fitnesshub-loadtest-backend:latest
    compose down -v >/dev/null 2>&1
    compose up -d --no-build postgres redis backend >/dev/null 2>&1
    "$HERE/wait-ready.sh" >/dev/null
    "$HERE/run-matrix.sh" "$label" "$LEVELS" | grep -v "^ " || true
  done
done
