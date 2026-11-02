#!/usr/bin/env bash
# PreToolUse(Bash): bloqueia comandos perigosos ou fora das convenções do projeto.
set -uo pipefail
cmd=$(jq -r '.tool_input.command // empty')
block() { echo "Bloqueado pelo guard-bash: $1" >&2; exit 2; }

re_force='git[[:space:]]+push.*(--force|[[:space:]]-f([[:space:]]|$))'
re_main='git[[:space:]]+push.*[[:space:]](origin[[:space:]]+)?(HEAD:)?main([[:space:]]|$)'
re_noverify='--no-verify'
re_npm='(^|[;&|[:space:]])npm[[:space:]]'
re_mvn='(^|[;&|[:space:]])mvn[[:space:]]'
re_env='(cat|less|more|head|tail|grep|sed|awk)[^|;&]*(^|[[:space:]/])\.env([[:space:]]|$)'
re_pipe_sh='(curl|wget)[^|]*\|[[:space:]]*(ba|z)?sh'
re_reset='git[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+-[a-z]*f)'

[[ $cmd =~ $re_force ]]    && block "push forçado não é permitido."
[[ $cmd =~ $re_main ]]     && block "a main é protegida; trabalhe em branch e abra PR."
[[ $cmd =~ $re_noverify ]] && block "não pule os hooks do git."
[[ $cmd =~ $re_npm ]]      && block "use pnpm, não npm."
[[ $cmd =~ $re_mvn ]]      && block "use ./mvnw, não mvn."
[[ $cmd =~ $re_env ]]      && block "não leia arquivos .env."
[[ $cmd =~ $re_pipe_sh ]]  && block "não execute scripts baixados da internet."
[[ $cmd =~ $re_reset ]]    && block "comando destrutivo; peça ao usuário para rodar."
exit 0
