<!--
SYNC IMPACT REPORT — remover antes do commit
=============================================
Versão anterior: 1.0.0
Versão nova: 2.0.0
Mudança de versão: 1.0.0 → 2.0.0 (MAJOR: princípio V redefinido — remove a exigência
  de CostMeter, tetos e alertas na aplicação; troca por limite no painel do provedor)
Última emenda: 2026-11-03

Princípios modificados: V (texto substituído exatamente como fornecido; título
  "Custo sob controle" mantido). Princípios I–IV e VI–IX inalterados.
Seções adicionadas: nenhuma
Seções removidas: nenhuma

Propagação:
  - Templates do Spec Kit (plan/spec/tasks/checklist): verificados; nenhum cita
    CostMeter ou teto na aplicação (leem a constituição em tempo de execução).
    Nenhum arquivo alterado, conforme o Scope Guard.

TODOs / inconsistências pendentes (fora do escopo desta emenda):
  - Princípio III ainda cita "controle de custo" (TDD) e "custo registrado, respeito ao
    teto" (suíte de contrato dos coletores); Princípio VII cita custo por coletor.
    Não alterados por instrução ("nenhum outro princípio").
  - .claude/agents/reviewer.md:17 e CLAUDE.md (regra 5) ainda exigem CostMeter.
  - Criar o ADR-0014 (adiamento do controle de custo na aplicação).
=============================================
-->

# Constituição do TrendGap

## Princípios

### I. Fatias verticais, sistema sempre rodando

Toda entrega é uma fatia vertical (da coleta à tela) que termina com o sistema rodando de
ponta a ponta via `docker compose up`. Nenhuma sprint termina com funcionalidade pela metade
na main.

### II. Núcleo fechado, extensão por portas

Arquitetura hexagonal dentro de um monólito modular (Spring Modulith). Fontes, scores, canais
de notificação e marketplaces entram como adaptadores que implementam portas, ligados por
configuração `trendgap.<tipo>.<id>.enabled`. Adicionar um deles NÃO PODE exigir mudança no
núcleo. Um módulo NUNCA acessa o interior de outro; os testes do Modulith quebram o build se
isso acontecer.

### III. Testes antes, onde há lógica

TDD é OBRIGATÓRIO para lógica de domínio (scores, regra de ouro, controle de custo,
calibração). Todo coletor DEVE passar na suíte de contrato comum (idempotência, ausência de
dados pessoais, custo registrado, respeito ao teto, tratamento de limites). A fronteira
Java ↔ Python DEVE ter testes de contrato dos dois lados a partir do mesmo OpenAPI.
Integração usa Testcontainers com Postgres real. Nada entra na main com teste falhando.

### IV. Probabilidade é estatística, nunca LLM

A probabilidade de um tema virar tendência vem de um modelo estatístico calibrado, validado
por backtest temporal e identificado por versão. O LLM SOMENTE extrai, classifica e redige
(fichas, checklists V e P). Um modelo só é promovido se o backtest não piorar.

### V. Custo sob controle — O orçamento total de dados e LLM da aplicação é US$ 20/mês. Antes de ligar qualquer provedor pago, um limite de gasto DEVE ser configurado no painel do próprio provedor (saldo pré-pago, teto mensal ou limite de gasto) dentro desse orçamento. Antes do LLM vêm filtragem, deduplicação, cache e Batch API. O controle de custo dentro da aplicação (medição por chamada, tetos e alertas) está adiado e será reavaliado antes da entrada do primeiro provedor pago (ADR-0014).

### VI. Segurança e privacidade por padrão

Segredos SOMENTE em variáveis de ambiente, nunca no repositório (público); gitleaks DEVE
rodar no commit e no CI. Coletamos APENAS dados públicos e agregados sobre temas, nunca
perfis de pessoas (LGPD): adaptadores DEVEM descartar autor e identificadores pessoais antes
de gravar. Login SOMENTE com Google (OAuth2), sessão httpOnly com CSRF, autorização por
roles, menor privilégio em tudo.

### VII. Observável e resiliente

Logs JSON estruturados com traceId, métricas por coletor (duração, itens, erros, custo) e
traces entre serviços via OpenTelemetry. Coletores DEVEM ser idempotentes, respeitar limites
das APIs e usar retry com backoff. Falha de dependência DEVE degradar com aviso, nunca
inventar dado.

### VIII. Simplicidade deliberada

Construir SOMENTE o necessário para a fatia atual (YAGNI). Nova dependência, serviço ou
padrão relevante EXIGE ADR. O backend Java é o único dono dos dados e das regras; o serviço
Python é sem estado e SOMENTE calcula.

### IX. Qualidade automática e rastreável

Formatação, lint e checagem de tipos DEVEM rodar em hooks e no CI, nunca depender de lembrar.
Commits convencionais, PR com squash merge, main protegida. Toda funcionalidade nasce de
spec → plano → tarefas versionados em `specs/`. Decisões arquiteturais DEVEM virar ADR em
`docs/adr/`.

## Restrições Técnicas

- Backend: Java 25, Spring Boot 4.1.x, Maven (wrapper), Spring Modulith, Flyway, JobRunr.
- Serviço de ML: Python 3.13, uv, FastAPI, contrato em `contracts/ml-service.openapi.yaml`.
- Frontend: React + TypeScript + Vite, pnpm.
- Dados: PostgreSQL + pgvector; migrações SOMENTE pelo backend.
- LLM da aplicação: Claude Haiku 5.5 (extração em massa) e Sonnet 5.5 (fichas, V, P, validador).
- Execução: Docker Compose; configuração por variáveis de ambiente; deploy agnóstico de provedor.
- Idioma: código, identificadores e commits em inglês; constituição, specs, planos e ADRs em
  português.

## Fluxo de Desenvolvimento

- Pontos de aprovação humana: constituição; spec de cada fatia (após clarify); plano e tarefas
  (após analyze); PR antes do merge.
- Uma tarefa por sessão do agente; testes do critério de aceite ANTES da implementação na
  lógica de domínio; revisão por agente somente leitura antes do commit.

## Governança

Esta constituição prevalece sobre todas as outras práticas. Emendas entram por PR com ADR e
atualização de versão semântica (MAJOR: princípio removido ou redefinido; MINOR: princípio
novo; PATCH: redação). Todo plano DEVE passar pelo "Constitution Check" do Spec Kit.

**Versão**: 2.0.0 | **Ratificada em**: 2026-11-02 | **Última emenda**: 2026-11-03
