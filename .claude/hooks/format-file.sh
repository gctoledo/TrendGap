#!/usr/bin/env bash
# PostToolUse(Edit|Write): formata o arquivo editado (Python e TS). Java é formatado no Stop.
set -uo pipefail
file=$(jq -r '.tool_input.file_path // empty')
if [ -z "$file" ] || [ ! -f "$file" ]; then exit 0; fi
cd "$CLAUDE_PROJECT_DIR" || exit 0
case "$file" in
  */ml-service/*.py)
    (cd ml-service && uv run --quiet ruff format "$file" && uv run --quiet ruff check --fix --quiet "$file") >/dev/null 2>&1 ;;
  */frontend/*.ts|*/frontend/*.tsx|*/frontend/*.css|*/frontend/*.json)
    (cd frontend && pnpm exec prettier --write --log-level warn "$file") >/dev/null 2>&1 ;;
esac
exit 0
