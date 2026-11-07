# 0021. spring-boot-starter-restclient para o cliente do serviço de ML

- Status: aceito
- Data: 2026-11-07

## Contexto
O cliente Java do serviço de ML é gerado do contrato (ADR-0016) e deve usar o `RestClient.Builder` auto-configurado do Boot, que injeta `traceparent` e registra métricas (ADR-0012). No Boot 4 esse suporte vive em módulos separados (`spring-boot-restclient`, `spring-boot-http-client`) e o `spring-boot-starter-web` não os traz.

## Opções consideradas
1. Adicionar `spring-boot-starter-restclient`, gerenciado pelo BOM do Boot 4.1.2.
2. Usar `RestClient.builder()` manual, sem starter: perde instrumentação automática de tracing e métricas e obriga a configurar tudo à mão.

## Decisão
Escolhemos a opção 1. O starter é mantido pelo projeto Spring Boot, sem versão própria, e entrega o `RestClient.Builder` e o `HttpClientSettings` para os timeouts de conexão e leitura (`trendgap.ml.timeout`).

## Consequências
- Positivas: `traceparent` e métricas `http.client.requests` sem código; timeouts via API do Boot.
- Negativas: um starter a mais no classpath (menor, da mesma família e versão do Boot).
