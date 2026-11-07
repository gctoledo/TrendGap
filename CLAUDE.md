# TrendGap: instruções para agentes

Radar que detecta tendências que cresceram lá fora e ainda não chegaram ao Brasil.
A constituição (.specify/memory/constitution.md) prevalece sobre tudo. Decisões: docs/decision-log.md e docs/adr/.

## Estrutura
backend/ Java 25, Spring Boot 4.1, Maven wrapper · ml-service/ Python 3.13, uv, FastAPI · frontend/ React+TS+Vite, pnpm
contracts/ OpenAPI · infra/ Docker Compose e observabilidade · scripts/ smoke e verificações · specs/ Spec Kit · docs/ ADRs e documentação

## Comandos
- Backend: `cd backend && ./mvnw spotless:apply verify` (só unitários: `./mvnw test`)
- ML: `cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest`
- Frontend: `cd frontend && pnpm lint && pnpm typecheck && pnpm test`
- Sistema: `docker compose --env-file .env -f infra/compose.yaml up --wait` (profile opcional: `--profile observability`)
- Smoke ponta a ponta: `scripts/smoke.sh` (`SMOKE_OBSERVABILITY=1` com o profile)
- Verificações: `scripts/check-env-example.sh`, `scripts/check-failfast.sh`, `scripts/test-gitleaks.sh`
- Hooks: `pnpm install && lefthook install` (gitleaks, formatadores, commitlint)
- CI: o check obrigatório da main é `ci-ok` (.github/workflows/ci.yml)
- Sempre pnpm (nunca npm) e ./mvnw (nunca mvn).

## Regras inegociáveis
1. Uma tarefa por sessão: leia só a tarefa em specs/<feature>/tasks.md e os arquivos que ela cita.
2. Lógica de domínio: testes primeiro com o subagente test-writer; depois implemente até ficar verde.
3. Fonte, score, canal ou marketplace novo = adaptador que implementa uma porta. Nunca altere o núcleo para isso.
4. Probabilidade nunca vem do LLM.
5. Nenhum provedor pago entra sem limite de gasto configurado no painel do próprio provedor; o controle de custo na aplicação está adiado (ADR-0014).
6. Nunca leia, crie ou commite segredos. Configuração nova = variável de ambiente documentada sem valor em .env.example.
7. Nada de dados pessoais de terceiros: só agregados sobre temas.
8. Dependência nova ou padrão relevante: pare e proponha um ADR (skill new-adr) antes de usar.
9. Código, identificadores e commits em inglês (Conventional Commits); specs, ADRs e docs em português.

## Fluxo de uma tarefa
test-writer (vermelho) → implementar (verde; hooks formatam e verificam) → reviewer → corrigir bloqueadores → commit → marcar a tarefa em tasks.md.
Falha difícil: debugger. Busca ampla no código: Explore. Documentação e ADR: scribe.
Não faça push nem abra PR sem pedido explícito.

## Respostas
Curtas. Ao terminar: arquivos alterados, como verificou, pendências. Nunca cole logs longos.
