#!/usr/bin/env bash
# Waits until the stack can actually serve the demo accounts.
#
# /actuator/health turns UP as soon as Tomcat is listening, but the demo seeder
# runs after that, so "healthy" is not "ready": a load test or E2E run that
# starts in that gap signs in as users who don't exist yet, and ten failed
# sign-ins from one IP trip the login lockout for 15 minutes. So check the
# database for the seed (one transaction, so any seeded row means all of them),
# never by attempting a login.
set -euo pipefail
COMPOSE_FILE="${COMPOSE_FILE:-$(cd "$(dirname "$0")" && pwd)/docker-compose.loadtest.yml}"
BASE="${BASE_URL:-http://localhost:18080}"
for _ in $(seq 1 120); do
  if curl -fsS "$BASE/actuator/health" >/dev/null 2>&1 &&
     [ "$(docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U fitnesshub -d fitnesshub -At \
           -c "select count(*) from users where email = 'client5@fitnesshub.local'" 2>/dev/null)" = "1" ]; then
    echo "stack ready"
    exit 0
  fi
  sleep 2
done
echo "stack never became ready" >&2
exit 1
