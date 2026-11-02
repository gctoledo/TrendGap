# 0010. Login com Google (OAuth2), sessão httpOnly e roles

- Status: aceito
- Data: 2026-11-02

## Contexto
O login é só com Google, com roles USER e ADMIN, e os planos ficam preparados para monetização futura (D-12).

## Opções consideradas
1. OAuth2 Login Google com sessão httpOnly e proteção CSRF, roles e tabela de planos (escolhida).
2. Outras opções: a definir.

## Decisão
Escolhemos OAuth2 Login com Google, sessão httpOnly com CSRF, roles USER e ADMIN e tabela de planos.

## Consequências
- Positivas: a definir.
- Negativas: a definir.
