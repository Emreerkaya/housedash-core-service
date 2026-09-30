#!/usr/bin/env bash
set -euo pipefail

env_file="${ENV_FILE:-.env}"
port="${VERIFY_PORT:-18080}"

if [ ! -f "$env_file" ]; then
    printf '%s does not exist; copy .env.example to .env and paste the database password into it\n' "$env_file" >&2
    exit 2
fi

set -a
. "$env_file"
set +a

for name in SUPABASE_DB_HOST SUPABASE_DB_USER SUPABASE_DB_PASSWORD; do
    if [ -z "${!name:-}" ]; then
        printf '%s is not set in %s\n' "$name" "$env_file" >&2
        exit 2
    fi
done

export PGHOST="$SUPABASE_DB_HOST"
export PGPORT="${SUPABASE_DB_PORT:-5432}"
export PGDATABASE="${SUPABASE_DB_NAME:-postgres}"
export PGUSER="$SUPABASE_DB_USER"
export PGPASSWORD="$SUPABASE_DB_PASSWORD"
export PGSSLMODE="${SUPABASE_DB_SSLMODE:-require}"
psql_bin="${PSQL:-psql}"

"$psql_bin" -X -q -t -A -c 'select 1' >/dev/null
printf 'database reachable at %s:%s as %s\n' "$PGHOST" "$PGPORT" "$PGUSER"

log_file="$(mktemp)"
SPRING_PROFILES_ACTIVE=supabase SERVER_PORT="$port" ./gradlew --console=plain bootRun >"$log_file" 2>&1 &
service_pid=$!
trap 'pkill -P "$service_pid" 2>/dev/null || true; kill "$service_pid" 2>/dev/null || true' EXIT

for _ in $(seq 1 120); do
    if grep -q 'Started HouseDashServiceKt' "$log_file"; then
        break
    fi
    if ! kill -0 "$service_pid" 2>/dev/null; then
        tail -40 "$log_file" >&2
        printf 'the service exited before it started; log above\n' >&2
        exit 1
    fi
    sleep 1
done

grep -q 'Started HouseDashServiceKt' "$log_file" || { tail -40 "$log_file" >&2; exit 1; }

key="verify-$(date +%s)-$RANDOM"
reply="$(curl -sS -f -X POST "http://localhost:$port/cases" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" \
    -d '{"nesterId":"ns_verify","description":"kitchen tap drips from the base and needs a look","photoIds":["ph_1"]}')"
printf 'POST /cases replied %s\n' "$reply"

case_id="$(printf '%s' "$reply" | sed -E 's/.*"caseId":"([^"]+)".*/\1/')"

"$psql_bin" -X -c "select version, success from flyway_schema_history order by installed_rank"
"$psql_bin" -X -c "select id, owner, state, created_at from cases where id = '$case_id'"
