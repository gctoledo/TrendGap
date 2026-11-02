#!/usr/bin/env bash
# PreToolUse(Edit|Write) de subagentes: restringe onde o agente pode escrever.
# Uso: restrict-paths.sh tests|docs
set -uo pipefail
mode="${1:-}"
file=$(jq -r '.tool_input.file_path // empty')
[ -z "$file" ] && exit 0
rel="${file#"$CLAUDE_PROJECT_DIR"/}"
case "$mode" in
  tests) re='(^|/)(src/test/|tests/)|\.(test|spec)\.tsx?$|(^|/)test_[^/]*\.py$' ;;
  docs)  re='^(docs/|specs/)|^(README|CHANGELOG)\.md$' ;;
  *) echo "restrict-paths: modo desconhecido '$mode'" >&2; exit 2 ;;
esac
[[ $rel =~ $re ]] && exit 0
echo "Bloqueado: este agente só escreve em caminhos de '$mode' (tentou: $rel)." >&2
exit 2
