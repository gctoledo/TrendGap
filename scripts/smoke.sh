#!/usr/bin/env bash
# Smoke test: brings the stack up, checks GET /api/status and the reaction to
# stopping/starting the ML service and the database, verifies trace propagation
# (same trace.id in backend and ML logs), that metrics endpoints are not reachable
# from the host, and that no secret leaks into the backend/ML logs.
# Usage: scripts/smoke.sh [--keep]   (--keep leaves the stack running at the end)
# SMOKE_ENV_FILE=path uses another env file (default .env; a temporary one is generated if missing).
# SMOKE_OBSERVABILITY=1 also starts the "observability" profile (OTLP to the lgtm
# service) and checks the trace in Tempo through Grafana (port 3000).
set -euo pipefail

cd "$(dirname "$0")/.."

KEEP=false
[[ "${1:-}" == "--keep" ]] && KEEP=true

DEADLINE=15
ENV_FILE="${SMOKE_ENV_FILE:-.env}"
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
    [[ "${SMOKE_OBSERVABILITY:-}" == "1" ]] && echo "OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318"
  } >"$ENV_FILE"
fi

env_value() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }

PROFILE_FLAGS=()
if [[ "${SMOKE_OBSERVABILITY:-}" == "1" ]]; then PROFILE_FLAGS=(--profile observability); fi

compose() {
  docker compose -p trendgap-smoke --env-file "$ENV_FILE" ${PROFILE_FLAGS[@]+"${PROFILE_FLAGS[@]}"} -f infra/compose.yaml "$@"
}

PORT="${TRENDGAP_WEB_PORT:-$(env_value TRENDGAP_WEB_PORT)}" # shell env wins, as in compose
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

echo "== trace propagation =="
TRACE_ID="$(openssl rand -hex 16)"
SPAN_ID="$(openssl rand -hex 8)"
curl -fsS --max-time 5 -H "traceparent: 00-${TRACE_ID}-${SPAN_ID}-01" "$BASE/api/status" >/dev/null ||
  fail "GET /api/status with traceparent failed"
backend_hit=0
ml_hit=0
end=$((SECONDS + DEADLINE))
while ((SECONDS < end)); do
  backend_hit="$(compose logs --no-color backend 2>&1 | grep -c -F "$TRACE_ID" || true)"
  ml_hit="$(compose logs --no-color ml-service 2>&1 | grep -c -F "$TRACE_ID" || true)"
  ((backend_hit > 0 && ml_hit > 0)) && break
  sleep 1
done
((backend_hit > 0)) || fail "trace.id $TRACE_ID not found in backend logs"
((ml_hit > 0)) || fail "trace.id $TRACE_ID not found in ML logs"
echo "ok: trace.id $TRACE_ID in backend and ML logs"

echo "== internal endpoints unreachable from host =="
for port in 8081 8000; do
  if curl -s --max-time 2 -o /dev/null "http://localhost:$port/"; then
    fail "port $port answers on the host"
  fi
done
for path in /actuator/health /actuator/prometheus /metrics; do
  body="$(curl -s --max-time 4 "$BASE$path" || true)"
  if grep -q -E '^# HELP|"status" *: *"(UP|DOWN)"' <<<"$body"; then
    fail "$path exposed through the frontend"
  fi
done
echo "ok: health/metrics endpoints are internal only"

if [[ "${SMOKE_OBSERVABILITY:-}" == "1" ]]; then
  echo "== trace in Tempo (Grafana) =="
  grep -q '^OTEL_EXPORTER_OTLP_ENDPOINT=.\+' "$ENV_FILE" ||
    fail "SMOKE_OBSERVABILITY=1 requires OTEL_EXPORTER_OTLP_ENDPOINT in $ENV_FILE"
  GRAFANA_AUTH="admin:admin" # gitleaks:allow (default credentials of the local Grafana)
  end=$((SECONDS + 60))
  services=""
  while ((SECONDS < end)); do
    services="$(curl -s --max-time 5 -u "$GRAFANA_AUTH" \
      "http://localhost:3000/api/datasources/proxy/uid/tempo/api/traces/${TRACE_ID}" |
      jq -r '[.batches[]?.resource.attributes[]? | select(.key == "service.name") | .value.stringValue] | unique | join(",")' \
        2>/dev/null || true)"
    [[ "$services" == *backend* && "$services" == *ml-service* ]] && break
    sleep 3
  done
  [[ "$services" == *backend* && "$services" == *ml-service* ]] ||
    fail "Tempo has no spans from both services for trace $TRACE_ID (got: ${services:-none})"
  echo "ok: Tempo has spans from $services"
else
  echo "skip: Tempo check (set SMOKE_OBSERVABILITY=1 with the observability profile to enable)"
fi

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
