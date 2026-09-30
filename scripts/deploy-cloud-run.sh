#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'usage: %s PROJECT REGION SERVICE DB_HOST DB_USER DB_PASSWORD_SECRET [SECRET_VERSION]\n' "$0" >&2
    printf 'DB_PASSWORD_SECRET is the name of a Secret Manager secret; its value is never read here\n' >&2
    printf 'DRY_RUN=1 prints the gcloud command instead of running it\n' >&2
    printf 'ALLOW_UNAUTHENTICATED=1 makes the service publicly invokable; the default is private\n' >&2
}

if [ "$#" -lt 6 ] || [ "$#" -gt 7 ]; then
    usage
    exit 2
fi

project="$1"
region="$2"
service="$3"
db_host="$4"
db_user="$5"
secret="$6"
secret_version="${7:-latest}"

if [ "${ALLOW_UNAUTHENTICATED:-0}" = "1" ]; then
    access="--allow-unauthenticated"
else
    access="--no-allow-unauthenticated"
fi

command=(
    gcloud run deploy "$service"
    --project "$project"
    --region "$region"
    --source .
    "$access"
    --port 8080
    --memory 512Mi
    --max-instances 2
    --set-env-vars "SPRING_PROFILES_ACTIVE=supabase,SUPABASE_DB_HOST=$db_host,SUPABASE_DB_USER=$db_user"
    --set-secrets "SUPABASE_DB_PASSWORD=$secret:$secret_version"
)

if [ "${DRY_RUN:-0}" = "1" ]; then
    printf '%q ' "${command[@]}"
    printf '\n'
    exit 0
fi

if ! command -v gcloud >/dev/null 2>&1; then
    printf 'gcloud is not installed; install it and authenticate before deploying\n' >&2
    exit 1
fi

"${command[@]}"
