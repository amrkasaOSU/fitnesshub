#!/usr/bin/env bash
# Runs the read mix at each concurrency level and stores one k6 summary per
# level under loadtest/results/<label>/.
#
#   loadtest/run-matrix.sh baseline
#   loadtest/run-matrix.sh after-nplus1 "50 100 250 500"
#
# The stack is restarted between levels so a run that saturated the server
# can't leave queued work behind to distort the next one.
set -euo pipefail

LABEL="${1:?usage: run-matrix.sh <label> [\"vus levels\"]}"
LEVELS="${2:-50 100 250 500}"
DURATION="${DURATION:-60s}"
HERE="$(cd "$(dirname "$0")" && pwd)"
compose() { docker compose -f "$HERE/docker-compose.loadtest.yml" "$@"; }
OUT="$HERE/results/$LABEL"
mkdir -p "$OUT"

wait_healthy() {
  for _ in $(seq 1 90); do
    if curl -fsS http://localhost:18080/actuator/health >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "backend never became healthy" >&2
  exit 1
}

for VUS in $LEVELS; do
  echo "=== $LABEL: $VUS VUs for $DURATION ==="
  compose restart backend >/dev/null 2>&1
  wait_healthy
  # One short pass so JIT warm-up isn't billed to the measured run.
  k6 run --quiet -e VUS=10 -e DURATION=10s "$HERE/k6/read-mix.js" >/dev/null 2>&1 || true
  k6 run --quiet -e VUS="$VUS" -e DURATION="$DURATION" \
    --summary-export "$OUT/vus-$VUS.json" \
    "$HERE/k6/read-mix.js" > "$OUT/vus-$VUS.txt" 2>&1 || true
  tail -n 4 "$OUT/vus-$VUS.txt" | head -n 2 || true
done
echo "summaries written to $OUT"
