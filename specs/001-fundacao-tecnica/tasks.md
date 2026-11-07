# Tarefas: Fundação técnica

**Entrada**: `specs/001-fundacao-tecnica/` (spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md)

**Formato**: `- [ ] T### [P?] [US?] Descrição com caminho de arquivo. Verificação: <comando>`

- **[P]**: independente (arquivos distintos, sem depender de tarefa incompleta).
- **[USn]**: história de spec.md. Setup, Fundação e Final não têm rótulo.
- **TDD**: onde há regra (validação de configuração, agregação de status, propagação de trace), a tarefa
  "Teste" (subagente `test-writer`, termina **vermelha**) vem antes da "Impl" (termina **verde**).
- Uma tarefa por sessão (CLAUDE.md). Código e commits em inglês; docs em português.
- Comandos: `./mvnw` (nunca `mvn`), `pnpm` (nunca `npm`), `uv`.
- **Exceção justificada ao teste primeiro**: T017 (migração Flyway) e T030 (Actuator) são infraestrutura sem lógica de
  domínio; o teste de integração acompanha a própria tarefa.

## Fase 0: Decisões (ADRs)

Executadas pelo subagente `scribe` com a skill `new-adr`, a partir de plan.md e research.md. Cada ADR antecede a primeira
tarefa que usa o que ele cobre (ver Dependências). Verificação comum: arquivo existe e contém `- Status: aceito` (formato de docs/adr/template.md).

- [x] T001 [P] ADR-0015 em `docs/adr/0015-*.md`: observabilidade do ML (OTel SDK, instrumentação FastAPI, OTLP, Prometheus, formatador JSON na stdlib) e appender OTel do Logback no backend, conforme research.md. Verificação: `ls docs/adr/0015-*.md && grep -q '^- Status: aceito' docs/adr/0015-*.md`
- [x] T002 [P] ADR-0016 em `docs/adr/0016-*.md`: contrato Java ↔ Python (openapi-generator restclient, schemathesis, mock Prism). Verificação: `ls docs/adr/0016-*.md && grep -q '^- Status: aceito' docs/adr/0016-*.md`
- [x] T003 [P] ADR-0017 em `docs/adr/0017-*.md`: qualidade estática do backend (Spotless, Error Prone + NullAway/JSpecify, JaCoCo). Verificação: `ls docs/adr/0017-*.md && grep -q '^- Status: aceito' docs/adr/0017-*.md`
- [x] T004 [P] ADR-0018 em `docs/adr/0018-*.md`: ferramentas do frontend (TanStack Query, ESLint flat + typescript-eslint, Prettier, Vitest + Testing Library, TS 6.0). Verificação: `ls docs/adr/0018-*.md && grep -q '^- Status: aceito' docs/adr/0018-*.md`
- [x] T005 [P] ADR-0019 em `docs/adr/0019-*.md`: PostgreSQL 18 + pgvector e papel de aplicação sem superusuário. Verificação: `ls docs/adr/0019-*.md && grep -q '^- Status: aceito' docs/adr/0019-*.md`
- [x] T006 [P] ADR-0020 em `docs/adr/0020-*.md`: pipeline de CI (paths-filter, `ci-ok`, gitleaks por binário, Trivy, dependency-review, ruleset) e imagens base fixas não-root. Verificação: `ls docs/adr/0020-*.md && grep -q '^- Status: aceito' docs/adr/0020-*.md`
- [x] T007 Atualizar `docs/decision-log.md` e `docs/adr/README.md` com ADR-0015 a 0020. Verificação: `for n in 15 16 17 18 19 20; do grep -q "00$n" docs/decision-log.md || echo faltou $n; done`

---

## Fase 1: Setup (esqueletos)

