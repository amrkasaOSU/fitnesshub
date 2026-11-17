#!/usr/bin/env bash
# Saves the JSON every optimised endpoint returns, so a refactor can be proven
# to change performance and nothing else:
#
#   loadtest/snapshot.sh before      # old build
#   loadtest/snapshot.sh after       # new build, same database
#   diff -r loadtest/results/snapshots/before loadtest/results/snapshots/after
#
# Both runs must hit the same database on the same UTC day - several values
# ("today", "this week") are relative to the clock.
set -euo pipefail
LABEL="${1:?usage: snapshot.sh <label>}"
BASE="${BASE_URL:-http://localhost:18080}"
OUT="$(cd "$(dirname "$0")" && pwd)/results/snapshots/$LABEL"
rm -rf "$OUT"; mkdir -p "$OUT"
JAR_DIR="$(mktemp -d)"; trap 'rm -rf "$JAR_DIR"' EXIT

login() {
  curl -fsS -c "$JAR_DIR/$1" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"FitnessHub!2024\"}" "$BASE/api/auth/login" >/dev/null
}
save() { # account name path
  curl -fsS -b "$JAR_DIR/$1" "$BASE$3" | python3 -m json.tool --sort-keys > "$OUT/$2.json"
}
id_of() { curl -fsS -b "$JAR_DIR/$1" "$BASE/api/users/me" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])'; }

login coach@fitnesshub.local
save coach@fitnesshub.local coach-dashboard /api/coach/dashboard
save coach@fitnesshub.local coach-clients /api/clients
save coach@fitnesshub.local coach-conversations /api/coach/conversations
save coach@fitnesshub.local coach-programs /api/programs

for n in 1 2 3 4 5; do
  c="client$n@fitnesshub.local"
  login "$c"
  save "$c" "client$n-today" /api/workouts/today   # first: creates today's session if absent
  save "$c" "client$n-dashboard" /api/dashboard
  save "$c" "client$n-week" /api/workouts/week
  save "$c" "client$n-history" "/api/workouts?pageSize=50"
  save "$c" "client$n-weight" /api/weight/dashboard
  save coach@fitnesshub.local "coach-client$n-workouts" "/api/clients/$(id_of "$c")/workouts?pageSize=50"
done

# Present only after loadtest/scale-seed.sql has been applied.
if curl -fsS -c "$JAR_DIR/scale" -H 'Content-Type: application/json' \
     -d '{"email":"scale-coach@fitnesshub.local","password":"FitnessHub!2024"}' \
     "$BASE/api/auth/login" >/dev/null 2>&1; then
  curl -fsS -b "$JAR_DIR/scale" "$BASE/api/coach/dashboard" | python3 -m json.tool --sort-keys > "$OUT/scale-coach-dashboard.json"
  curl -fsS -b "$JAR_DIR/scale" "$BASE/api/clients" | python3 -m json.tool --sort-keys > "$OUT/scale-coach-clients.json"
  curl -fsS -b "$JAR_DIR/scale" "$BASE/api/coach/conversations" | python3 -m json.tool --sort-keys > "$OUT/scale-coach-conversations.json"
fi
echo "$(ls "$OUT" | wc -l | tr -d ' ') responses saved to $OUT"
