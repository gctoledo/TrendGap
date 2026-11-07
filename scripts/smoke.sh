#!/usr/bin/env bash
# Smoke test (status part): brings the stack up, checks GET /api/status and the
# reaction to stopping/starting the ML service and the database, and verifies that
# no secret leaks into the backend/ML logs.
# Usage: scripts/smoke.sh [--keep]   (--keep leaves the stack running at the end)
set -euo pipefail

cd "$(dirname "$0")/.."

KEEP=false
[[ "${1:-}" == "--keep" ]] && KEEP=true

DEADLINE=15
ENV_FILE=".env"
GENERATED_ENV=""
if [[ ! -f "$ENV_FILE" ]]; then
  GENERATED_ENV="$(mktemp)"
  ENV_FILE="$GENERATED_ENV"
  {
    echo "POSTGRES_USER=postgres"
    echo "POSTGRES_PASSWORD=$(openssl rand -hex 16)"
    echo "TRENDGAP_DB_USERNAME=trendgap"
    echo "TRENDGAP_DB_PASSWORD=$(openssl rand -hex 16)"
    echo "TRENDGAP_WEB_PORT=${TRENDGAP_WEB_PORT:-8080}"
  } >"$ENV_FILE"
fi

env_value() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }

compose() { docker compose -p trendgap-smoke --env-file "$ENV_FILE" -f infra/compose.yaml "$@"; }

PORT="$(env_value TRENDGAP_WEB_PORT)"
BASE="http://localhost:${PORT:-8080}"

cleanup() {
  local code=$?
  if [[ "$KEEP" == false ]]; then
    compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  [[ -n "$GENERATED_ENV" ]] && rm -f "$GENERATED_ENV"
  exit "$code"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

component_status() {
  curl -fsS --max-time 4 "$BASE/api/status" 2>/dev/null |
    jq -r --arg n "$1" '.components[] | select(.name == $n) | .status' 2>/dev/null || true
}

expect_status() { # component expected
  local end=$((SECONDS + DEADLINE))
  while ((SECONDS < end)); do
    [[ "$(component_status "$1")" == "$2" ]] && { echo "ok: $1=$2"; return 0; }
    sleep 1
  done
  fail "$1 did not reach $2 within ${DEADLINE}s"
}

echo "== up =="
BUILD_FLAG="--build"; [[ -n "${SMOKE_NO_BUILD:-}" ]] && BUILD_FLAG="--no-build"
compose up "$BUILD_FLAG" --wait --wait-timeout 240 -d >/dev/null

echo "== all components UP =="
for c in backend database ml-service; do expect_status "$c" UP; done

echo "== ML service down/up =="
compose stop ml-service >/dev/null
expect_status ml-service DOWN
compose start ml-service >/dev/null
expect_status ml-service UP

echo "== database down/up =="
compose stop postgres >/dev/null
expect_status database DOWN
compose start postgres >/dev/null
expect_status database UP

echo "== no secrets in logs =="
logs="$(compose logs --no-color backend ml-service 2>&1)"
for var in POSTGRES_PASSWORD TRENDGAP_DB_PASSWORD; do
  secret="$(env_value "$var")"
  if [[ -n "$secret" ]] && grep -qF -- "$secret" <<<"$logs"; then
    fail "value of $var found in backend/ML logs"
  fi
done
if grep -qE '://[^/[:space:]@:]+:[^/[:space:]@]+@' <<<"$logs"; then
  fail "URL with credentials found in backend/ML logs"
fi
echo "ok: no secrets in logs"

echo "SMOKE OK"