- [x] T008 [P] Criar esqueleto do backend: `backend/pom.xml` (Spring Boot 4.1.2, Java 25, web/actuator/validation/jdbc/flyway), Maven wrapper 3.3.4, `backend/.mvn/jvm.config`, `TrendGapApplication.java`. Verificação: `cd backend && ./mvnw -q -DskipTests compile`
- [x] T009 [P] Criar esqueleto do ML: `ml-service/pyproject.toml` (Python 3.13, ruff, mypy strict, pytest), `src/trendgap_ml/__init__.py`, teste trivial. Verificação: `cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest`
- [x] T010 [P] Criar esqueleto do frontend: Vite + React 19 + TS em `frontend/` (`package.json`, `tsconfig*.json`, `vite.config.ts`, `src/main.tsx`, `src/App.tsx`). Verificação: `cd frontend && pnpm install && pnpm typecheck && pnpm build`
- [x] T011 Qualidade do backend em `backend/pom.xml`: Spotless (google-java-format), Error Prone + NullAway + JSpecify (erro, excluindo gerado), JaCoCo configurado com `check` ≥ 80% de linhas, exclusões `TrendGapApplication` e código gerado, e `<skip>` controlado por `jacoco.check.skip=true` (o limite é ligado em T016, primeira tarefa com lógica), Surefire `*Test` / Failsafe `*IT`. Verificação: `cd backend && ./mvnw spotless:apply verify`
- [x] T012 Criar módulo `platform` com `package-info.java` (`@ApplicationModule` + `@NullMarked`) e `ModularityTest.java` (Spring Modulith 2.1.2). Verificação: `cd backend && ./mvnw test -Dtest=ModularityTest`
- [x] T013 [P] Qualidade do frontend: `eslint.config.js` (flat + typescript-eslint), `.prettierrc`, Vitest + Testing Library + jsdom, scripts `lint`, `format:check`, `typecheck`, `test` em `frontend/package.json`, teste trivial. Verificação: `cd frontend && pnpm lint && pnpm format:check && pnpm typecheck && pnpm test`
- [x] T014 [P] Mover o rascunho `specs/001-fundacao-tecnica/contracts/ml-service.openapi.yaml` para `contracts/ml-service.openapi.yaml` (fonte da verdade) com `git mv`, e atualizar o ponteiro em `specs/001-fundacao-tecnica/contracts/README.md`. Verificação: `test -f contracts/ml-service.openapi.yaml && grep -q 'operationId: getHealth' contracts/ml-service.openapi.yaml`

---

## Fase 2: Fundação (bloqueia todas as histórias)

**Configuração com falha rápida (backend)** — regras em data-model.md §4.

- [x] T015 Teste: `backend/src/test/java/io/github/gctoledo/trendgap/platform/config/TrendGapPropertiesTest.java` com `ApplicationContextRunner`: para cada variável de data-model §4 (`TRENDGAP_DB_URL` começa com `jdbc:postgresql://`; `DB_USERNAME`/`DB_PASSWORD` não vazias; `ML_BASE_URL` URL http/https absoluta; `ML_TIMEOUT` entre `100ms` e `5s`, padrão `2s`; `HTTP_PORT` 1–65535, padrão `8080`; `MANAGEMENT_PORT` 1–65535 e diferente da HTTP, padrão `8081`; `OTEL_EXPORTER_OTLP_ENDPOINT` URL absoluta ou ausente) cobrir ausente/inválida/válida; valor vazio de `OTEL_EXPORTER_OTLP_ENDPOINT` conta como ausente; o `ConfigFailureAnalyzer` é testado diretamente com a `BindException`/`ConfigurationPropertiesBindException` real do binding: a descrição cita a variável e a regra e nunca o valor. O caminho real (inicialização via `SpringApplication`) é coberto por T052. Deve ficar vermelha. Verificação: `cd backend && ./mvnw test -Dtest=TrendGapPropertiesTest` (falha esperada)
- [x] T016 Impl: `platform/config/TrendGapProperties.java` (records `Db`, `Ml`, `@Validated`), `ConfigFailureAnalyzer.java`, `META-INF/spring.factories`, `application.yaml` (Hikari `connection-timeout=2s`, threads virtuais); passa `jacoco.check.skip=false` no `pom.xml` (D1). Verificação: `cd backend && ./mvnw test -Dtest=TrendGapPropertiesTest && ./mvnw verify`
- [ ] T017 Flyway: `backend/src/main/resources/db/migration/V1__enable_vector.sql` (`CREATE EXTENSION IF NOT EXISTS vector`) + `FlywayMigrationIT` com Testcontainers 2.0.5 e imagem `pgvector/pgvector:0.8.7-pg18-trixie` (confirma pg18, R-04). Infraestrutura sem lógica de domínio: o IT acompanha a tarefa (exceção ao teste primeiro). Verificação: `cd backend && ./mvnw verify -Dit.test=FlywayMigrationIT`

**Configuração com falha rápida (ML)**

