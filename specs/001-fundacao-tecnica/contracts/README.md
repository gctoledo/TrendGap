# Contratos desta fatia

| Arquivo | Interface | Consumidor | Fonte da verdade |
| --- | --- | --- | --- |
| [contracts/ml-service.openapi.yaml](../../../contracts/ml-service.openapi.yaml) | HTTP do serviço de ML | backend (cliente gerado) | `contracts/ml-service.openapi.yaml` na raiz (movido do rascunho) |
| [status-api.openapi.yaml](status-api.openapi.yaml) | `GET /api/status` do backend | frontend | este arquivo (desenho); o teste `@WebMvcTest` do controller confere campos, enums e ordem por JSONPath |

Superfície HTTP exposta fora da rede interna do Compose: só o frontend (nginx), que serve a SPA e
faz proxy de `/api/` para o backend. Actuator (`:8081`) e `/metrics` do ML não são publicados.

Endpoints internos (não contratados, só para healthcheck e scrape):

- backend: `GET :8081/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`
  (só `{"status":"UP|DOWN"}`, sem componentes nem detalhes) e `GET :8081/actuator/prometheus`.
- ml-service: `GET :8000/metrics`.
