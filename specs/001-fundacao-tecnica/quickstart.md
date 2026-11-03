# Quickstart e validação: Fundação técnica

Guia para provar, de ponta a ponta, que a fatia funciona. Detalhes de formato estão em
[data-model.md](data-model.md) e [contracts/](contracts/).

## Pré-requisitos

- Docker Engine com Compose v2.24+ (precisa de `up --wait`), `curl` e `jq`.
- Para desenvolver fora de contêiner: JDK 25, uv 0.13+, Node 24 + pnpm 12 (via `corepack`),
  gitleaks 8.30+ e lefthook.
- Instalar os hooks uma vez: `pnpm install && lefthook install`.

## 1. Subir o sistema (História 1, SC-001)

```sh
cp .env.example .env        # preencher as variáveis obrigatórias com valores locais
docker compose -f infra/compose.yaml build
docker compose -f infra/compose.yaml up -d --wait --wait-timeout 180
```

Esperado: os quatro serviços `healthy` em até 3 minutos. Abrir `http://localhost:8080`
(ou `TRENDGAP_WEB_PORT`): backend, banco e serviço de ML como **saudável**, com horário.

## 2. Queda e volta de componentes (SC-002)

| Ação | Esperado na página em até 15 s |
| --- | --- |
| `docker compose -f infra/compose.yaml stop ml-service` | ML **fora do ar**; backend e banco saudáveis |
| `... start ml-service` | ML **saudável** |
| `... stop postgres` | banco **fora do ar** |
| `... start postgres` | banco **saudável** |
| `... stop backend` | backend **fora do ar**; banco e ML **desconhecido**; página continua visível |
| `... start backend` | tudo saudável |

## 3. Nada de detalhes internos (SC-003)

```sh
curl -s http://localhost:8080/api/status | jq .
docker compose -f infra/compose.yaml exec frontend \
  wget -qO- http://backend:8081/actuator/health              # de dentro da rede interna
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/health   # 404
```

Esperado: só `name`/`status`/`checkedAt` em `/api/status`; só `{"status":"UP"}` no actuator;
Actuator inacessível pelo host.

## 4. Smoke automatizado (Histórias 1 e 4, SC-002, SC-007)

```sh
scripts/smoke.sh
```

O script: sobe o Compose com `--wait`; envia `GET /api/status` com um `traceparent` W3C gerado;
exige os três componentes `UP`; procura o mesmo `trace.id` nos logs do backend **e** do ML; para o
ML, exige `DOWN` em até 15 s, religa e exige `UP` em até 15 s; faz o mesmo com o postgres (`database=DOWN`, depois `UP`); derruba o ambiente no final
(`--keep` mantém no ar). Saída ≠ 0 em qualquer falha, com a etapa que falhou.

O smoke mede a API; a atualização da UI a cada 5 s é coberta pelo Vitest com timers falsos.

## 5. Falha rápida de configuração (História 3, SC-006)

```sh
# Backend: sem variável obrigatória
docker compose -f infra/compose.yaml run --rm -e TRENDGAP_ML_BASE_URL= backend
# Esperado: sai com erro citando TRENDGAP_ML_BASE_URL e a regra, sem ecoar valores.

# Backend: valor inválido
docker compose -f infra/compose.yaml run --rm -e TRENDGAP_ML_TIMEOUT=10m backend

# ML: porta inválida
docker compose -f infra/compose.yaml run --rm -e TRENDGAP_ML_PORT=abc ml-service
```

Os testes automatizados cobrem cada variável obrigatória (backend: `ApplicationContextRunner`;
ML: pytest de `Settings`).

## 6. Observabilidade (História 4)

```sh
docker compose -f infra/compose.yaml --profile observability up -d --wait
# definir OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318 no .env antes de subir
```

Abrir Grafana em `http://localhost:3000`, fazer algumas requisições à página e ver: trace com os
trechos do backend e do ML (Tempo), logs dos dois (Loki) e métricas de requisição (Prometheus/Mimir).
O Grafana na porta 3000 é a única porta extra, exposta só no profile `observability`.
Sem o profile, o sistema funciona igual (passo 1).

## 7. Verificação local de cada componente

```sh
cd backend && ./mvnw spotless:apply verify
cd ml-service && uv run ruff check . && uv run mypy src && uv run pytest
cd frontend && pnpm lint && pnpm typecheck && pnpm test
```

## 8. CI e proteção da main (História 2, SC-004, SC-005)

1. Aplicar o ruleset de `.github/rulesets/main.json` e ligar secret scanning + push protection
   (passo a passo em `docs/operacao/repositorio.md`).
2. Abrir um PR limpo: todos os checks verdes em até 10 min (ver duração no resumo do workflow).
3. Abrir um PR de teste com um segredo falso commitado de propósito: job `gitleaks` vermelho
   apontando o arquivo, `ci-ok` vermelho, merge bloqueado. Fechar o PR e apagar a branch.
4. Localmente, `git commit` de um arquivo com segredo falso: o hook recusa.

## 9. Dependabot (História 5)

Com `.github/dependabot.yml` na main, conferir em *Insights → Dependency graph → Dependabot* que
há execução semanal para maven, uv, npm (raiz e frontend), github-actions, docker e docker-compose.
