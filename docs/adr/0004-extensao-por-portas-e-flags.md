# 0004. Extensão por portas, registro por injeção de dependência e flags

- Status: aceito
- Data: 2026-11-02

## Contexto
Fontes, scores, canais e marketplaces novos devem entrar sem alterar o núcleo (regra 3 do CLAUDE.md).

## Opções consideradas
1. Extensão por portas, com registro via injeção de dependência e flag `trendgap.<tipo>.<id>.enabled` (escolhida).
2. Outras opções: a definir.

## Decisão
Escolhemos extensão por portas, com registro via injeção de dependência e flag `trendgap.<tipo>.<id>.enabled` para ligar e desligar cada adaptador.

## Consequências
- Positivas: a definir.
- Negativas: a definir.
