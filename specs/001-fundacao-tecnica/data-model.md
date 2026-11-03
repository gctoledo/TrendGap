# Modelo de dados (Fase 1): Fundação técnica

**Feature**: 001-fundacao-tecnica · **Data**: 2026-11-03

Esta fatia não tem dados de negócio nem tabelas próprias. O "modelo" aqui são os valores que
circulam entre componentes, a configuração validada na inicialização e o formato de log.
O banco recebe só a migração `V1__enable_vector.sql` (extensão `vector`) e a tabela de histórico
do Flyway.

## 1. Componente monitorado

Valor de domínio do módulo `platform` (sem persistência).

| Campo | Tipo | Regras |
| --- | --- | --- |
| `name` | enum `ComponentName` | `backend`, `database`, `ml-service` (nomes estáveis no JSON) |
| `status` | enum `HealthStatus` | `UP` ou `DOWN` no backend; o frontend acrescenta `UNKNOWN` (ver §3) |
| `checkedAt` | instante UTC (ISO-8601) | momento em que a sonda daquele componente terminou |

**Regras de derivação** (lógica de domínio, com TDD):

- `backend`: `UP` sempre que o próprio backend responde a `/api/status`.
- `database`: `UP` se a sonda obtém conexão e valida (`isValid`) dentro do tempo limite; senão `DOWN`.
- `ml-service`: `UP` se `GET /health` do ML responde 2xx com corpo `{"status":"UP"}` dentro do
  tempo limite (`TRENDGAP_ML_TIMEOUT`); qualquer erro, corpo inesperado ou estouro de tempo → `DOWN`.
- As sondas rodam em paralelo; uma sonda que falha ou estoura não afeta as outras.
- Nenhuma mensagem de exceção, host ou versão sai na resposta; a falha vai só para o log (sem valores
  de configuração).

**Portas e adaptadores** (princípio II):

- Porta de saída `ComponentProbe` (`name()`, `probe() → HealthStatus`).
- Adaptadores: `DatabaseProbe` (JDBC), `MlServiceProbe` (cliente gerado do contrato).
  `backend` é derivado no serviço, sem sonda. Componente novo = novo adaptador da porta.

## 2. Resposta de `GET /api/status`

Contrato: [contracts/status-api.openapi.yaml](contracts/status-api.openapi.yaml).

```json
{
  "components": [
    { "name": "backend",    "status": "UP",   "checkedAt": "2026-11-03T12:00:00.120Z" },
    { "name": "database",   "status": "UP",   "checkedAt": "2026-11-03T12:00:00.118Z" },
    { "name": "ml-service", "status": "DOWN", "checkedAt": "2026-11-03T12:00:02.101Z" }
  ]
}
```

- Sempre HTTP 200 quando o backend responde; o estado vai no corpo.
- Ordem fixa: `backend`, `database`, `ml-service`.
- Tempo de resposta máximo ≈ maior tempo limite das sondas + margem (alvo ≤ 2,5 s).

## 3. Estado exibido na página (frontend)

| Situação | backend | database | ml-service | Horário mostrado |
| --- | --- | --- | --- | --- |
| `/api/status` responde | do corpo | do corpo | do corpo | `checkedAt` de cada componente |
| `/api/status` falha (rede, 5xx, tempo) | `DOWN` | `UNKNOWN` | `UNKNOWN` | horário da tentativa que falhou |

- Consulta a cada 5 s (`refetchInterval: 5000`), também em segundo plano; sem retry automático,
  para refletir queda dentro de 15 s (FR-004).
- Tempo limite da chamada no navegador: 4 s (menor que o intervalo).

## 4. Configuração validada na inicialização

Todas as variáveis entram no `.env.example` com descrição, obrigatoriedade e valor vazio ou fictício.
A falha nomeia a **variável de ambiente** e a **regra**, nunca o valor (FR-013).

### Backend (`trendgap.*`)

| Variável | Propriedade | Obrigatória | Regra |
| --- | --- | --- | --- |
| `TRENDGAP_DB_URL` | `trendgap.db.url` | sim | começa com `jdbc:postgresql://` |
| `TRENDGAP_DB_USERNAME` | `trendgap.db.username` | sim | não vazia |
| `TRENDGAP_DB_PASSWORD` | `trendgap.db.password` | sim | não vazia (segredo: nunca logada) |
| `TRENDGAP_ML_BASE_URL` | `trendgap.ml.base-url` | sim | URL `http`/`https` absoluta |
| `TRENDGAP_ML_TIMEOUT` | `trendgap.ml.timeout` | não (padrão `2s`) | duração entre `100ms` e `5s` |
| `TRENDGAP_HTTP_PORT` | `server.port` | não (padrão `8080`) | inteiro 1–65535 |
| `TRENDGAP_MANAGEMENT_PORT` | `management.server.port` | não (padrão `8081`) | inteiro 1–65535, diferente da porta HTTP |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | exportação OTLP | não | URL absoluta; ausente = sem exportação |

### Serviço de ML (`pydantic-settings`, prefixo `TRENDGAP_ML_`)

| Variável | Obrigatória | Regra |
| --- | --- | --- |
| `TRENDGAP_ML_PORT` | não (padrão `8000`) | inteiro 1–65535 |
| `TRENDGAP_ML_LOG_LEVEL` | não (padrão `INFO`) | um de `DEBUG`, `INFO`, `WARNING`, `ERROR` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | não | URL absoluta; ausente = sem exportação |

O processo valida as configurações antes de iniciar o uvicorn; falha → mensagem com variável e
regra (`ValidationError` reformatado, sem `input_value`) e código de saída ≠ 0.

### Infra (Compose)

| Variável | Uso | Obrigatória |
| --- | --- | --- |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | superusuário, só dentro do contêiner do Postgres | sim |
| `TRENDGAP_DB_USERNAME`, `TRENDGAP_DB_PASSWORD` | papel de aplicação criado pelo script de init | sim |
| `TRENDGAP_WEB_PORT` | porta publicada no host para o frontend (padrão `8080`) | não |

`TRENDGAP_DB_URL` e `TRENDGAP_ML_BASE_URL` são montadas no `compose.yaml` a partir dos nomes de
serviço; o `.env` não precisa delas para o Compose, só para rodar o backend fora do contêiner.

## 5. Formato de log (backend e ML)

Uma linha JSON por evento, mesmos nomes de campo nos dois serviços (ECS):

| Campo | Exemplo | Obrigatório |
| --- | --- | --- |
| `@timestamp` | `2026-11-03T12:00:00.120Z` | sim |
| `log.level` | `INFO` | sim |
| `service.name` | `backend` / `ml-service` | sim |
| `message` | `GET /health 200` | sim |
| `trace.id` | 32 hex | quando há requisição em curso |
| `span.id` | 16 hex | quando há requisição em curso |
| `log.logger` | nome do logger | sim |

Proibido em log: valores de variáveis de configuração, cabeçalhos de autenticação, corpo de
requisição, dados pessoais (FR-019).

## 6. Métricas (rede interna)

| Componente | Endpoint interno | Mínimo |
| --- | --- | --- |
| backend | `:8081/actuator/prometheus` | `http_server_requests_seconds_*` (contagem e duração), `http_client_requests_seconds_*`, `trendgap_component_status{component}` (1 = UP, 0 = DOWN, atualizado a cada `/api/status`) |
| ml-service | `:8000/metrics` | `http_server_duration_*` / `http_server_request_duration_*` do OTel (contagem e duração) |

Nenhuma porta de métricas é publicada no host (FR-017).
