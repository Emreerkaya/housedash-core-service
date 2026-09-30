# Running against Supabase

Supabase is used as a plain Postgres (ADR-0003). The service connects with one database role, Flyway applies `src/main/resources/db/migration` on startup, and nothing is created in the dashboard, the CLI or the MCP server.

## One-time setup

1. In the Supabase dashboard open the project, choose Connect, then the Session pooler tab. It shows a host of the form `aws-0-<region>.pooler.supabase.com`, port 5432 and a user `postgres.<project ref>`. For project `xdrnqovydhrrohdbgnla` the host measured as `aws-0-us-east-1.pooler.supabase.com`.
2. Copy `.env.example` to `.env` (untracked) and replace `SUPABASE_DB_PASSWORD` with the database password. Reset it under Project Settings, Database if it is lost.
3. Run `just supabase-verify`.

## What `just supabase-verify` does

It reads `.env`, checks the database is reachable with `psql`, starts the service with the `supabase` profile so Flyway applies `V1__case.sql`, posts a case to `/cases`, prints `flyway_schema_history` and the stored row, then stops the service.

## Variables

| variable | default | meaning |
|---|---|---|
| `SUPABASE_DB_HOST` | none | session pooler host |
| `SUPABASE_DB_PORT` | 5432 | keep 5432; Flyway needs session-level advisory locks, which the transaction pooler on 6543 does not provide |
| `SUPABASE_DB_NAME` | postgres | database name |
| `SUPABASE_DB_USER` | none | `postgres.<project ref>` on the pooler |
| `SUPABASE_DB_PASSWORD` | none | never committed; `.env` is ignored by git and by Docker |
| `SUPABASE_DB_SSLMODE` | require | only set to `disable` against a local Postgres |
| `SUPABASE_DB_POOL_SIZE` | 5 | Hikari maximum pool size per instance |

The direct host `db.<ref>.supabase.co` resolves to an IPv6 address only, so it is unreachable from IPv4 networks; use the session pooler.

## Container

`just image` builds `housedash-core-service:local`. The image defaults to the `supabase` profile and runs as a non-root user:

    docker run --env-file .env -p 8080:8080 housedash-core-service:local

## Cloud Run

Deploying needs Google Cloud credentials, which the owner holds. Store the password in Secret Manager once, then:

    just deploy-cloud-run PROJECT REGION SERVICE aws-0-us-east-1.pooler.supabase.com postgres.xdrnqovydhrrohdbgnla SECRET_NAME

The password is mounted from the named secret with `--set-secrets`; only the secret name appears in the command. The service is deployed private by default because it has no authentication; `ALLOW_UNAUTHENTICATED=1` opts in to a public service. `DRY_RUN=1` prints the `gcloud` command without running it.

## Row-level security

Tables created in `public` are reachable through Supabase's generated API by the `anon` and `authenticated` roles unless row-level security is enabled. ADR-0003 forbids using that API, so the remedy is a Flyway migration that enables row-level security on every table with no policies, which makes the generated API see nothing while the service's owner role is unaffected.