- [x] T018 [P] Teste: `ml-service/tests/test_settings.py`: `TRENDGAP_ML_PORT` inteiro 1–65535 (padrão 8000), `TRENDGAP_ML_LOG_LEVEL` ∈ {DEBUG, INFO, WARNING, ERROR} (padrão INFO), `OTEL_EXPORTER_OTLP_ENDPOINT` URL absoluta ou ausente; erro cita variável e regra, sem `input_value`; valor vazio de `OTEL_EXPORTER_OTLP_ENDPOINT` conta como ausente; `__main__` sai com código ≠ 0. Vermelha. Verificação: `cd ml-service && uv run pytest tests/test_settings.py` (falha esperada)
- [ ] T019 Impl: `ml-service/src/trendgap_ml/settings.py` (pydantic-settings, prefixo `TRENDGAP_ML_`) e `__main__.py` (valida antes do uvicorn, reformata `ValidationError`, saída 1). Verificação: `cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest tests/test_settings.py`

**Checkpoint**: backend e ML recusam configuração ruim; migração V1 validada.

---

## Fase 3: História 1 – Subir o sistema e ver a saúde (P1) 🎯 MVP

**Meta**: um comando sobe tudo; a página mostra backend, banco e ML e reage a queda/volta em ≤ 15 s.

**Teste independente**: quickstart §1–§4 (`scripts/smoke.sh`).

### Backend: status e sondas

- [ ] T020 [US1] Teste: `platform/status/application/StatusServiceTest.java` com sondas falsas: ordem fixa `backend`, `database`, `ml-service`; `backend` sempre `UP` sem sonda; sondas em paralelo; exceção ou estouro de tempo → `DOWN` sem afetar as outras; nenhuma mensagem de exceção no resultado; `checkedAt` UTC por componente. Vermelha. Verificação: `cd backend && ./mvnw test -Dtest=StatusServiceTest` (falha esperada)
- [ ] T021 [US1] Impl: `platform/status/domain/{ComponentName,HealthStatus,ComponentHealth,SystemStatus}.java`, `platform/status/application/{ComponentProbe,StatusService}.java` (threads virtuais). Verificação: `cd backend && ./mvnw test -Dtest='StatusServiceTest,ModularityTest'`
- [ ] T022 [P] [US1] Teste: `platform/status/adapter/out/database/DatabaseProbeTest.java`: `isValid` dentro do tempo limite → `UP`; conexão recusada ou `isValid` falso → `DOWN`. Vermelha. Verificação: `cd backend && ./mvnw test -Dtest=DatabaseProbeTest` (falha esperada)
- [ ] T023 [US1] Impl: `platform/status/adapter/out/database/DatabaseProbe.java`. Verificação: `cd backend && ./mvnw test -Dtest=DatabaseProbeTest`
- [ ] T024 [US1] Gerar o cliente do ML no `generate-sources` a partir de `contracts/ml-service.openapi.yaml` (openapi-generator 7.27, `restclient`, Jackson 3), excluído de Error Prone/JaCoCo. Verificação: `cd backend && ./mvnw -q generate-sources compile && ls target/generated-sources | head -1`
- [ ] T025 [US1] Teste: `platform/status/adapter/out/mlservice/MlServiceProbeTest.java` (`MockRestServiceServer`): 2xx com `{"status":"UP"}` → `UP`; corpo inesperado, 5xx, erro de rede ou estouro de `TRENDGAP_ML_TIMEOUT` → `DOWN`. Vermelha. Verificação: `cd backend && ./mvnw test -Dtest=MlServiceProbeTest` (falha esperada)
- [ ] T026 [US1] Impl: `platform/status/adapter/out/mlservice/{MlServiceProbe,MlClientConfig}.java` (`RestClient.Builder` do Boot, `baseUrl`, timeouts de conexão e leitura = `trendgap.ml.timeout`). Verificação: `cd backend && ./mvnw test -Dtest=MlServiceProbeTest`
- [ ] T027 [US1] Teste de contrato: `MlServiceContractIT.java` com Prism em Testcontainers servindo `contracts/ml-service.openapi.yaml`; a sonda real vê `UP`. Verificação: `cd backend && ./mvnw verify -Dit.test=MlServiceContractIT`
- [ ] T028 [US1] Teste: `platform/status/adapter/in/web/StatusControllerTest.java` (`@WebMvcTest`) conferindo por JSONPath campos `name`/`status`/`checkedAt`, enums, ordem fixa e ausência de campos extras (contrato em `specs/001-fundacao-tecnica/contracts/status-api.openapi.yaml`); sempre HTTP 200. Vermelha. Verificação: `cd backend && ./mvnw test -Dtest=StatusControllerTest` (falha esperada)
- [ ] T029 [US1] Impl: `platform/status/adapter/in/web/StatusController.java` (`GET /api/status`). Verificação: `cd backend && ./mvnw test -Dtest=StatusControllerTest`
- [ ] T030 [US1] Actuator em `application.yaml` e `ActuatorHealthIT.java`: porta 8081, `exposure.include=health,prometheus`, `show-details=never`, `show-components=never`, liveness/readiness ligados, readiness = `readinessState` + `db`; IT afirma corpo só `{"status":"UP|DOWN"}` e readiness `DOWN` com banco fora. Infraestrutura sem lógica de domínio (exceção ao teste primeiro). Verificação: `cd backend && ./mvnw verify -Dit.test=ActuatorHealthIT`

