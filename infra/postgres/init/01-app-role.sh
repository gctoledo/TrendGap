#!/usr/bin/env bash
# Runs once, on first initialisation of the data volume, as the image superuser.
# Creates the application database, a non-superuser application role that owns it,
# and the pgvector extension (only a superuser can create it; ADR-0019).
set -euo pipefail

: "${TRENDGAP_DB_USERNAME:?TRENDGAP_DB_USERNAME is required}"
: "${TRENDGAP_DB_PASSWORD:?TRENDGAP_DB_PASSWORD is required}"
APP_DB="${TRENDGAP_DB_NAME:-trendgap}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v app_user="$TRENDGAP_DB_USERNAME" -v app_password="$TRENDGAP_DB_PASSWORD" -v app_db="$APP_DB" \
  <<'SQL' >/dev/null
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L',
              :'app_user', :'app_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'app_db', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'app_db') \gexec
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$APP_DB" \
  -v app_user="$TRENDGAP_DB_USERNAME" <<'SQL' >/dev/null
CREATE EXTENSION IF NOT EXISTS vector;
SELECT format('ALTER SCHEMA public OWNER TO %I', :'app_user') \gexec
SQL
