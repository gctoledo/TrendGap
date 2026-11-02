#!/usr/bin/env bash
# Stop: verifica as áreas alteradas. Se algo falhar, devolve o erro ao agente (uma vez).
set -uo pipefail
input=$(cat)
active=$(jq -r '.stop_hook_active // false' <<<"$input")
cd "$CLAUDE_PROJECT_DIR" || exit 0
changed=$(git status --porcelain | awk '{print $NF}' | cut -d/ -f1 | sort -u)
fail=0; report=""
run() {
  local name="$1"; shift
  local out
  if ! out=$("$@" 2>&1); then
    fail=1; report+="### $name falhou (últimas linhas)"$'\n'"$(tail -n 30 <<<"$out")"$'\n'
  fi
}
if grep -qx backend <<<"$changed" && [ -f backend/pom.xml ]; then
  run backend bash -c "cd backend && ./mvnw -q spotless:apply test"
fi
if grep -qx ml-service <<<"$changed" && [ -f ml-service/pyproject.toml ]; then
  run ml-service bash -c "cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest -q"
fi
if grep -qx frontend <<<"$changed" && [ -f frontend/package.json ]; then
  run frontend bash -c "cd frontend && pnpm -s lint && pnpm -s typecheck && pnpm -s test --run"
fi
[ "$fail" -eq 0 ] && exit 0
printf '%s' "$report" >&2
# Se já insistiu uma vez, não prende o agente em laço: o erro fica visível para você decidir.
[ "$active" = "true" ] && exit 0
exit 2