### ML: `/health`

- [ ] T031 [P] [US1] Teste: `ml-service/tests/test_health.py` (`TestClient`): `GET /health` → 200 com corpo exato `{"status":"UP"}`. Vermelha. Verificação: `cd ml-service && uv run pytest tests/test_health.py` (falha esperada)
- [ ] T032 [US1] Impl: `ml-service/src/trendgap_ml/api/health.py` e `app.py` (`create_app()`), `__main__.py` chama uvicorn com `access_log=False`. Verificação: `cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest`
- [ ] T033 [US1] Teste de contrato: `ml-service/tests/test_contract.py` (schemathesis contra `contracts/ml-service.openapi.yaml` + rotas do app ⇔ contrato). Verificação: `cd ml-service && uv run pytest tests/test_contract.py`

### Frontend: página de status

- [ ] T034 [P] [US1] Teste: `frontend/src/api/status.test.ts` e `frontend/src/status/StatusPage.test.tsx`: renderiza os três componentes com estado e horário e os rótulos "Saudável" (`UP`), "Fora do ar" (`DOWN`) e "Desconhecido" (`UNKNOWN`); falha de rede/5xx/4 s → backend `DOWN`, banco e ML `UNKNOWN`, horário da tentativa; troca de estado entre consultas com timers falsos (`refetchInterval` 5000). Vermelha. Verificação: `cd frontend && pnpm test` (falha esperada)
- [ ] T035 [US1] Impl: `frontend/src/api/status.ts` (tipos do contrato, `AbortSignal.timeout(4000)`, mapeamento de erro) e `frontend/src/status/useSystemStatus.ts` (TanStack Query: `refetchInterval: 5000`, `refetchIntervalInBackground: true`, `retry: false`). Verificação: `cd frontend && pnpm test -- status.test`
- [ ] T036 [US1] Impl: `frontend/src/status/StatusPage.tsx` (rótulos da interface: `UP` → "Saudável", `DOWN` → "Fora do ar", `UNKNOWN` → "Desconhecido"), `App.tsx`, `main.tsx` (QueryClientProvider), proxy `/api → http://localhost:8080` no Vite. Verificação: `cd frontend && pnpm lint && pnpm typecheck && pnpm test`

### Contêineres, Compose e smoke

