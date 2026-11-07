# Observabilidade local (profile `observability`)

Pilha opcional Grafana LGTM (`grafana/otel-lgtm`): Grafana, Tempo (traces), Loki (logs) e
Prometheus (métricas). Sem ela, backend e ML só escrevem logs JSON no console e expõem métricas
internamente.

## Como ligar

1. No `.env`, defina `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318` (vazio = sem exportação).
2. Suba com o profile:

   ```bash
   docker compose --env-file .env -f infra/compose.yaml --profile observability up --wait
   ```

3. Abra o Grafana em <http://localhost:3000> (usuário e senha padrão da imagem: `admin`/`admin`;
   só para uso local). Em *Explore*, escolha Tempo e busque pelo `trace.id`.

## Rede e portas

- O OTLP HTTP (4318) fica só na rede interna do Compose; nenhuma porta de métricas é publicada.
- O Prometheus da imagem raspa `backend:8081/actuator/prometheus` e `ml-service:8000/metrics`
  conforme `prometheus.yaml`, montado sobre a configuração da imagem.
- Apenas o Grafana (3000) é publicado no host.

## Desligar

Remova `OTEL_EXPORTER_OTLP_ENDPOINT` do `.env` e suba sem `--profile observability`.
