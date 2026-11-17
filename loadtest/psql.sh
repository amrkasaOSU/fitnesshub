#!/usr/bin/env bash
# psql inside the load-test Postgres container: loadtest/psql.sh -c "select 1"
exec docker exec -i "${PG_CONTAINER:-fitnesshub-loadtest-postgres-1}" psql -U fitnesshub -d fitnesshub "$@"
