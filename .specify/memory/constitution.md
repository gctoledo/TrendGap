<!--
SYNC IMPACT REPORT — remover antes do commit
=============================================
Versão anterior: (nenhuma — criação inicial)
Versão nova: 1.0.0
Mudança de versão: n/a → 1.0.0 (primeira ratificação)

Seções adicionadas:
  - Princípios I a IX (9 princípios no total; o template previa 5 — expandido conforme rascunho)
  - Restrições Técnicas
  - Fluxo de Desenvolvimento
  - Governança

Seções removidas: n/a

Princípios renomeados: n/a

TODOs diferidos: nenhum — todos os campos foram preenchidos a partir de
  docs/constitution-draft.md e dos parâmetros fornecidos pelo usuário.

Nota de interpretação:
  O template previa 5 slots de princípio; o rascunho contém 9. O comando prevê
  expansão ("The user might require less or more principles"), por isso os 9
  princípios foram preservados integralmente.
  A instrução "propague as verificações para os modelos de plano, spec e tarefas"
  refere-se ao comportamento em tempo de execução dos próprios templates do Spec Kit
  (que lêem esta constituição automaticamente). Nenhum template foi modificado aqui
  — o Scope Guard confirma que isso está fora do escopo deste comando.
  Linguagem normativa (DEVE, NÃO PODE, SOMENTE, OBRIGATÓRIO) foi capitalizada para
  garantir que os princípios sejam declarativos e testáveis, sem alterar o sentido
  original do rascunho.
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

### V. Custo sob controle

Toda chamada a provedor pago DEVE passar pelo CostMeter, que estima antes e registra depois.
Cada provedor tem teto mensal global; ao atingir 80% o sistema alerta, a 100% para o coletor
e avisa o ADMIN. Ações disparadas por usuário consomem a cota dele. O orçamento total de dados
e LLM é US$ 20/mês. Antes do LLM DEVEM vir filtragem, deduplicação, cache e Batch API.

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

**Versão**: 1.0.0 | **Ratificada em**: 2026-11-02 | **Última emenda**: 2026-11-02
