#!/usr/bin/env bash
# Lighthouse for two frontend images against the same backend and database,
# so the frontend is the only variable:
#
#   loadtest/lighthouse/compare.sh fitnesshub-frontend:baseline fitnesshub-frontend:after
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
COMPOSE="$HERE/../docker-compose.loadtest.yml"
ORIGINAL="$(docker image inspect fitnesshub-loadtest-frontend:latest --format '{{.Id}}')"
trap 'docker tag "$ORIGINAL" fitnesshub-loadtest-frontend:latest' EXIT
"$HERE/../wait-ready.sh" >/dev/null
for image in "$@"; do
  label="$(echo "${image##*:}")"
  docker tag "$image" fitnesshub-loadtest-frontend:latest
  docker compose -f "$COMPOSE" --profile ui up -d --no-deps --no-build --force-recreate frontend >/dev/null 2>&1
  for _ in $(seq 1 30); do curl -fsS -o /dev/null http://localhost:13000/login 2>/dev/null && break; sleep 1; done
  rm -rf "$HERE/../results/lighthouse/$label"
  node "$HERE/run.mjs" "$label" > "$HERE/../results/lighthouse-$label.md"
  echo "== $image"; cat "$HERE/../results/lighthouse-$label.md"
done
