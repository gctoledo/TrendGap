# 0016. Contrato Java ↔ Python: geração de cliente e testes de contrato

- Status: aceito
- Data: 2026-11-07

## Contexto
O backend Java e o serviço Python de ML se falam por HTTP (ADR-0005). O arquivo `contracts/ml-service.openapi.yaml` é a fonte da verdade dessa fronteira. Precisamos de um cliente Java sem escrita manual, de testes que validem os dois lados contra o mesmo arquivo e de compatibilidade com Spring Boot 4.1 e Jackson 3 (research.md, R-09 e R-10).

## Opções consideradas
1. Cliente Java gerado com openapi-generator 7.27.x (`java`, `library=restclient`, `useSpringBoot4`, `useJackson3`); schemathesis 4.x no Python; mock Prism 5.16.0 via Testcontainers no Java.
2. WireMock com `swagger-request-validator-wiremock` no Java: stubs escritos à mão e uma dependência Java a mais.
3. `openapi-core` no Python validando respostas manualmente: exige escrever os casos à mão.

## Decisão
Escolhemos a opção 1. O cliente sai do mesmo arquivo do contrato. O schemathesis gera os casos a partir dele. O Prism valida a requisição do cliente e responde com os exemplos do contrato, sem dependência Java nova.

O cliente é gerado no `generate-sources`, no pacote `io.github.gctoledo.trendgap.platform.mlclient`, e usa o `RestClient.Builder` do Boot, que injeta `traceparent`. O código gerado fica fora de Error Prone, NullAway e JaCoCo.

## Consequências
- Positivas: uma única fonte da verdade; divergência entre código e contrato quebra o build nos dois lados.
- Negativas: a versão do gerador precisa conter a correção de Jackson 3 no `ApiClient` restclient (PR #24640, ago/2026); a API do schemathesis 4.x para carregar um arquivo contra o app ASGI ainda precisa ser confirmada na tarefa T033; o teste com Prism exige Docker.
