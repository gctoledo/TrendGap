# 0014. Adiar o controle de custo dentro da aplicação

- Status: aceito
- Data: 2026-11-03

## Contexto
O orçamento total é de US$ 20 por mês para dados e LLM. Nenhum provedor pago será usado até a Sprint 2. Queremos reduzir o escopo da fundação. Controlar custo dentro da aplicação exige contabilizar cada chamada paga, o que hoje não tem uso real a justificar. Este ADR emenda o princípio V da constituição (v2.0.0).

## Opções consideradas
1. Controle de custo na aplicação já na fundação (contabilização de cada chamada paga).
2. Limites de gasto configurados nos painéis dos provedores agora, com reavaliação antes da Sprint 2.

## Decisão
Escolhemos a opção 2: limites de gasto nos painéis dos provedores agora e reavaliação do controle de custo na aplicação antes da Sprint 2. Assim a fundação fica menor e é entregue antes, sem gastar esforço num controle que ainda não tem uso.

## Consequências
- Positivas: fundação menor e entregue antes.
- Negativas: sem visibilidade de custo por fonte dentro do TrendGap. Há dependência da configuração de cada provedor.
