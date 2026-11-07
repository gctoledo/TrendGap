#!/usr/bin/env bash
# Checks that every TRENDGAP_*, OTEL_* and POSTGRES_* variable used by the backend
# (application.yaml), the ML service (settings.py) and Compose is documented in
# .env.example: a definition line (`VAR=` or `# VAR=`) with an empty or fictitious
# value, and a preceding comment stating obligatoriness (Obrigatória(s)/Opcional(is)).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env.example"
YAML="$ROOT/backend/src/main/resources/application.yaml"
SETTINGS="$ROOT/ml-service/src/trendgap_ml/settings.py"
COMPOSE="$ROOT/infra/compose.yaml"
PATTERN='^(TRENDGAP|OTEL|POSTGRES)_[A-Z0-9_]+$'

for f in "$ENV_FILE" "$YAML" "$SETTINGS" "$COMPOSE"; do
  [[ -f "$f" ]] || { echo "FAIL: missing file $f" >&2; exit 1; }
done

# Spring placeholders ${trendgap.db.url:default} -> TRENDGAP_DB_URL.
from_yaml() {
  { grep -oE '\$\{[A-Za-z0-9._-]+' "$YAML" || true; } | sed 's/^\${//' |
    tr '[:lower:].-' '[:upper:]__'
}

# ENV_PREFIX + fields of Settings that have no explicit alias, plus literal OTEL_* names.
from_settings() {
  local prefix
  prefix="$(sed -nE 's/^ENV_PREFIX = "([A-Z_]+)".*/\1/p' "$SETTINGS")"
  if [[ -n "$prefix" ]]; then
    sed -nE '/^class Settings\(/,/^def |^class [A-Z][A-Za-z]*\(object/p' "$SETTINGS" |
      sed -nE 's/^    ([a-z_][a-z0-9_]*): (.*)$/\1 \2/p' |
      { grep -vE '^(model_config|otlp_endpoint) |Annotated|validation_alias' || true; } |
      while read -r name _; do echo "${prefix}$(tr '[:lower:]' '[:upper:]' <<<"$name")"; done
  fi
  { grep -oE '"OTEL_[A-Z0-9_]+"' "$SETTINGS" || true; } | tr -d '"'
}

# ${VAR...} references and environment keys (`  VAR: value`).
from_compose() {
  { grep -oE '\$\{[A-Z][A-Z0-9_]*' "$COMPOSE" || true; } | sed 's/^\${//'
  sed -nE 's/^[[:space:]]+([A-Z][A-Z0-9_]*):.*/\1/p' "$COMPOSE"
}

VARS="$({ from_yaml; from_settings; from_compose; } | { grep -E "$PATTERN" || true; } | sort -u)"
[[ -n "$VARS" ]] || { echo "FAIL: no variables extracted from sources" >&2; exit 1; }

mapfile -t LINES <"$ENV_FILE"

is_definition() { [[ "$1" =~ ^#?[[:space:]]*[A-Z][A-Z0-9_]*= ]]; }

# Value must be empty or fictitious; secret-like names never carry real-looking values.
value_ok() {
  local name="$1" value="$2"
  [[ -z "$value" ]] && return 0
  if [[ "$name" =~ (PASSWORD|SECRET|TOKEN|KEY) ]]; then
    [[ "$value" =~ ^(changeme|change-me|example|fake|dummy|fictitious|your-.*|\<.*\>)$ ]]
    return
  fi
  return 0
}

# Comment block above line $1, walking over other definitions, stopping at blank or section lines.
comment_block() {
  local i=$(($1 - 1)) out=""
  while ((i >= 0)); do
    local l="${LINES[$i]}"
    if [[ -z "${l//[[:space:]]/}" || "$l" =~ ^#[[:space:]]*--- ]]; then break; fi
    if ! is_definition "$l"; then out+="${l}"$'\n'; fi
    i=$((i - 1))
  done
  printf '%s' "$out"
}

FAILED=0
report() { echo "$1: $2"; FAILED=1; }

while read -r var; do
  idx=-1
  for i in "${!LINES[@]}"; do
    if [[ "${LINES[$i]}" =~ ^(#[[:space:]]*)?"$var"=(.*)$ ]]; then idx=$i; value="${BASH_REMATCH[2]}"; break; fi
  done
  if ((idx < 0)); then report "$var" "missing from .env.example"; continue; fi
  value_ok "$var" "$value" || report "$var" "value must be empty or fictitious"
  block="$(comment_block "$idx")"
  if [[ -z "${block//[[:space:]]/}" ]]; then
    report "$var" "no description comment"
  elif ! grep -qiE 'obrigat[óo]ri(a|as)|opcional|opcionais' <<<"$block"; then
    report "$var" "comment lacks obligatoriness (Obrigatória(s)/Opcional(is))"
  fi
done <<<"$VARS"

if ((FAILED)); then
  echo "FAIL: .env.example is incomplete" >&2
  exit 1
fi
echo "OK: $(wc -l <<<"$VARS") variables documented in .env.example"
