# Pesquisa (Fase 0): Fundação técnica

**Feature**: 001-fundacao-tecnica · **Data**: 2026-11-03

As decisões já registradas em `docs/decision-log.md` e `docs/adr/` (ADR-0001 a 0014, P-13, P-14)
não são rediscutidas aqui. Este documento registra só o que precisou ser verificado e as escolhas
novas que dependem dessa verificação. Itens marcados **[ADR]** exigem ADR antes do uso (princípio VIII).

Versões consultadas em 2026-11-03 direto nos registros (Maven Central, Docker Hub, PyPI, npm,
GitHub Releases). Dependabot assume as atualizações daqui em diante.

---

## R-01. Spring Boot: último patch da linha 4.1

- **Decisão**: Spring Boot **4.1.2**. Ele gerencia Flyway 12.4.0, Testcontainers 2.0.5,
  Micrometer Tracing 1.7.2, OpenTelemetry 1.62.0, Jackson 3.1.7 e driver PostgreSQL 42.7.14.
- **Justificativa**: 4.1.2 é o patch mais recente da 4.1; a 4.2 ainda está em RC1.
- **Alternativas**: 4.2.0-RC1 (descartada: pré-lançamento).

## R-02. Maven e Maven Wrapper

- **Decisão**: Maven **3.9.16** via Maven Wrapper **3.3.4** (`distributionType=only-script`).
- **Justificativa**: último 3.9.x; o Maven 4 ainda está em RC (ADR-0002).

## R-03. Spring Modulith compatível com Boot 4.1

- **Decisão**: `spring-modulith-bom` **2.1.2** (o Boot não gerencia o Modulith).
- **Justificativa**: a linha 2.1 acompanha o Boot 4.1; a 2.2 está em RC.
- **Resolvido (T012)**: `ApplicationModules.verify()` sobe com Boot 4.1.2 + Modulith 2.1.2 sem conflito.

## R-04. PostgreSQL 18 com pgvector: Flyway e Testcontainers

- **Decisão**: imagem **`pgvector/pgvector:0.8.7-pg18-trixie`** no Compose e nos testes.
- **Verificação**:
  - Flyway 12.4.0 (`flyway-database-postgresql`): o bytecode de `PostgreSQLDatabase` chama
    `recommendFlywayUpgradeIfNecessaryForMajorVersion("18")`, ou seja, o 18 é a versão máxima
    testada; não há aviso de versão não suportada. A documentação da Redgate também lista 12–18.
  - Testcontainers 2.0.5: o módulo `testcontainers-postgresql` (classe
    `org.testcontainers.postgresql.PostgreSQLContainer`) aceita imagem compatível via
    `DockerImageName.parse(...).asCompatibleSubstituteFor("postgres")`. A mudança do PG 18 que
    quebra setups antigos é o layout do PGDATA (`/var/lib/postgresql/18/docker`); o Testcontainers
    não monta volume de dados, então não é afetado.
  - Compose: o volume nomeado DEVE ser montado em `/var/lib/postgresql` (e não em
    `/var/lib/postgresql/data`), por causa do novo layout.
- **Resolvido (T017)**: `FlywayMigrationIT` passa com `pgvector/pgvector:0.8.7-pg18-trixie` (PG 18, Testcontainers 2.0.5, migração idempotente); fallback pg17 não necessário.
- **Fallback**: se o primeiro teste de integração (tarefa de Flyway + Testcontainers) falhar por
  causa do PG 18, trocar para `pgvector/pgvector:0.8.7-pg17-trixie` nos dois lugares e registrar
  no ADR do banco.
- **Alternativas**: pg17 (fica como fallback).
- **Achado relevante**: a extensão `vector` **não** é *trusted* (`vector.control` não tem
  `trusted = true`); só superusuário a cria. Ver R-05.

## R-05. Menor privilégio no banco versus `CREATE EXTENSION vector`

