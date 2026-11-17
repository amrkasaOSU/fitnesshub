#!/usr/bin/env bash
# Endpoint cost against the synthetic scale dataset (loadtest/scale-seed.sql):
# SQL statements per request, database time per request, and median
# wall-clock time over N sequential requests from one client.
#
#   loadtest/scale-measure.sh > loadtest/results/scale/<label>.md
set -euo pipefail
BASE="${BASE_URL:-http://localhost:18080}"
N="${N:-7}"
HERE="$(cd "$(dirname "$0")" && pwd)"
JAR_DIR="$(mktemp -d)"; trap 'rm -rf "$JAR_DIR"' EXIT
psql() { "$HERE/psql.sh" -At -F '|' "$@"; }

login() {
  curl -fsS -c "$JAR_DIR/$1" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"FitnessHub!2024\"}" "$BASE/api/auth/login" >/dev/null
}

measure() { # label account path
  local label="$1" jar="$JAR_DIR/$2" path="$3" times=()
  curl -fsS -b "$jar" "$BASE$path" >/dev/null
  psql -c "select pg_stat_statements_reset()" >/dev/null
  for _ in $(seq 1 "$N"); do
    times+=("$(curl -fsS -o /dev/null -w '%{time_total}' -b "$jar" "$BASE$path")")
  done
  local row calls ms median
  row="$(psql -c "
    select coalesce(sum(calls),0), coalesce(round(sum(total_exec_time)::numeric, 2),0)
    from pg_stat_statements s join pg_database d on d.oid = s.dbid
    where d.datname = 'fitnesshub'
      and query !~* '^(begin|commit|rollback|set |show |select pg_stat_statements_reset)'
      and query not ilike '%pg_stat_statements%'")"
  calls="${row%%|*}"; ms="${row##*|}"
  median="$(printf '%s\n' "${times[@]}" | sort -n | sed -n "$(( (N + 1) / 2 ))p")"
  printf '| %s | %.0f | %.1f | %.0f |\n' "$label" \
    "$(echo "$calls / $N" | bc -l)" "$(echo "$ms / $N" | bc -l)" "$(echo "$median * 1000" | bc -l)"
}

login scale-coach@fitnesshub.local
login scale-client1@fitnesshub.local

echo "| endpoint (200-client coach / 1-year client) | SQL statements / request | DB time / request (ms) | median response (ms) |"
echo "|---|---:|---:|---:|"
measure "coach dashboard"        scale-coach@fitnesshub.local   /api/coach/dashboard
measure "coach client roster"    scale-coach@fitnesshub.local   /api/clients
measure "coach conversations"    scale-coach@fitnesshub.local   /api/coach/conversations
measure "coach unread badge"     scale-coach@fitnesshub.local   /api/notifications/unread-count
measure "client dashboard"       scale-client1@fitnesshub.local /api/dashboard
measure "client today's workout" scale-client1@fitnesshub.local /api/workouts/today
measure "client week strip"      scale-client1@fitnesshub.local /api/workouts/week
measure "client workout history" scale-client1@fitnesshub.local /api/workouts
measure "client weight dashboard" scale-client1@fitnesshub.local /api/weight/dashboard
