# Plano de implementação: Fundação técnica

**Branch**: `001-fundacao-tecnica` | **Data**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Entrada**: especificação em `specs/001-fundacao-tecnica/spec.md`

## Resumo

Esqueleto sem regra de negócio que roda de ponta a ponta e é observável: backend Spring Boot com um
único módulo Modulith (`platform`) que expõe `GET /api/status` agregando a saúde do backend, do banco
e do serviço de ML; serviço FastAPI sem estado com `GET /health`; SPA React que consulta o status a
cada 5 s, servida por nginx que faz proxy de `/api`; Compose com healthchecks e pilha de
observabilidade opcional; CI com filtro por caminho, varredura de segredos, CodeQL, Trivy e um check
agregador `ci-ok`. A fronteira Java ↔ Python nasce de um único OpenAPI, com testes de contrato dos
dois lados. Verificações e versões: [research.md](research.md).

## Contexto técnico

**Linguagens/versões**: Java 25 (Temurin 25.0.5), Python 3.13.18, TypeScript 6.0.3 (Node 24.22 LTS).

**Dependências principais**:
- Backend: Spring Boot 4.1.2 (web, actuator, validation, jdbc, flyway, opentelemetry), Spring Modulith
  2.1.2, Flyway 12.4 + `flyway-database-postgresql`, Micrometer (Prometheus + OTLP), cliente gerado
  pelo openapi-generator 7.27 (`restclient`, Jackson 3). Build: Maven 3.9.16 via wrapper 3.3.4,
  Spotless, Error Prone 2.52 + NullAway 0.14 + JSpecify 1.0, JaCoCo 0.8.16.
- ML: FastAPI 0.162, uvicorn 0.54, pydantic-settings 2.16, OpenTelemetry SDK 1.45 + instrumentação
  FastAPI + exportadores OTLP HTTP e Prometheus; ruff 0.17, mypy 2.4 (strict), pytest 9.2,
  schemathesis 4.33.
- Frontend: React 19.3, Vite 8.3, TanStack Query 5.105, ESLint 10 + typescript-eslint 8.72, Prettier
  3.9, Vitest 5.1 + Testing Library 16.3 + jsdom.

**Armazenamento**: PostgreSQL 18 + pgvector 0.8.7 (`pgvector/pgvector:0.8.7-pg18-trixie`); só a
migração V1 da extensão `vector`. Fallback pg17 descrito em R-04.

**Testes**: JUnit 5 + AssertJ, `@WebMvcTest`, `ApplicationContextRunner`, Testcontainers 2.0.5
(Postgres real e mock Prism do contrato), teste de verificação do Modulith; pytest + schemathesis;
Vitest + Testing Library; `scripts/smoke.sh` contra o Compose.

**Plataforma-alvo**: contêineres Linux (Docker Compose); CI em `ubuntu-latest`.

**Tipo de projeto**: aplicação web em monorepo (backend + serviço auxiliar + SPA).

**Metas de desempenho**: tudo saudável em ≤ 3 min com imagens prontas (SC-001); mudança de estado
visível em ≤ 15 s (5 s de polling + até 2,5 s de sonda); CI de PR ≤ 10 min (SC-005).

**Restrições**: só variáveis de ambiente; nenhuma porta de métricas/Actuator publicada; contêineres
não-root; imagens com tag fixa; logs JSON sem segredos nem dados pessoais.

**Escala/escopo**: 4 serviços (+1 opcional), 1 endpoint público, 1 tela.

## Verificação da constituição

*Gate antes da Fase 0, revisto depois da Fase 1.*