- **Decisão**: dois papéis. O superusuário da imagem (`POSTGRES_USER`/`POSTGRES_PASSWORD`) só existe
  dentro do contêiner do Postgres e é usado por um script de inicialização
  (`infra/postgres/init/01-app-role.sh`) que cria o banco `trendgap`, o papel de aplicação
  `TRENDGAP_DB_USERNAME` (LOGIN, sem SUPERUSER/CREATEDB/CREATEROLE, dono do schema `public` do banco)
  e executa `CREATE EXTENSION IF NOT EXISTS vector`. A migração Flyway `V1__enable_vector.sql` usa
  `CREATE EXTENSION IF NOT EXISTS vector;`: no Compose é no-op (já existe); no Testcontainers (usuário
  superusuário de teste) cria de fato, o que mantém a migração como fonte verificável.
- **Justificativa**: princípio VI (menor privilégio) sem abrir mão da migração pedida.
- **Alternativas**: backend com o superusuário (descartada: viola o princípio VI).

## R-06. Configuração tipada com falha rápida sem ecoar valores

- **Decisão**: `@ConfigurationProperties` em records com `@Validated` (Jakarta Validation) sob o
  prefixo `trendgap.*`, ligados por variáveis `TRENDGAP_*` (binding relaxado do Boot). Um
  `FailureAnalyzer` próprio, ordenado antes do `BindValidationFailureAnalyzer` do Boot, traduz o
  nome da propriedade para o nome da variável de ambiente e imprime só **variável + regra**.
- **Verificação**: o analisador padrão do Boot imprime `Value: "<valor>"` na falha de validação,
  o que ecoaria segredos (FR-013). Por isso o analisador próprio é obrigatório.
- **Teste**: unitário do analisador (mensagem contém o nome da variável e a regra, não contém o
  valor) e teste de inicialização com `ApplicationContextRunner` para cada variável obrigatória.

## R-07. Exportação OTLP ligada só com endpoint configurado (backend)

- **Decisão**: `spring-boot-starter-opentelemetry` (novo no Boot 4) + `micrometer-registry-prometheus`
  + `micrometer-registry-otlp`. O endpoint vem de `OTEL_EXPORTER_OTLP_ENDPOINT` (a mesma variável que o
  SDK Python lê nativamente), mapeado no `application.yaml` para as propriedades de exportação de
  traces, métricas e logs.
- **A confirmar na tarefa de observabilidade**: (a) os nomes exatos das propriedades de exportação
  OTLP no Boot 4.1 (`management.opentelemetry.tracing.export.otlp.endpoint` e correlatas, que
  substituíram `management.otlp.tracing.endpoint`); (b) que o exportador **não** é criado quando a
  variável está ausente. Se um valor vazio (`${OTEL_EXPORTER_OTLP_ENDPOINT:}`) ativar o exportador,
  usar `@ConditionalOnProperty`/perfil ou `management.*.export.enabled=${...}` derivado.
- **Resolvido (T058)**: o exportador OTLP existia por padrão (`management.*.export.otlp.enabled=true`); `OtlpExportSwitch` (EnvironmentPostProcessor) desliga os de traces, logs e métricas quando `OTEL_EXPORTER_OTLP_ENDPOINT` está ausente/vazia.
- **Exportação de logs para a pilha local**: appender `opentelemetry-logback-appender-1.0` do BOM de
  instrumentação OTel (2.33.x) instalado pelo starter. **[ADR]** junto da observabilidade (ver ADRs).
- **Amostragem**: `management.tracing.sampling.probability=1.0` fixo nesta fatia (volume local baixo;
  garante trace id em todo log).

## R-08. Logs JSON nativos do Boot

- **Decisão**: `logging.structured.format.console=ecs`, `logging.structured.ecs.service.name=backend`.
- **Justificativa**: o formato ECS já inclui `@timestamp`, `log.level`, `service.name`, `message`,
  `trace.id` e `span.id` (vindos do MDC do Micrometer Tracing), cobrindo FR-015 sem dependência.
- **Resolvido (T054)**: o ECS do Boot 4.1 emite `traceId`/`spanId`; `logging.structured.json.rename` os converte para `trace.id`/`span.id`. O contexto de trace não atravessa `newVirtualThreadPerTaskExecutor` sozinho: `StatusService` usa `ContextExecutorService` (context-propagation).
- O serviço de ML usa os mesmos nomes de campo (ver data-model.md, "Formato de log"), para que a
  consulta na pilha local seja igual nos dois.

## R-09. Cliente Java gerado do OpenAPI com Boot 4 (Jackson 3)

