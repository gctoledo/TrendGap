# Registro de decisões (espelho do plano de engenharia, 2026-11-02)

## Produto e restrições
- D-01 Tudo do escopo é versão 1, entregue em fatias incrementais.
- D-02 Fontes grátis: YouTube Data API, Hacker News (Algolia), Product Hunt, RSS/newsletters/podcasts, GDELT/Google News RSS, Mercado Livre.
- D-03 Fonte paga: Google Trends EUA/BR via DataForSEO (+ Shopping e YouTube). API oficial do Trends só como troca futura de adaptador.
- D-04 Apify mínimo: TikTok Creative Center e Kickstarter, foto semanal, dentro de US$ 5/mês.
- D-05 Amazon Movers & Shakers e Shopee implementadas e desligadas; Reddit só interface e adaptador vazio.
- D-07 Treino e backtest só com fontes de histórico; fotos (TikTok, Amazon) viram features após meses de coleta própria.
- D-08 Probabilidade vem de modelo estatístico calibrado, nunca do LLM; base rotulada 50+ casos; backtest temporal; medir atraso EUA→BR.
- D-10 Regra de ouro: candidato só com 3+ fontes independentes e sinal de dinheiro.
- D-12 Login só Google; roles USER e ADMIN; planos preparados para monetização futura.
- D-15 Orçamento: US$ 20/mês para dados e LLM da aplicação.
- D-16 Adiado em 2026-11-03: controle de custo fora da aplicação; limite de gasto no painel de cada provedor; reavaliar antes da Sprint 2 (ADR-0014).
- D-19 LGPD: só dados públicos agregados sobre temas.
- D-21 Nome TrendGap; pacote io.github.<usuario>.trendgap; Python trendgap_ml; config trendgap.*.
- D-22 Código e commits em inglês; constituição, specs, ADRs e docs em português.

## Arquitetura e ferramentas
- [ADR] P-01 Java 25 LTS + Spring Boot 4.1.x (4.0 perde suporte grátis em dez/2026).
- [ADR] P-02 Maven com Maven Wrapper, Maven 3.9.x (4.0 ainda em RC). Substitui a proposta de Gradle.
- [ADR] P-03 Monólito modular com Spring Modulith; hexagonal dentro de cada módulo.
- [ADR] P-04 Extensão por portas + registro via injeção de dependência + flag trendgap.<tipo>.<id>.enabled.
- [ADR] P-05 Serviço Python (FastAPI, uv, 3.13) sem estado, só ML/estatística/embeddings; contrato OpenAPI; Java é dono dos dados.
- [ADR] P-06 Embeddings locais multilíngues (multilingual-e5-small) com text-embedding-3-small como adaptador alternativo.
- [ADR] P-07 Modelo inicial: regressão logística regularizada + calibração sigmoide; LightGBM só com centenas de rótulos e ganho no backtest.
- [ADR] P-08 JobRunr OSS (starter Spring Boot 4) com armazenamento no Postgres para agendamento e retries.
- [ADR] P-09 Frontend Vite + React + TypeScript (SPA); Next.js descartado.
- [ADR] P-10 OAuth2 Login Google com sessão httpOnly + CSRF; roles; tabela de planos.
- [ADR] P-11 SDK oficial Anthropic para Java atrás de LlmPort (Spring AI descartado).
- [ADR] P-12 Observabilidade: logs JSON, Micrometer + OpenTelemetry, grafana/otel-lgtm local.
- P-13 Segurança: gitleaks, secret scanning + push protection, CodeQL, Dependabot, Trivy, rate limit na API pública.
- P-14 Git hooks com lefthook + commitlint.
- P-15 Teto global por provedor para a coleta; cota por usuário só em ações sob demanda. (suspensa até a reavaliação do ADR-0014)
- [ADR] P-16/P-17/P-18 Desenvolvimento com agentes: Spec Kit (spec por fatia, sem implement em lote); Claude Code com opusplan; sessão principal planeja e implementa; subagentes test-writer, reviewer, debugger, scribe e Explore (Haiku).
- P-19 Branch por fatia, PR com squash merge, main protegida.
- P-21 Licença MIT.