| Princípio | Como o plano atende | Situação |
| --- | --- | --- |
| I. Fatias verticais, sistema rodando | `docker compose up` sobe tudo; smoke no CI prova ponta a ponta | ✅ |
| II. Núcleo fechado, portas | Módulo `platform` hexagonal; sondas são adaptadores da porta `ComponentProbe`; teste do Modulith no build. Sem tipos plugáveis (`trendgap.<tipo>.<id>.enabled`) nesta fatia | ✅ |
| III. Testes antes onde há lógica | TDD (test-writer) para agregação de status, derivação UP/DOWN com tempo limite e validação de configuração; contrato testado dos dois lados a partir do mesmo OpenAPI; Testcontainers com Postgres real | ✅ |
| IV. Probabilidade não vem do LLM | Sem modelo nem LLM nesta fatia | N/A |
| V. Custo sob controle | Nenhum provedor pago; controle na aplicação adiado (ADR-0014) | ✅ |
| VI. Segurança e privacidade | gitleaks no commit e no CI (com autoteste); CodeQL, Trivy, dependency-review; Actuator e métricas só na rede interna; papel de banco sem superusuário; contêineres não-root; falha de configuração não ecoa valores. Login fora do escopo | ✅ |
| VII. Observável e resiliente | Logs JSON ECS com `trace.id` nos dois serviços; OTel com W3C `traceparent`; ML fora → `DOWN`, nunca resposta inventada | ✅ |
| VIII. Simplicidade deliberada | Só o necessário; ML sem estado; **dependências novas condicionadas a ADR** (lista abaixo) | ✅ com condição |
| IX. Qualidade automática | lefthook (format/lint no pre-commit, tipos no pre-push, commitlint) e CI; main protegida por ruleset versionado | ✅ |

**Condição do gate**: os ADRs 0015–0020 precisam estar aceitos antes das tarefas que usam o que eles
cobrem. **Revisão pós-desenho**: nenhuma violação nova; tabela de complexidade vazia.

## ADRs novos (para o scribe)

| Proposta | Título | Bloqueia |
| --- | --- | --- |
| ADR-0015 | Observabilidade: serviço Python (OTel SDK, instrumentação FastAPI, OTLP, Prometheus, formatador JSON na stdlib) e appender OTel do Logback no backend | tarefas de observabilidade |
| ADR-0016 | Contrato Java ↔ Python: openapi-generator (restclient), schemathesis e mock Prism | cliente gerado e testes de contrato |
| ADR-0017 | Qualidade estática do backend: Spotless, Error Prone + NullAway/JSpecify, JaCoCo | esqueleto do backend |
| ADR-0018 | Ferramentas do frontend: TanStack Query, ESLint flat + typescript-eslint, Prettier, Vitest + Testing Library; TS 6.0 | esqueleto do frontend |
| ADR-0019 | PostgreSQL 18 + pgvector e papel de aplicação sem superusuário | Compose e Flyway |
| ADR-0020 | Pipeline de CI (paths-filter, `ci-ok`, gitleaks por binário, Trivy, dependency-review, ruleset) e imagens base fixas não-root | CI e Dockerfiles |

