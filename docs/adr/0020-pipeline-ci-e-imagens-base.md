# 0020. Pipeline de CI e imagens de contêiner

- Status: aceito
- Data: 2026-11-07

## Contexto
Cada PR precisa de verificação em até 10 min (SC-005), com segredos barrados antes do merge, análise de segurança e merge liberado só com um check verde. As imagens de contêiner precisam ser reproduzíveis e rodar como não-root (research.md, R-13, R-14 e R-16).

## Opções consideradas
1. `dorny/paths-filter` v4 em job `changes`; job agregador `ci-ok`; gitleaks pelo binário oficial v8.30.1; Trivy com HIGH/CRITICAL falhando; `actions/dependency-review-action` v5 com `fail-on-severity: high`; ruleset versionado; imagens com tag fixa e `USER` numérico.
2. `gitleaks-action` oficial: descartada, porque pede licença para organizações.
3. Imagens com tag móvel (como `latest`): descartada, porque o build deixa de ser reproduzível.

## Decisão
Escolhemos a opção 1. O filtro por caminho evita rodar componentes sem mudança, e o gitleaks roda sempre. O `ci-ok` é o único check obrigatório do `ci.yml`: falha se algum job terminar em `failure` ou `cancelled`, e aceita `skipped` por filtro de caminho.

O CodeQL fica em workflow separado. O bloqueio de severidade alta vem da regra de code scanning do ruleset, não do `ci-ok`. Aplicar o ruleset é ação manual do mantenedor.

Imagens base fixas: `eclipse-temurin:25.0.5_8` (build e runtime do backend), `python:3.13.18-slim-trixie` com `uv` 0.13.7, `node:24.22.2-alpine3.24` (build do frontend), `nginxinc/nginx-unprivileged:1.30.6-alpine3.24` e a imagem do Postgres do ADR-0019.

## Consequências
- Positivas: um único check para o ruleset; imagens reproduzíveis e não-root; falha em HIGH/CRITICAL com correção disponível.
- Negativas: o job `smoke` é o mais lento (build de três imagens) e, se passar de ~8 min, precisa de cache de dependências nos Dockerfiles; `ignore-unfixed: true` deixa de bloquear vulnerabilidades sem correção upstream; ruleset e secret scanning são configuração externa ao repositório.
