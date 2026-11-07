# TrendGap

Detecta produtos, modelos de negócio e formatos de conteúdo que cresceram lá fora
e ainda não chegaram ao Brasil, estimando a probabilidade de virarem tendência.

**Status:** Sprint 0, fundação.

Stack: Java 25 + Spring Boot 4.1, Python (serviço de ML), React + Vite, PostgreSQL + pgvector.
Desenvolvido com agentes de IA (Claude Code + Spec Kit).

Licença: MIT.

## Como rodar

Pré-requisitos: Docker Engine com Compose v2.24+ (`up --wait`), `curl` e `jq`. Para desenvolver
fora de contêiner: JDK 25, uv, Node 24 + pnpm (via `corepack`), gitleaks e lefthook
(`pnpm install && lefthook install`).

```sh
cp .env.example .env   # preencha as variáveis obrigatórias com valores locais
docker compose -f infra/compose.yaml up --wait
```

Abra <http://localhost:8080> (ou `TRENDGAP_WEB_PORT`): a página de status mostra backend, banco e
serviço de ML, com horário.

**Observabilidade (opcional):** defina `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318` no `.env` e
suba com `--profile observability`; Grafana em <http://localhost:3000>. Detalhes em
[infra/observability/README.md](infra/observability/README.md).

**Smoke ponta a ponta:** `scripts/smoke.sh` (com o profile: `SMOKE_OBSERVABILITY=1 scripts/smoke.sh`).

**Verificação por componente:**

```sh
cd backend && ./mvnw spotless:apply verify
cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest
cd frontend && pnpm lint && pnpm typecheck && pnpm test
```
