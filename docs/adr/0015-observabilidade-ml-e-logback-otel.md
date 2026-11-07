# 0015. Observabilidade do serviço de ML e exportação de logs do backend

- Status: aceito
- Data: 2026-11-07

## Contexto
A fundação exige logs JSON com `trace.id` nos dois serviços, o mesmo trace entre backend e ML, métricas internas e uma pilha de observabilidade opcional (profile `observability`), sem publicar portas de métricas. O ADR-0012 já fixou logs JSON, Micrometer e OpenTelemetry. Este ADR escolhe as bibliotecas do serviço Python e o caminho dos logs do backend até a pilha local (research.md, R-07 e R-11).

## Opções consideradas
1. OTel SDK, instrumentação FastAPI, exportador OTLP HTTP e exportador Prometheus, com formatador JSON próprio sobre a `logging` da stdlib (≈30 linhas). No backend, appender `opentelemetry-logback-appender-1.0`.
2. `structlog` ou `python-json-logger` para os logs e `prometheus-fastapi-instrumentator` para as métricas.
3. Backend sem appender OTel: logs só no console, sem envio à pilha local.

## Decisão
Escolhemos a opção 1. O OTel já produz traces e métricas, e o `prometheus-fastapi-instrumentator` duplicaria essas métricas. A stdlib resolve o formato JSON, então o `structlog` seria uma dependência a mais. No backend, o appender vem do BOM de instrumentação OTel (2.33.x), instalado pelo starter.

Regras:
- o exportador OTLP HTTP só é criado com `OTEL_EXPORTER_OTLP_ENDPOINT` definido (ausente ou vazio = sem exportação);
- `/metrics` fica só na rede interna;
- o log de acesso do uvicorn é desligado; um middleware registra uma linha por requisição dentro do span.

## Consequências
- Positivas: mesmo conjunto de SDKs OTel nos dois serviços; mesmos nomes de campo ECS nos logs; nenhuma exportação sem endpoint configurado.
- Negativas: o formatador JSON próprio precisa de manutenção; os pacotes de instrumentação Python estão na versão beta 0.66bX; a ordem dos middlewares (o do OTel como mais externo) ainda precisa ser confirmada na tarefa T062.