- **Decisão**: `openapi-generator-maven-plugin` **7.27.x** (ou o 7.x mais recente na tarefa),
  gerador `java`, `library=restclient`, `useSpringBoot4=true`, `useJackson3=true`,
  `openApiNullable=false`, saída em `target/generated-sources/openapi` no pacote
  `io.github.gctoledo.trendgap.platform.mlclient`. O `RestClient` do `ApiClient` gerado é construído
  a partir do `RestClient.Builder` auto-configurado do Boot (é ele que injeta `traceparent` e tem
  observação/métricas).
- **Verificação**: `useSpringBoot4`/`useJackson3` existem desde a 7.16; a correção que registra os
- **Resolvido (T024)**: openapi-generator-maven-plugin 7.27.0 gera o cliente restclient com Jackson 3 e compila no Boot 4.1.2.
  conversores padrão com Jackson 3 no `ApiClient` do restclient entrou em ago/2026 (PR #24640), então
  usar versão ≥ a que contém essa correção.
- **NullAway/Error Prone**: o código gerado fica fora da análise (`-XepExcludedPaths:.*/generated-sources/.*`
  e `NullAway:AnnotatedPackages=io.github.gctoledo.trendgap` com `UnannotatedSubPackages` para
  `...platform.mlclient`). JaCoCo também exclui o pacote gerado.

## R-10. Testes de contrato dos dois lados a partir do mesmo arquivo

- **Python** — **[ADR]** `schemathesis` **4.x**: carrega `contracts/ml-service.openapi.yaml` e
  executa casos contra o app ASGI em memória, validando status, `Content-Type` e esquema da
  resposta. Complementa um teste simples que garante que toda rota do app existe no contrato (e
  vice-versa). **A confirmar na tarefa**: a API da 4.x para usar um esquema de arquivo contra um app
  ASGI (`schemathesis.openapi.from_path(...)` + chamada com o app).
  - Alternativa: `openapi-core` validando respostas manualmente (descartada: exige escrever os
    casos à mão; o schemathesis gera a partir do contrato).
- **Java** — **[ADR]** mock **Prism** (`stoplight/prism:5.14.3`) em `GenericContainer` do
  - **Resolvido (T027)**: a tag 5.16.0 não existe no Docker Hub e a 5.15.10 falha em `mock` (`cluster.isPrimary`); usada a 5.14.3. O `ApiClient` gerado ignora o `baseUrl` do `RestClient`: é preciso `setBasePath` (feito em `MlClientConfig`).
  Testcontainers, servindo o mesmo arquivo. O cliente gerado chama o stub; o Prism valida a
  requisição contra o contrato e responde com os exemplos do contrato. Sem dependência Java nova.
  - Alternativa: WireMock + `swagger-request-validator-wiremock` (descartada: dependência Java a
    mais e stubs escritos à mão, que podem divergir do contrato).
- **Teste de timeout do cliente**: `com.sun.net.httpserver.HttpServer` do JDK com resposta atrasada
  (sem dependência), verificando que o ML lento conta como DOWN.

## R-11. Observabilidade no serviço de ML

- **Decisão** — **[ADR]**:
  - `opentelemetry-sdk` 1.45.x, `opentelemetry-instrumentation-fastapi` 0.66bX (extrai `traceparent`
    W3C; propagador padrão `tracecontext,baggage`), `opentelemetry-exporter-otlp-proto-http` 1.45.x
    (traces, métricas e logs; criado só se `OTEL_EXPORTER_OTLP_ENDPOINT` estiver definido) e
    `opentelemetry-exporter-prometheus` 0.66bX (expõe `/metrics` na rede interna).
  - Logs JSON com **formatador próprio sobre `logging` da stdlib** (≈30 linhas), que lê
    `trace_id`/`span_id` do span corrente. Sem `structlog` nem `python-json-logger`.
  - Log de acesso do uvicorn desligado; um middleware próprio registra uma linha por requisição
    (método, rota, status, duração) **dentro** do span do OTel. **A confirmar na tarefa**: ordem dos
    middlewares (o do OTel precisa ser o mais externo).
- **Alternativas**: `structlog` (descartada: dependência a mais para o que a stdlib resolve);
  `prometheus-fastapi-instrumentator` (descartada: duplicaria as métricas que o OTel já produz).

## R-12. Frontend: versões e compatibilidade

- **Decisão**: Node **24.22.2** (LTS), pnpm 12.16.1 (mesmo da raiz), React 19.3, Vite 8.3,
  `@vitejs/plugin-react` 6.2, Vitest 5.1 (peer `vite ^8`), Testing Library 16.3 + jsdom, TanStack
  Query 5.105, ESLint 10 flat + `typescript-eslint` 8.72 (`strictTypeChecked`), Prettier 3.9.
- **TypeScript 6.0.3, não 7.0**: `typescript-eslint` 8.72 declara peer `typescript >=4.8.4 <6.1.0`.
  Subir para o TS 7 quando o `typescript-eslint` suportar (Dependabot vai propor; o CI barra se quebrar).
- Mock de rede nos testes com `vi.stubGlobal('fetch', ...)`; MSW descartado (YAGNI).
- `frontend/` é um pacote pnpm independente com lockfile próprio (sem `pnpm-workspace.yaml`); a raiz
  continua só com ferramentas de commit.

## R-13. Imagens base (versão fixa, não-root)

| Uso | Imagem |
| --- | --- |
| Build backend | `eclipse-temurin:25.0.5_8-jdk-noble` |
| Runtime backend | `eclipse-temurin:25.0.5_8-jre-noble` |
| Build/runtime ML | `python:3.13.18-slim-trixie` + binário de `ghcr.io/astral-sh/uv:0.13.7` |
| Build frontend | `node:24.22.2-alpine3.24` |
| Runtime frontend | `nginxinc/nginx-unprivileged:1.30.6-alpine3.24` (linha estável) |
| Banco | `pgvector/pgvector:0.8.7-pg18-trixie` |
| Observabilidade (profile) | `grafana/otel-lgtm:0.39.0` |
| Mock de contrato (testes) | `stoplight/prism:5.14.3` |

- **Healthchecks sem instalar ferramentas**: ML usa `python -c` com `urllib`; nginx usa `wget` do
  busybox; Postgres usa `pg_isready`. **A confirmar na tarefa do Dockerfile do backend**: se a imagem
  JRE noble traz `curl`; se não, usar um healthcheck em Java de arquivo único
  (`java HealthCheck.java <url>`), sem pacote extra.
  - **Resolvido (T037/T041)**: a JRE noble não traz `curl`/`wget` nem `jdk.compiler` (modo arquivo-fonte falha); o `HealthCheck.java` é compilado no estágio de build e executado com `java -cp /app HealthCheck <url>`. O Dockerfile do backend usa a raiz do repositório como contexto (o cliente é gerado de `contracts/`).
- **Duplicidade conhecida**: a tag do Postgres aparece no `compose.yaml` e no teste Java. O Dependabot
  só atualiza a do Compose; a tarefa de testes de integração lê a imagem de uma propriedade Maven
  (`pgvector.image`) para concentrar a edição num lugar do backend.

## R-14. CI dentro de 10 minutos

- **Decisão**:
  - `dorny/paths-filter` v4 num job `changes`; jobs `backend`, `ml-service`, `frontend` só rodam com
    mudança no seu caminho, em `contracts/` ou em `.github/workflows/`. `gitleaks` sempre roda.
    `smoke` roda com mudança em qualquer componente, `contracts/`, `infra/` ou `scripts/`.
  - `ci-ok` com `if: always()` e `needs` de todos; falha se algum resultado for `failure` ou
    `cancelled` (`skipped` conta como sucesso). É o único check obrigatório vindo do `ci.yml`.
  - `concurrency` por workflow + ref com `cancel-in-progress: true`.
  - Caches: `actions/setup-java` (maven), `astral-sh/setup-uv` (uv), `pnpm/action-setup` +
    `actions/setup-node` (pnpm store); no smoke, `docker buildx bake` lendo o `compose.yaml` com
    `cache-from/to=type=gha`, depois `compose up --no-build`.
  - Segurança: `gitleaks` pelo binário/imagem oficial v8.30.1 (sem a action, que pede licença para
    organizações) e autoteste do próprio gitleaks (ver R-15); `actions/dependency-review-action` v5
    com `fail-on-severity: high` em PR; Trivy (P-13) nas imagens do smoke com
    `severity: HIGH,CRITICAL`, `ignore-unfixed: true`, `exit-code: 1`, mais um relatório completo
    sem falha para média/baixa.
  - `codeql.yml` separado (java-kotlin com `build-mode: none`, python, javascript-typescript,
    actions). O bloqueio por severidade alta/crítica vem da regra de *code scanning* no ruleset
    da main, não do `ci-ok`.
- **Risco**: o job `smoke` é o mais lento (build de 3 imagens). Medir no primeiro PR; se passar de
  ~8 min, reduzir com cache de dependências em `RUN --mount=type=cache` nos Dockerfiles.

## R-15. Prova automática de que um segredo falha a verificação (SC-004)

- **Decisão**: `scripts/test-gitleaks.sh` cria um repositório git temporário, gera em tempo de
  execução um segredo falso de formato conhecido (montado por concatenação para não disparar o
  próprio scanner no repositório), commita e exige que `gitleaks git` saia com código ≠ 0. Roda no
  job `gitleaks`. O teste manual com PR real fica no quickstart.

## R-16. Proteção da main e varredura do GitHub

- **Decisão**: documentar em `docs/operacao/repositorio.md` e versionar o ruleset em
  `.github/rulesets/main.json` (importável pela interface ou `gh api`): PR obrigatório com squash,
  checks obrigatórios `ci-ok` e CodeQL, regra de *code scanning* bloqueando alertas de severidade
  alta ou superior, *secret scanning* e *push protection* ligados.
- Aplicar o ruleset é ação manual do mantenedor (configuração externa ao código).

## R-17. Hooks locais (P-14)

- **Decisão**: estender o `lefthook.yml` existente. `pre-commit` (paralelo, só arquivos staged):
  gitleaks (já existe), Spotless `apply` nos `.java` de `backend/`, `ruff format` + `ruff check --fix`
  nos `.py`, Prettier + ESLint nos arquivos do `frontend/`, com `stage_fixed: true`. `pre-push`:
  `mypy` e `tsc --noEmit`. `commit-msg`: commitlint (já existe).

## R-18. Serviço de ML: "vivo" versus "pronto" (FR-002)

- **Decisão**: `GET /health` responde pelas duas coisas nesta fatia, porque o serviço não tem estado
  nem dependências (ADR-0005): se responde, está pronto. Quando houver carga de modelo, entra
  `GET /health/ready` no contrato.
- No backend: `/actuator/health/liveness` e `/actuator/health/readiness` (readiness inclui o banco;
  **não** inclui o ML, para o ML fora não derrubar o backend).

---

## ADRs novos necessários (para o scribe)

| Proposta | Título | Conteúdo |
| --- | --- | --- |
| ADR-0015 | Observabilidade: serviço Python e exportação de logs | OTel SDK + instrumentação FastAPI + OTLP HTTP + exportador Prometheus; formatador JSON próprio sobre a stdlib (structlog e python-json-logger descartados); no backend, appender OTel do Logback para enviar logs à pilha local — R-07, R-11 |
| ADR-0016 | Contrato Java ↔ Python: geração e testes | openapi-generator (restclient, Boot 4, Jackson 3); schemathesis no Python; Prism via Testcontainers no Java — R-09, R-10 |
| ADR-0017 | Qualidade estática do backend | Spotless (google-java-format), Error Prone + NullAway com JSpecify, JaCoCo (mínimo 80% de linhas, sem código gerado) — R-09 |
| ADR-0018 | Ferramentas do frontend | TanStack Query, ESLint flat + typescript-eslint, Prettier, Vitest + Testing Library + jsdom; TypeScript 6.0 enquanto o typescript-eslint não suportar o 7 — R-12 |
| ADR-0019 | PostgreSQL 18 com pgvector e papel de aplicação sem superusuário | imagem, layout de volume do PG 18, script de papéis, migração idempotente da extensão — R-04, R-05 |
| ADR-0020 | Pipeline de CI e imagens de contêiner | paths-filter + check agregador `ci-ok`, gitleaks por binário, Trivy, dependency-review, ruleset versionado; imagens base fixas e não-root — R-13, R-14, R-16 |

Nenhuma dessas bibliotecas entra no código antes de o ADR correspondente estar aceito.