- [ ] T037 [P] [US1] `backend/Dockerfile` multi-stage, `USER` numérico não-root, tag fixa, `RUN --mount=type=cache` para Maven. Verificação: `docker build -t trendgap-backend:test backend`
- [ ] T038 [P] [US1] `ml-service/Dockerfile` multi-stage com uv, não-root, tag fixa. Verificação: `docker build -t trendgap-ml:test ml-service`
- [ ] T039 [P] [US1] `frontend/Dockerfile` + `frontend/nginx/default.conf.template` (nginx-unprivileged na 8080, `try_files` para a SPA, `location /api/ { proxy_pass http://backend:8080; }`). Verificação: `docker build -t trendgap-frontend:test frontend`
- [ ] T040 [P] [US1] `infra/postgres/init/01-app-role.sh`: papel de aplicação sem superusuário e extensão `vector`. Verificação: `bash -n infra/postgres/init/01-app-role.sh && shellcheck infra/postgres/init/01-app-role.sh`
- [ ] T041 [US1] `infra/compose.yaml` e `.env.example` (variáveis de infra de data-model §4): rede interna, só `frontend` publica `${TRENDGAP_WEB_PORT:-8080}:8080`, healthchecks (`pg_isready`, readiness 8081, `/health`, `wget /`), `depends_on` com `service_healthy` (backend não depende do ML), `restart: unless-stopped`. Verificação: `cp .env.example .env && docker compose -f infra/compose.yaml config -q`
- [ ] T042 [US1] `scripts/smoke.sh` (parte de status): sobe com `--wait`; `GET /api/status` com os três `UP`; para o ML e exige `DOWN` em ≤ 15 s; religa e exige `UP` em ≤ 15 s; para o postgres e exige `database=DOWN` em ≤ 15 s; sobe de novo e exige `UP` em ≤ 15 s; derruba no fim (`--keep` mantém); falha se a senha do banco (`POSTGRES_PASSWORD`/`TRENDGAP_DB_PASSWORD` do `.env`) ou uma URL com credenciais (`://usuario:senha@`) aparecer em `docker compose logs` do backend ou do ML. Verificação: `scripts/smoke.sh`

**Checkpoint**: MVP funcional; História 1 testável de forma independente.

---

## Fase 4: História 2 – Verificação automática em todo push e PR (P1)

**Meta**: formatação, lint, tipos, testes, segredos e análise de segurança em todo push/PR; merge só com `ci-ok` verde.

**Teste independente**: quickstart §8.

- [ ] T043 [P] [US2] `scripts/test-gitleaks.sh`: autoteste que gera um segredo falso em arquivo temporário (nunca versionado), roda gitleaks e exige falha apontando o arquivo; sem o segredo exige sucesso. Verificação: `scripts/test-gitleaks.sh`
- [ ] T044 [P] [US2] Raiz: estender o `lefthook.yml` existente (hoje: pre-commit gitleaks; commit-msg `pnpm exec commitlint`) com pre-commit spotless, ruff, prettier/eslint e pre-push mypy e tsc. O `package.json` (pnpm) e o `commitlint.config.mjs` da raiz já existem e não são recriados. Verificação: `lefthook run pre-commit && lefthook run pre-push && echo 'bad message' | pnpm exec commitlint; test $? -ne 0`
- [ ] T045 [US2] `.github/workflows/ci.yml` (parte 1): permissões `contents: read`, actions fixadas por SHA, todo job com `timeout-minutes` e, como último passo `if: always()`, a duração do job em `$GITHUB_STEP_SUMMARY` (início registrado no primeiro passo); job `changes` (paths-filter) e jobs `backend` (`./mvnw -B spotless:check verify`), `ml-service` (`uv sync --frozen`, ruff check + format --check, mypy, pytest), `frontend` (`pnpm install --frozen-lockfile`, lint, format:check, typecheck, test, build). Verificação: `python3 -c "import yaml;yaml.safe_load(open('.github/workflows/ci.yml'))" && grep -c '^  [a-z-]*:$' .github/workflows/ci.yml`
- [ ] T046 [US2] `ci.yml` (parte 2): jobs `gitleaks` (histórico do PR + `scripts/test-gitleaks.sh`) e `dependency-review` (só PR, falha em alta/crítica). Verificação: `python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/ci.yml'));assert {'gitleaks','dependency-review'}<=set(d['jobs'])"`
- [ ] T047 [US2] `ci.yml` (parte 3): job `smoke` (bake com cache gha, Trivy nas 3 imagens falhando em HIGH/CRITICAL e avisando MEDIUM/LOW, `scripts/smoke.sh`). Verificação: `python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/ci.yml'));assert 'smoke' in d['jobs']"`
- [ ] T048 [US2] `ci.yml` (parte 4): job agregador `ci-ok` (`needs` de todos, `if: always()`, falha se algum `needs` for `failure` ou `cancelled`; `skipped` por filtro de caminho é aceito). Verificação: `python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/ci.yml'));assert d['jobs']['ci-ok']['if']=='always()';assert all('timeout-minutes' in j for j in d['jobs'].values())"`
- [ ] T049 [P] [US2] `.github/workflows/codeql.yml`: matriz java-kotlin, python, javascript-typescript, actions; `build-mode: none`; push na main, PR e semanal; `timeout-minutes` e duração no resumo, como no CI. Verificação: `python3 -c "import yaml;d=yaml.safe_load(open('.github/workflows/codeql.yml'));assert all('timeout-minutes' in j for j in d['jobs'].values())"`

