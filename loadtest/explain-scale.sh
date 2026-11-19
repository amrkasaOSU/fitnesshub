#!/usr/bin/env bash
# EXPLAIN (ANALYZE, BUFFERS) for the queries that dominate at scale, against
# the synthetic dataset from loadtest/scale-seed.sql. Run before and after an
# index migration and compare:
#
#   loadtest/explain-scale.sh > loadtest/results/queries/scale-explain-<label>.txt
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
q() { "$HERE/psql.sh" -At -c "$1"; }
explain() { echo; echo "-- $1"; "$HERE/psql.sh" -P pager=off -c "explain (analyze, buffers) $2"; }

COACH="$(q "select id from users where email='scale-coach@fitnesshub.local'")"
CLIENT="$(q "select id from users where email='scale-client1@fitnesshub.local'")"
CONV="$(q "select id from conversations where client_id='$CLIENT'")"

echo "-- $(date -u +%FT%TZ)  rows: $(q "select count(*) from workout_sets") sets, $(q "select count(*) from workout_sessions") sessions, $(q "select count(*) from notifications") notifications"

explain "1. Unread notification badge (polled every 30 s per open tab)" \
  "select count(*) from notifications where user_id='$COACH' and read_at is null"
explain "2. Newest notifications page" \
  "select * from notifications where user_id='$COACH' order by created_at desc limit 20"
explain "3. A client's sessions in the last 7 days (dashboards, adherence, flags)" \
  "select * from workout_sessions where client_id='$CLIENT' and started_at between now() - interval '7 days' and now()"
explain "4. Workout history page, newest first" \
  "select * from workout_sessions where client_id='$CLIENT' order by started_at desc limit 20"
explain "5. Latest completed session per client, whole roster" \
  "select distinct on (client_id) * from workout_sessions where client_id in (select client_id from coach_clients where coach_id='$COACH') and status='COMPLETED' order by client_id, started_at desc"
explain "6. A client's weigh-ins in the last 23 days" \
  "select * from weight_entries where client_id='$CLIENT' and recorded_at >= now() - interval '23 days' order by recorded_at"
explain "7. Latest weigh-in per client, whole roster" \
  "select distinct on (client_id) * from weight_entries where client_id in (select client_id from coach_clients where coach_id='$COACH') order by client_id, recorded_at desc"
explain "8. A client's recent personal records" \
  "select * from personal_records where client_id='$CLIENT' order by achieved_at desc limit 5"
explain "9. Newest message in a conversation" \
  "select * from messages where conversation_id='$CONV' order by created_at desc limit 1"
explain "10. Unread messages in a conversation" \
  "select count(*) from messages where conversation_id='$CONV' and sender_id <> '$COACH' and read_at is null"
explain "11. A client's active program assignment" \
  "select * from client_programs where client_id='$CLIENT' and status='ACTIVE' order by start_date desc limit 1"
