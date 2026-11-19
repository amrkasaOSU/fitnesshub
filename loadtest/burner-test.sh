#!/usr/bin/env bash
# Tests whether low-load latency depends on how busy the CPU already is.
# Runs the read mix at 50 VUs for one image, first as-is, then with one
# container spinning a single core inside the same VM. If an idle CPU is what
# makes cheap requests slow, they get *faster* when something else keeps a
# core awake.
#
#   loadtest/burner-test.sh fitnesshub-backend:indexed
set -euo pipefail
IMAGE="$1"
HERE="$(cd "$(dirname "$0")" && pwd)"
compose() { docker compose -f "$HERE/docker-compose.loadtest.yml" "$@"; }
ORIGINAL="$(docker image inspect fitnesshub-loadtest-backend:latest --format '{{.Id}}')"
cleanup() { docker rm -f fh-cpu-burner >/dev/null 2>&1 || true; docker tag "$ORIGINAL" fitnesshub-loadtest-backend:latest; }
trap cleanup EXIT
tag="$(echo "$IMAGE" | tr ':/' '--')"
docker tag "$IMAGE" fitnesshub-loadtest-backend:latest
for mode in idle busy; do
  compose down -v >/dev/null 2>&1
  compose up -d --no-build postgres redis backend >/dev/null 2>&1
  "$HERE/wait-ready.sh" >/dev/null
  if [ "$mode" = busy ]; then
    docker run -d --rm --name fh-cpu-burner --cpus 1 alpine:3 sh -c 'while :; do :; done' >/dev/null
  fi
  echo "=== $IMAGE, other cores $mode ==="
  "$HERE/run-matrix.sh" "burner-$tag-$mode" "50" | grep -v "^ " || true
  docker rm -f fh-cpu-burner >/dev/null 2>&1 || true
done
