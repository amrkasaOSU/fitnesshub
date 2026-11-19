#!/usr/bin/env bash
# How many SQL statements does one request to each endpoint cost?
#
#   loadtest/query-count.sh            # prints a Markdown table
#
# Method: reset pg_stat_statements, send N identical requests with nothing else
# running, then divide the total statement calls and total database time by N.
# Counting in the database rather than in the application means nothing can be
# hidden by a cache layer or missed by a logging filter.
set -euo pipefail

BASE="${BASE_URL:-http://localhost:18080}"
N="${N:-20}"
PG="${PG_CONTAINER:-fitnesshub-loadtest-postgres-1}"
JAR_DIR="$(mktemp -d)"
trap 'rm -rf "$JAR_DIR"' EXIT

psql() { docker exec -i "$PG" psql -U fitnesshub -d fitnesshub -At -F '|' "$@"; }

login() { # email -> cookie jar path
  local jar="$JAR_DIR/$1.jar"
  curl -fsS -c "$jar" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"FitnessHub!2024\"}" "$BASE/api/auth/login" >/dev/null
  echo "$jar"
}

measure() { # label jar path
  local label="$1" jar="$2" path="$3"
  curl -fsS -b "$jar" "$BASE$path" >/dev/null   # warm: first call may create today's session
  psql -c "select pg_stat_statements_reset()" >/dev/null
  for _ in $(seq 1 "$N"); do curl -fsS -b "$jar" "$BASE$path" >/dev/null; done
  # Exclude this script's own bookkeeping and transaction control statements.
  local row
  row="$(psql -c "
    select coalesce(sum(calls),0), coalesce(round(sum(total_exec_time)::numeric, 2),0)
    from pg_stat_statements s join pg_database d on d.oid = s.dbid
    where d.datname = 'fitnesshub'
      and query !~* '^(begin|commit|rollback|set |show |select pg_stat_statements_reset)'
      and query not ilike '%pg_stat_statements%'")"
  local calls="${row%%|*}" ms="${row##*|}"
  printf '| %s | %s | %.1f | %.2f |\n' "$label" "$path" \
    "$(echo "$calls / $N" | bc -l)" "$(echo "$ms / $N" | bc -l)"
}

COACH="$(login coach@fitnesshub.local)"
CLIENT="$(login client1@fitnesshub.local)"
CLIENT_ID="$(curl -fsS -b "$CLIENT" "$BASE/api/users/me" | sed -E 's/.*"id":"([^"]+)".*/\1/')"

echo "| endpoint | path | SQL statements / request | DB time / request (ms) |"
echo "|---|---|---:|---:|"
measure client_dashboard      "$CLIENT" "/api/dashboard"
measure workout_today         "$CLIENT" "/api/workouts/today"
measure workout_week          "$CLIENT" "/api/workouts/week"
measure workout_history       "$CLIENT" "/api/workouts"
measure weight_dashboard      "$CLIENT" "/api/weight/dashboard"
measure exercises             "$CLIENT" "/api/exercises?pageSize=100"
measure coach_dashboard       "$COACH"  "/api/coach/dashboard"
measure coach_clients         "$COACH"  "/api/clients"
measure coach_client_workouts "$COACH"  "/api/clients/$CLIENT_ID/workouts"
measure coach_conversations   "$COACH"  "/api/coach/conversations"