**Checkpoint**: pipeline completo no repositório; a proteção da main é ligada na Fase final.

---

## Fase 5: História 3 – Configuração só por variáveis de ambiente (P2)

**Meta**: `.env.example` completo e falha rápida comprovada em cada componente.

**Teste independente**: quickstart §5.

- [ ] T050 [US3] Teste: `scripts/check-env-example.sh`: extrai as variáveis `TRENDGAP_*`/`OTEL_*`/`POSTGRES_*` de `application.yaml`, `settings.py` e `infra/compose.yaml` e exige cada uma em `.env.example`, com comentário de descrição e obrigatoriedade e valor vazio ou fictício. Vermelho até T051. Verificação: `scripts/check-env-example.sh` (falha esperada)
- [ ] T051 [US3] Completar `.env.example` com todas as variáveis de data-model §4 (backend, ML, infra), descrição, obrigatoriedade, padrão e valor vazio/fictício. Verificação: `scripts/check-env-example.sh && gitleaks detect --no-git --source .env.example`
- [ ] T052 [US3] `scripts/check-failfast.sh`: roda `docker compose run --rm` com `TRENDGAP_ML_BASE_URL=` vazia, `TRENDGAP_ML_TIMEOUT=10m` e `TRENDGAP_ML_PORT=abc`; exige saída ≠ 0, nome da variável e regra na saída, e que o valor informado não apareça; para o backend isso exercita o `ConfigFailureAnalyzer` pelo caminho real (`SpringApplication`), conferindo o bloco "APPLICATION FAILED TO START". Verificação: `scripts/check-failfast.sh`

**Checkpoint**: SC-006 verificável por script.

---

## Fase 6: História 4 – Logs estruturados, trace e métricas (P2)

**Meta**: logs JSON com `trace.id`, mesmo trace nos dois serviços, métricas internas e pilha opcional.

**Teste independente**: quickstart §4 (trace) e §6.

### Backend

- [ ] T053 [US4] Teste: `platform/observability/TracePropagationIT.java`: requisição a `/api/status` com `traceparent` W3C; um stub HTTP local (`com.sun.net.httpserver`) no lugar do ML recebe `traceparent` com o mesmo trace id; o log JSON do backend contém esse `trace.id`; cada linha do log é JSON válido com data/hora, nível, componente, mensagem. Vermelha. Verificação: `cd backend && ./mvnw verify -Dit.test=TracePropagationIT` (falha esperada)
- [ ] T054 [US4] Impl: Micrometer Tracing com bridge OTel (amostragem 1.0), logs ECS no console em `application.yaml`, uma linha de log por chamada da `MlServiceProbe` (resultado e duração, sem segredos). Verificação: `cd backend && ./mvnw verify -Dit.test=TracePropagationIT`
- [ ] T055 [P] [US4] Teste: `platform/observability/ComponentStatusMetricTest.java`: gauge `trendgap.component.status` por componente (1 = UP, 0 = DOWN) e contagem/duração de requisições no endpoint Prometheus. Vermelha. Verificação: `cd backend && ./mvnw test -Dtest=ComponentStatusMetricTest` (falha esperada)
- [ ] T056 [US4] Impl: `platform/observability/ComponentStatusMetrics.java` ligado ao `StatusService`; `micrometer-registry-prometheus` em `/actuator/prometheus`. Verificação: `cd backend && ./mvnw test -Dtest=ComponentStatusMetricTest`
- [ ] T057 [US4] Teste: `OtlpConditionalTest.java` com `ApplicationContextRunner`: sem `OTEL_EXPORTER_OTLP_ENDPOINT` (ausente ou vazio) nenhum exportador OTLP é criado; com URL, é criado. Vermelha. Verificação: `cd backend && ./mvnw test -Dtest=OtlpConditionalTest` (falha esperada)
- [ ] T058 [US4] Impl: configuração condicional da exportação OTLP (`@ConditionalOnProperty`/propriedade equivalente) em `platform/observability/`. Verificação: `cd backend && ./mvnw test -Dtest=OtlpConditionalTest`

### ML

