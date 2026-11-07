#!/usr/bin/env bash
# Fail-fast check (SC-006): starts each service with an invalid setting and requires a
# non-zero exit, the variable name and the broken rule in the output, and never the value.
# Usage: scripts/check-failfast.sh   (FAILFAST_NO_BUILD=1 reuses the local images)
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE="$(mktemp)"
OUT_FILE="$(mktemp)"
trap 'rm -f "$ENV_FILE" "$OUT_FILE"' EXIT
{
  echo "POSTGRES_USER=postgres"
  echo "POSTGRES_PASSWORD=failfast-pg-pass"
  echo "TRENDGAP_DB_USERNAME=trendgap"
  echo "TRENDGAP_DB_PASSWORD=failfast-app-pass"
} >"$ENV_FILE"

compose() { docker compose -p trendgap-failfast --env-file "$ENV_FILE" -f infra/compose.yaml "$@"; }

if [[ -z "${FAILFAST_NO_BUILD:-}" ]]; then
  compose build backend ml-service >/dev/null 2>&1
fi

failures=0
fail() { echo "FAIL: $*" >&2; failures=$((failures + 1)); }

# check <service> <variable> <value> <rule fragment> [banner]
check() {
  local service="$1" variable="$2" value="$3" rule="$4" banner="${5:-}"
  local code=0
  compose run --rm --no-deps -e "$variable=$value" "$service" >"$OUT_FILE" 2>&1 || code=$?
  local label="$service $variable"
  if ((code == 0)); then fail "$label: exit code was 0"; return; fi
  grep -qF -- "$variable" "$OUT_FILE" || fail "$label: variable name missing from output"
  grep -qiF -- "$rule" "$OUT_FILE" || fail "$label: rule '$rule' missing from output"
  if [[ -n "$banner" ]]; then
    grep -qF -- "$banner" "$OUT_FILE" || fail "$label: '$banner' missing from output"
  fi
  if [[ -n "$value" ]] && grep -qF -- "$value" "$OUT_FILE"; then
    fail "$label: informed value leaked into output"
  fi
  echo "ok: $label"
}

check backend TRENDGAP_ML_BASE_URL "" "absolute http or https URL" "APPLICATION FAILED TO START"
check backend TRENDGAP_ML_TIMEOUT "10m" "between 100ms and 5s" "APPLICATION FAILED TO START"
check ml-service TRENDGAP_ML_PORT "abc-not-an-int" "integer"

if ((failures > 0)); then
  echo "FAILFAST FAILED ($failures)" >&2
  exit 1
fi
echo "FAILFAST OK"