Detalhes e alternativas descartadas em [research.md](research.md#adrs-novos-necessários-para-o-scribe).

## Estrutura do projeto

### Documentação (esta feature)

```text
specs/001-fundacao-tecnica/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── README.md
│   ├── ml-service.openapi.yaml     # rascunho; vai para contracts/ na raiz
│   └── status-api.openapi.yaml
└── tasks.md                        # /speckit-tasks
```

### Código-fonte (raiz do repositório)

```text
backend/
├── .mvn/wrapper/maven-wrapper.properties
├── .mvn/jvm.config                         # --add-exports para Error Prone no JDK 25
├── mvnw, mvnw.cmd, pom.xml
├── Dockerfile
└── src/
    ├── main/java/io/github/gctoledo/trendgap/
    │   ├── TrendGapApplication.java
    │   └── platform/                       # único módulo Modulith desta fatia
    │       ├── package-info.java           # @ApplicationModule + @NullMarked
    │       ├── config/                     # records @ConfigurationProperties + FailureAnalyzer
    │       ├── observability/              # métrica trendgap.component.status, ajustes de tracing
    │       └── status/
    │           ├── domain/                 # ComponentName, HealthStatus, ComponentHealth, SystemStatus
    │           ├── application/            # StatusService, porta ComponentProbe
    │           └── adapter/
    │               ├── in/web/             # StatusController (GET /api/status)
    │               └── out/
    │                   ├── database/       # DatabaseProbe
    │                   └── mlservice/      # MlServiceProbe + configuração do cliente gerado
    ├── main/resources/
    │   ├── application.yaml
    │   ├── META-INF/spring.factories       # registro do FailureAnalyzer
    │   └── db/migration/V1__enable_vector.sql
    └── test/java/io/github/gctoledo/trendgap/
        ├── ModularityTest.java
        └── platform/...                    # unit (*Test), integração (*IT, Testcontainers), contrato (*ContractIT, Prism)

ml-service/
├── pyproject.toml, uv.lock, Dockerfile
├── src/trendgap_ml/
│   ├── __main__.py                         # valida Settings e chama uvicorn.run
│   ├── app.py                              # create_app()
│   ├── settings.py
│   ├── logging.py                          # formatador JSON (ECS) com trace.id
│   ├── telemetry.py                        # TracerProvider/MeterProvider/LoggerProvider; OTLP condicional
│   └── api/health.py
└── tests/                                  # test_settings, test_health, test_logging, test_contract

frontend/
├── package.json, pnpm-lock.yaml, tsconfig*.json, vite.config.ts, eslint.config.js, .prettierrc
├── Dockerfile
├── nginx/default.conf.template             # SPA + proxy /api → backend
└── src/
    ├── main.tsx, App.tsx
    ├── api/status.ts                       # fetch com timeout + tipos do contrato
    ├── status/useSystemStatus.ts           # TanStack Query, refetchInterval 5000
    ├── status/StatusPage.tsx
    └── **/*.test.tsx

contracts/
└── ml-service.openapi.yaml                 # fonte da verdade da fronteira Java ↔ Python

infra/
├── compose.yaml                            # postgres, backend, ml-service, frontend; profile observability (lgtm)
└── postgres/init/01-app-role.sh            # papel de aplicação + extensão vector

scripts/
├── smoke.sh
└── test-gitleaks.sh

.github/
├── workflows/ci.yml                        # changes → backend | ml-service | frontend | gitleaks | smoke → ci-ok
├── workflows/codeql.yml
├── dependabot.yml
└── rulesets/main.json

docs/operacao/repositorio.md                # proteção da main, secret scanning, push protection
.env.example                                # todas as variáveis, sem valores
lefthook.yml                                # + spotless, ruff, prettier/eslint; pre-push com mypy e tsc
```

**Decisão de estrutura**: monorepo com um diretório por componente, cada um com seu próprio
gerenciador (Maven wrapper, uv, pnpm) e seus próprios comandos de verificação (CLAUDE.md). A raiz
só guarda ferramentas transversais (commitlint, lefthook, gitleaks).

## Desenho por componente

### Backend

- **Configuração**: records `TrendGapProperties.Db` e `.Ml` com `@Validated`; `DataSource` montado a
  partir de `trendgap.db.*`. `FailureAnalyzer` próprio converte `trendgap.ml.base-url` →
  `TRENDGAP_ML_BASE_URL` e imprime só variável + regra (R-06). Hikari com `connection-timeout=2s`.
- **Status**: `StatusService` dispara as sondas em paralelo (threads virtuais,
  `spring.threads.virtual.enabled=true`), cada uma com tempo limite; exceção ou estouro → `DOWN`.
  Atualiza o gauge `trendgap.component.status`. Controller devolve `SystemStatus` (contrato em
  [status-api.openapi.yaml](contracts/status-api.openapi.yaml)).
- **Cliente do ML**: gerado no `generate-sources`; `ApiClient` recebe `RestClient` vindo do
  `RestClient.Builder` do Boot com `baseUrl` e timeouts de conexão/leitura = `TRENDGAP_ML_TIMEOUT`.
- **Actuator**: `management.server.port=8081`; `exposure.include=health,prometheus`;
  `show-details=never`, `show-components=never`; probes de liveness/readiness ligados; readiness =
  `readinessState` + `db`.
- **Logs e tracing**: ECS no console (R-08); Micrometer Tracing com bridge OTel; amostragem 1.0;
  OTLP só com `OTEL_EXPORTER_OTLP_ENDPOINT` (R-07). A sonda do ML registra uma linha de log por
  chamada (resultado e duração) para o smoke encontrar o `trace.id`.
- **Qualidade**: Spotless (google-java-format) em `verify`; Error Prone + NullAway como erro,
  excluindo código gerado; JaCoCo `check` com mínimo de 80% de linhas fora do gerado; Surefire para
  `*Test`, Failsafe para `*IT`.

### Serviço de ML

- `python -m trendgap_ml`: carrega `Settings` (falha → mensagem com variável e regra, saída 1),
  configura logging e telemetria, sobe uvicorn com `access_log=False`.
- `create_app()` monta FastAPI, rota `/health`, `/metrics` (exportador Prometheus) e instrumenta com
  OTel; middleware de log por requisição dentro do span (R-11).
- Testes: `Settings` (cada regra), `/health` via `TestClient`, formatador (JSON válido, campos
  obrigatórios, `trace.id` presente com span ativo), contrato (schemathesis + rotas do app ⇔ contrato).

### Frontend

- `getStatus()` com `AbortSignal.timeout(4000)`; `useSystemStatus` com `refetchInterval: 5000`,
  `refetchIntervalInBackground: true`, `retry: false`. Em erro, mapeia para backend `DOWN` e demais
  `UNKNOWN` com horário da tentativa (data-model §3).
- Dev: proxy do Vite `/api → http://localhost:8080`. Produção: nginx-unprivileged na porta 8080
  interna, `try_files` para a SPA e `location /api/ { proxy_pass http://backend:8080; }`; sem CORS.
- Testes: renderiza os três componentes; erro de rede mostra backend fora e demais desconhecidos;
  troca de estado entre consultas (timers falsos do Vitest).

### Infra

- `compose.yaml`: rede interna única; só `frontend` publica porta (`${TRENDGAP_WEB_PORT:-8080}:8080`)
  e, no profile `observability`, o Grafana (`3000`). Healthchecks: `pg_isready`; backend
  `/actuator/health/readiness` na 8081; ML `/health`; frontend `/` via `wget`. `depends_on` com
  `condition: service_healthy` (backend ← postgres; frontend ← backend; backend **não** depende do ML,
  para mostrar ML fora). `restart: unless-stopped`. Volume do Postgres em `/var/lib/postgresql`.
- Dockerfiles multi-stage, `USER` numérico não-root, tags fixas (R-13), `RUN --mount=type=cache`
  para Maven, uv e pnpm.
- `scripts/smoke.sh`: descrito no [quickstart](quickstart.md#4-smoke-automatizado-histórias-1-e-4-sc-002-sc-007).

### CI

- `ci.yml`: `changes` (paths-filter) → `backend` (`./mvnw -B spotless:check verify`), `ml-service`
  (`uv sync --frozen`, ruff check + format --check, mypy, pytest), `frontend` (`pnpm install
  --frozen-lockfile`, lint, format:check, typecheck, test, build), `gitleaks` (varredura do histórico
  do PR + `scripts/test-gitleaks.sh`), `dependency-review` (só PR), `smoke` (bake com cache gha, Trivy
  nas 3 imagens, `scripts/smoke.sh`) → `ci-ok`. Permissões mínimas (`contents: read`) por padrão;
  actions fixadas por SHA com comentário da versão.
- `codeql.yml`: matriz das 4 linguagens, `build-mode: none`, em push na main, PR e semanal.
- `dependabot.yml`: semanal para `maven` (/backend), `uv` (/ml-service), `npm` (/ e /frontend),
  `github-actions` (/), `docker` (/backend, /ml-service, /frontend) e `docker-compose` (/infra);
  agrupando minor/patch por ecossistema.

## Ordem sugerida (insumo para /speckit-tasks)

1. ADRs 0015–0020 (scribe) e `.env.example`.
2. Contrato do ML em `contracts/`.
3. Backend: esqueleto + qualidade + teste do Modulith → configuração com falha rápida (TDD) → Flyway +
   Testcontainers (confirma pg18) → status e sondas (TDD) → cliente gerado + contrato Prism →
   Actuator, logs e tracing.
4. ML: esqueleto + qualidade → settings (TDD) → `/health` + contrato → logs e telemetria.
5. Frontend: esqueleto + qualidade → status page (TDD do mapeamento de estado) → nginx.
6. Infra: Dockerfiles → Compose → smoke.
7. CI, CodeQL, Dependabot, lefthook, ruleset e `docs/operacao/repositorio.md`.

## Acompanhamento de complexidade

Nenhuma violação da constituição a justificar.