- [ ] T059 [P] [US4] Teste: `ml-service/tests/test_logging.py`: cada linha é JSON válido (ECS) com `@timestamp`, nível, componente, mensagem; `trace.id` presente com span ativo; nenhum campo de segredo. Vermelha. Verificação: `cd ml-service && uv run pytest tests/test_logging.py` (falha esperada)
- [ ] T060 [US4] Impl: `ml-service/src/trendgap_ml/logging.py` (formatador JSON na stdlib). Verificação: `cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest tests/test_logging.py`
- [ ] T061 [US4] Teste: `ml-service/tests/test_telemetry.py`: requisição com `traceparent` gera log com o mesmo `trace.id`; `/metrics` expõe contagem e duração de requisições; OTLP só com `OTEL_EXPORTER_OTLP_ENDPOINT`. Vermelha. Verificação: `cd ml-service && uv run pytest tests/test_telemetry.py` (falha esperada)
- [ ] T062 [US4] Impl: `ml-service/src/trendgap_ml/telemetry.py` (Tracer/Meter/LoggerProvider, instrumentação FastAPI, exportador Prometheus em `/metrics`, middleware de log por requisição dentro do span) ligado em `app.py`. Verificação: `cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest`

### Infra

- [ ] T063 [US4] Profile `observability`: serviço `lgtm` em `infra/compose.yaml` (imagem LGTM com tag fixa, `profiles: [observability]`, Grafana na 3000, OTLP HTTP 4318 só na rede interna), `infra/observability/prometheus.yaml` (scrape de `backend:8081` em `/actuator/prometheus` e `ml-service:8000/metrics`, montado na imagem) e `infra/observability/README.md` (como ligar). `backend` e `ml-service` recebem `OTEL_EXPORTER_OTLP_ENDPOINT: ${OTEL_EXPORTER_OTLP_ENDPOINT:-}` no `environment` (vazio = sem exportação); com o profile ativo o usuário define `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318` no `.env` (exemplo comentado em `.env.example`). Nenhuma porta de métricas publicada. Verificação: `docker compose -f infra/compose.yaml --profile observability config -q && ! docker compose -f infra/compose.yaml --profile observability config | grep -E 'published: "?(8081|8000)' && test -f infra/observability/prometheus.yaml`
- [ ] T064 [US4] Estender `scripts/smoke.sh`: `traceparent` W3C gerado, mesmo `trace.id` nos logs do backend e do ML (SC-007); `/actuator/health`, `/actuator/prometheus` e o `/metrics` do ML inacessíveis pelo host; **opcional** (só com `SMOKE_OBSERVABILITY=1` e o profile ativo com `OTEL_EXPORTER_OTLP_ENDPOINT` apontando para o `lgtm`): consulta o mesmo `trace.id` no Tempo via Grafana (3000) e exige spans dos dois serviços; sem o profile o passo é pulado e avisa. Verificação: `scripts/smoke.sh` e, com o profile, `SMOKE_OBSERVABILITY=1 scripts/smoke.sh`

**Checkpoint**: História 4 verificável; sistema continua subindo sem o profile.

---

## Fase 7: História 5 – Atualizações automáticas de dependências (P3)

**Meta**: PRs semanais por ecossistema, sob o mesmo `ci-ok`.

**Teste independente**: quickstart §9.

- [ ] T065 [P] [US5] `.github/dependabot.yml`: semanal para `maven` (/backend), `uv` (/ml-service), `npm` (/ e /frontend), `github-actions` (/), `docker` (/backend, /ml-service, /frontend) e `docker-compose` (/infra); agrupar minor/patch por ecossistema. Verificação: `python3 -c "import yaml;d=yaml.safe_load(open('.github/dependabot.yml'));assert len(d['updates'])>=9"`

---

## Fase final: documentação e proteção da main

**Condição do gate (plan.md)**: ADR-0015 a 0020 (T001–T007) já estão aceitos antes desta fase.

### Documentação (scribe)

- [ ] T066 Atualizar `CLAUDE.md`: estrutura real (`backend/`, `ml-service/`, `frontend/`, `contracts/`, `infra/`, `scripts/`), comandos de verificação confirmados, `scripts/smoke.sh`, `scripts/test-gitleaks.sh`, `lefthook`. Verificação: `grep -q 'scripts/smoke.sh' CLAUDE.md && grep -q 'ci-ok' CLAUDE.md`
- [ ] T067 Criar/atualizar `README.md` com "Como rodar": pré-requisitos, `cp .env.example .env`, `docker compose -f infra/compose.yaml up --wait`, URL da página de status, profile `observability`, `scripts/smoke.sh`, verificação por componente. Verificação: `grep -q 'compose.yaml' README.md && grep -q 'observability' README.md`

