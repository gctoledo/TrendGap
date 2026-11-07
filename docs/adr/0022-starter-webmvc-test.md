# 0022. spring-boot-starter-webmvc-test para testes de controller

- Status: aceito
- Data: 2026-11-07

## Contexto
Os testes de controller (T028, `@WebMvcTest` com `MockMvc`) conferem o contrato JSON do `/api/status`. No Boot 4 o suporte a `@WebMvcTest` saiu do `spring-boot-starter-test` e vive em `spring-boot-starter-webmvc-test`.

## Opções consideradas
1. Adicionar `spring-boot-starter-webmvc-test` com escopo `test`, gerenciado pelo BOM do Boot 4.1.2.
2. `MockMvc` standalone, sem a fatia do Boot: não valida a configuração real de Jackson e do MVC, que é o que o contrato exige.

## Decisão
Escolhemos a opção 1. Escopo `test`, sem versão própria, nada entra no artefato de produção.

## Consequências
- Positivas: o teste de contrato usa o mesmo `ObjectMapper` e a mesma configuração MVC da aplicação.
- Negativas: mais um starter de teste a manter alinhado ao Boot.
