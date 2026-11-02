# 0013. Desenvolvimento com agentes (Spec Kit e Claude Code)

- Status: aceito
- Data: 2026-11-02

## Contexto
O desenvolvimento usa agentes de IA. É preciso definir o fluxo de especificação, a configuração do Claude Code e a divisão de papéis (P-16, P-17 e P-18 do registro, tratados como um único ADR).

## Opções consideradas
1. Spec Kit com spec por fatia (sem implementação em lote), Claude Code com opusplan, sessão principal que planeja e implementa, e subagentes test-writer, reviewer, debugger, scribe e Explore (Haiku) (escolhida).
2. Outras opções: a definir.

## Decisão
Escolhemos Spec Kit com uma spec por fatia, sem implementação em lote. Usamos Claude Code com opusplan. A sessão principal planeja e implementa. Os subagentes são test-writer, reviewer, debugger, scribe e Explore (Haiku).

## Consequências
- Positivas: a definir.
- Negativas: a definir.