### Proteção da main (check obrigatório `ci-ok`)

- [ ] T068 [P] `.github/rulesets/main.json` (merge só por PR, status check obrigatório `ci-ok`, bloqueio de force push e de exclusão da main) e `docs/operacao/repositorio.md` (passo a passo do ruleset, secret scanning e push protection). Verificação: `python3 -c "import json;d=json.load(open('.github/rulesets/main.json'));assert 'ci-ok' in json.dumps(d)" && test -f docs/operacao/repositorio.md`
- [ ] T069 Aplicar o ruleset e ligar secret scanning + push protection no GitHub (**ação externa: pedir confirmação ao usuário antes de executar**; sem push/PR sem pedido explícito). Verificação: `gh api repos/:owner/:repo/rulesets --jq '.[].name'` lista o ruleset e `gh api repos/:owner/:repo --jq '.security_and_analysis.secret_scanning.status'` retorna `enabled`
- [ ] T070 Validar a proteção (quickstart §8, **só com pedido explícito para abrir PR**): PR limpo com todos os checks verdes em ≤ 10 min; PR com segredo falso com `gitleaks` e `ci-ok` vermelhos e merge bloqueado; commit local com segredo falso recusado pelo hook. Verificação: `gh pr checks <n>` e `gh pr view <n> --json mergeStateStatus`
- [ ] T071 Rodar a validação final do quickstart §1–§9 em clone limpo (3 tentativas de subida ≤ 3 min, SC-001) e marcar o checklist. SC-008 (validação manual): seguir o README do zero e ver a página de status em até 15 minutos. Verificação: `scripts/smoke.sh && scripts/check-env-example.sh && scripts/check-failfast.sh`

---

## Dependências e ordem

- **Fase 0**: T001–T006 são [P] e independentes; T007 depende de T001–T006. Cada ADR precisa estar aceito antes da primeira tarefa que usa o que ele cobre:
  - ADR-0017 (T003) → T011 (qualidade do backend); ADR-0018 (T004) → T013 (qualidade do frontend);
  - ADR-0019 (T005) → T017 (Flyway/pgvector) e T040 (papel de aplicação); ADR-0016 (T002) → T024 (cliente gerado);
  - ADR-0020 (T006) → T037 (Dockerfiles, imagens fixas não-root) e T043 (pipeline de CI); ADR-0015 (T001) → T053 (observabilidade).
  - Mais simples: concluir a Fase 0 inteira antes da Fase 1.
- Setup (T008–T014) → Fundação (T015–T019) → histórias. T008 → T011 → T012; T010 → T013; T014 independe dos demais (exceto ADR-0016 antes de T024).
- Dentro de cada par Teste → Impl, o teste vem sempre antes (exceções justificadas: T017 e T030).
- **US1** depende da Fundação. T024 depende de T014 e T011; T027 depende de T026; T042 depende de T037–T041.
- **US2** pode começar após T013 e T011, mas T047 (smoke) depende de T042; T048 depende de T045–T047.
- **US3** depende de T041 (compose) e T019; **US4** depende de T026, T032 e T041; **US5** é independente (após existir `/infra`, T041, para o ecossistema docker-compose).
- **Final**: T066–T067 dependem do estado final do código; T069 depende de T048 e T068; T070 depende de T069.

## Paralelismo (exemplos)

- Fase 0: T001–T006 juntas; depois T007.
- Setup: T008, T009, T010, T014 juntas; depois T011 | T013.
- US1: T022 | T025 | T031 | T034 podem rodar em paralelo (cada uma em seu componente); T037 | T038 | T039 | T040 em paralelo.
- US4: T055 | T059 em paralelo; T053 e T061 em sequência com suas impls.

## Estratégia de implementação

1. Fase 0 (ADRs) primeiro; MVP = Setup + Fundação + US1 (smoke verde).
2. Depois US2 (CI) para proteger o resto; US3 e US4 em seguida; US5 a qualquer momento.
3. Fase final fecha docs e a proteção da main.
