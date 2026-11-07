# Especificação da feature: Fundação técnica

**Branch da feature**: `001-fundacao-tecnica`

**Criada em**: 2026-11-03

**Status**: Rascunho

**Entrada**: descrição do usuário: "Fundação técnica do TrendGap (Sprint 0, parte B). Objetivo: um esqueleto sem regra de negócio de tendências, mas que roda de ponta a ponta e é observável, servindo de base para todas as fatias seguintes."

## Esclarecimentos

### Sessão 2026-11-03

- P: Qual achado de segurança bloqueia o merge na main? → R: Severidade alta ou crítica bloqueia; média e baixa aparecem como aviso no PR sem bloquear.
- P: Quem pode ler as métricas sem login nesta fatia? → R: Só a rede interna dos contêineres (incluindo a pilha opcional); nenhuma porta de métricas publicada para fora.
- Mudança de escopo (decisão do usuário): o controle e o monitoramento de custo de provedores pagos dentro da aplicação saem desta fatia. Os limites de gasto ficam configurados nos painéis dos próprios provedores, e a decisão será reavaliada antes da entrada do primeiro provedor pago (ver ADR de adiamento do controle de custo).

## Cenários de usuário e testes _(obrigatório)_

### História 1 – Subir o sistema e ver a saúde dos componentes (Prioridade: P1)

Como desenvolvedor, a partir de um clone limpo do repositório, executo um único comando documentado e o sistema inteiro sobe localmente. Abro uma página web de status e vejo, para cada componente (backend, serviço de ML e banco de dados), se ele está saudável ou fora do ar. Se um componente cai ou volta, a página mostra a mudança sozinha, sem eu recarregar.

**Por que esta prioridade**: é a base de todas as fatias seguintes (princípio I da constituição: sistema sempre rodando de ponta a ponta). Sem isso nada mais pode ser demonstrado.

**Teste independente**: clonar o repositório numa máquina com apenas os pré-requisitos documentados, criar a configuração local a partir do arquivo de exemplo, executar o comando único e abrir a página de status; derrubar e religar cada componente e observar a página.

**Cenários de aceite**:

1. **Dado** um clone limpo com a configuração local criada a partir do arquivo de exemplo e as imagens já construídas, **quando** executo o comando único de subida, **então** todos os componentes ficam saudáveis em até 3 minutos.
2. **Dado** o sistema no ar, **quando** abro a página de status, **então** vejo backend, serviço de ML e banco de dados, cada um com seu estado (saudável / fora do ar) e o horário da última verificação.
3. **Dado** a página de status aberta com tudo saudável, **quando** paro o serviço de ML, **então** em até 15 segundos a página mostra o serviço de ML como fora do ar, e os demais continuam corretos.
4. **Dado** o serviço de ML mostrado como fora do ar, **quando** ele volta, **então** em até 15 segundos a página o mostra como saudável.
5. **Dado** a página de status aberta, **quando** o backend fica inacessível, **então** a página continua carregada e informa que o backend está fora do ar (e que o estado dos demais é desconhecido), em vez de quebrar ou ficar em branco.
6. **Dado** um visitante sem autenticação, **quando** consulta os pontos de verificação de saúde, **então** recebe apenas o estado de cada componente, sem versões de bibliotecas, variáveis de ambiente, nomes de máquinas, mensagens de exceção ou outros detalhes internos.

---

### História 2 – Verificação automática em todo push e PR (Prioridade: P1)

Como mantenedor, quero que todo push e PR passe automaticamente por formatação, lint, checagem de tipos, testes, varredura de segredos e análise de segurança do código, e que o merge na main só seja possível com tudo verde.

**Por que esta prioridade**: o repositório é público (princípio VI) e a qualidade não pode depender de lembrar (princípio IX).

**Teste independente**: abrir um PR limpo e verificar que todas as verificações passam em até 10 minutos; abrir um PR com um segredo de teste commitado de propósito e verificar que a verificação falha e o merge fica bloqueado.

**Cenários de aceite**:

1. **Dado** um PR com código correto, **quando** ele é aberto, **então** todas as verificações rodam e terminam verdes em até 10 minutos.
2. **Dado** um PR que contém um segredo de teste commitado de propósito, **quando** a verificação roda, **então** ela falha apontando o arquivo do segredo.
3. **Dado** um PR com falha de formatação, lint, tipos, teste ou achado de segurança de severidade alta ou crítica em qualquer componente, **quando** a verificação roda, **então** ela falha e indica qual etapa e qual componente falharam.
4. **Dado** um PR com qualquer verificação vermelha ou pendente, **quando** alguém tenta fazer merge na main, **então** o merge é bloqueado.
5. **Dado** um desenvolvedor tentando criar um commit local com um segredo, **quando** o commit é feito com os hooks instalados, **então** o commit é recusado antes de chegar ao repositório remoto.

---

### História 3 – Configuração só por variáveis de ambiente, com falha rápida (Prioridade: P2)

Como desenvolvedor, configuro cada componente apenas por variáveis de ambiente, todas documentadas num arquivo de exemplo sem valores reais. Se uma configuração obrigatória estiver ausente ou inválida, o componente recusa iniciar e diz qual variável está errada.

**Por que esta prioridade**: evita segredos no repositório e erros silenciosos de configuração; importante, mas o sistema já pode ser demonstrado sem a falha rápida perfeita.

**Teste independente**: remover ou corromper cada variável obrigatória, uma por vez, e verificar que o componente correspondente não inicia e nomeia a variável.

**Cenários de aceite**:

1. **Dado** o arquivo de exemplo, **quando** o inspeciono, **então** toda variável usada por qualquer componente está listada com descrição, se é obrigatória e um valor de exemplo fictício ou vazio, e nenhuma contém valor real de segredo.
2. **Dado** uma variável obrigatória ausente, **quando** o componente inicia, **então** ele encerra com erro que nomeia a variável e diz o que se espera dela, sem imprimir o valor de outras variáveis.
3. **Dado** uma variável com valor inválido (ex.: porta não numérica ou fora do intervalo permitido), **quando** o componente inicia, **então** ele encerra com erro que nomeia a variável e a regra violada, sem ecoar valores que possam ser segredos.
4. **Dado** uma configuração válida, **quando** o componente inicia, **então** ele sobe sem precisar de nenhum arquivo de configuração com valores reais versionado no repositório.

---

### História 4 – Logs estruturados, rastreio entre serviços e métricas (Prioridade: P2)

Como operador, quero logs estruturados em JSON, o mesmo identificador de rastreio em uma requisição que atravessa backend e serviço de ML, métricas básicas expostas e, opcionalmente, uma pilha local para visualizar logs, métricas e traces.

**Por que esta prioridade**: princípio VII; necessário antes dos coletores, mas não bloqueia a demonstração da fundação.

**Teste independente**: disparar uma requisição que passa do backend ao serviço de ML, procurar o identificador de rastreio nos logs dos dois e consultar as métricas expostas; subir a pilha opcional e ver o trace completo.

**Cenários de aceite**:

1. **Dado** o sistema no ar, **quando** qualquer componente escreve um log, **então** cada linha é um objeto JSON válido com, no mínimo, data e hora, nível, componente, mensagem e identificador de rastreio quando houver requisição em curso.
2. **Dado** uma requisição que o backend repassa ao serviço de ML, **quando** consulto os logs dos dois, **então** ambos contêm o mesmo identificador de rastreio para essa requisição.
3. **Dado** o sistema no ar, **quando** consulto as métricas de dentro do ambiente local, **então** vejo, no mínimo, contagem e duração de requisições por componente e estado de saúde de cada componente.
4. **Dado** que não ativei a pilha de observabilidade, **quando** subo o sistema, **então** ele funciona normalmente sem ela.
5. **Dado** que ativei a pilha de observabilidade opcional, **quando** faço uma requisição que atravessa backend e serviço de ML, **então** vejo seus logs, métricas e o trace com os dois trechos numa interface local.
6. **Dado** qualquer log ou métrica, **quando** o inspeciono, **então** não há segredos nem dados pessoais de terceiros.

---

### História 5 – Atualizações de dependências automáticas (Prioridade: P3)

Como mantenedor, quero que atualizações de dependências cheguem como PRs automáticos, que passam pela mesma verificação da História 2.

**Por que esta prioridade**: reduz risco de segurança ao longo do tempo, mas não afeta o funcionamento imediato.

**Teste independente**: com a automação habilitada, verificar que PRs de atualização são abertos para cada ecossistema de dependências do repositório.

**Cenários de aceite**:

1. **Dado** uma dependência desatualizada em qualquer componente ou na verificação automática, **quando** a rotina semanal roda, **então** um PR de atualização é aberto.
2. **Dado** um PR automático de atualização, **quando** ele é aberto, **então** passa pela verificação completa e só pode ser mesclado com tudo verde.

---

### Casos de borda

- Página de status aberta quando o backend está fora: a página precisa continuar exibida e mostrar o backend como fora e os demais como desconhecidos.
- Componente lento, mas não morto: verificação de saúde que não responde dentro do tempo limite conta como fora do ar.
- Banco de dados indisponível na subida: backend não deve ficar em estado "saudável" falso; ele se reporta fora do ar ou aguarda o banco, e a página reflete isso.
- Serviço de ML indisponível quando o backend tenta chamá-lo: o backend responde com erro claro e registra a falha, sem travar nem inventar resposta.

## Requisitos _(obrigatório)_

### Requisitos funcionais

**Subida e status**

- **FR-001**: O sistema DEVE subir por completo (backend, serviço de ML, banco de dados e página de status) com um único comando documentado, a partir de um clone limpo com configuração criada do arquivo de exemplo.
- **FR-002**: Cada componente DEVE expor uma verificação de saúde que distingue "vivo" de "pronto para atender" (o backend só está pronto se o banco estiver acessível).
- **FR-003**: O sistema DEVE oferecer uma página web de status que mostra, para backend, serviço de ML e banco de dados, o estado (saudável, fora do ar ou desconhecido) e o horário da última verificação.
- **FR-004**: A página de status DEVE atualizar sozinha e refletir mudança de estado de qualquer componente em até 15 segundos.
- **FR-005**: As verificações de saúde acessíveis sem autenticação DEVEM expor somente o estado por componente; NÃO PODEM expor versões de bibliotecas, variáveis de ambiente, nomes de máquinas, rotas internas, mensagens de exceção ou detalhes de configuração. Demais pontos administrativos ficam inacessíveis de fora nesta fatia.
- **FR-006**: Nenhuma regra de negócio de tendências DEVE ser implementada nesta fatia; o backend DEVE ter pelo menos uma chamada real ao serviço de ML (ex.: verificação de saúde ou eco) para comprovar a integração ponta a ponta.

**Verificação automática**

- **FR-007**: Todo push e PR DEVE disparar verificação automática de formatação, lint, tipos e testes de cada componente, além de varredura de segredos e análise de segurança do código e das dependências. Achados de severidade alta ou crítica DEVEM falhar a verificação; média e baixa DEVEM aparecer como aviso no PR sem bloquear.
- **FR-008**: A varredura de segredos DEVE rodar também localmente no commit (hook) e no CI; um segredo commitado de propósito DEVE fazer a verificação falhar.
- **FR-009**: A main DEVE estar protegida: merge só por PR, com todas as verificações obrigatórias verdes.
- **FR-010**: A verificação automática de um PR DEVE terminar em até 10 minutos.
- **FR-011**: Mensagens de commit DEVEM seguir Conventional Commits, verificadas por hook local.

**Configuração**

- **FR-012**: Toda configuração DEVE vir de variáveis de ambiente; o repositório DEVE conter um arquivo de exemplo que lista todas, com descrição, obrigatoriedade e valores fictícios ou vazios.
- **FR-013**: Com configuração obrigatória ausente ou inválida, o componente DEVE recusar iniciar e informar qual variável e qual regra falhou, sem ecoar valores de segredos.
- **FR-014**: Nenhum segredo real DEVE existir em arquivos versionados.

**Observabilidade**

- **FR-015**: Todos os componentes DEVEM escrever logs estruturados em JSON com data e hora, nível, componente, mensagem e identificador de rastreio quando houver requisição em curso.
- **FR-016**: Uma requisição que atravessa backend e serviço de ML DEVE carregar o mesmo identificador de rastreio nos logs e traces dos dois.
- **FR-017**: O sistema DEVE expor métricas básicas: contagem e duração de requisições por componente e saúde por componente. As métricas DEVEM ser acessíveis somente pela rede interna do ambiente (componentes e pilha opcional), sem porta publicada para fora.
- **FR-018**: DEVE existir uma pilha local opcional, ativada por um comando ou opção documentados, para visualizar logs, métricas e traces; o sistema DEVE funcionar sem ela.
- **FR-019**: Logs e métricas NÃO PODEM conter segredos nem dados pessoais de terceiros.

**Dependências**

- **FR-020**: Atualizações de dependências de todos os ecossistemas do repositório (backend, serviço de ML, frontend, imagens de contêiner e automação de CI) DEVEM chegar como PRs automáticos semanais, sujeitos à mesma verificação.

### Entidades-chave

- **Componente**: parte do sistema com estado de saúde próprio (backend, serviço de ML, banco de dados). Atributos: nome, estado, horário da última verificação.

## Critérios de sucesso _(obrigatório)_

### Resultados mensuráveis

- **SC-001**: Com as imagens já construídas, todos os componentes ficam saudáveis em até 3 minutos após o comando único de subida, em 3 de 3 tentativas a partir de um clone limpo.
- **SC-002**: A página de status reflete a queda ou a volta de qualquer componente em até 15 segundos, em 100% das trocas testadas (queda e volta de cada componente).
- **SC-003**: 0 ocorrências de versão de biblioteca, variável de ambiente, nome de máquina ou mensagem de exceção nas respostas de saúde acessíveis sem autenticação.
- **SC-004**: Um segredo de teste commitado de propósito faz a verificação automática falhar em 100% das tentativas, e o merge fica bloqueado.
- **SC-005**: A verificação automática completa de um PR termina em até 10 minutos.
- **SC-006**: Para cada variável obrigatória, removê-la ou invalidá-la faz o componente recusar iniciar com mensagem que nomeia a variável, em 100% dos casos.
- **SC-007**: Para uma requisição que atravessa backend e serviço de ML, o mesmo identificador de rastreio aparece nos logs dos dois em 100% das requisições amostradas.
- **SC-008**: Um desenvolvedor novo consegue subir o sistema e ver a página de status seguindo só a documentação do repositório, sem ajuda.

## Premissas

- O ambiente local tem os pré-requisitos documentados instalados (motor de contêineres e ferramentas de cada componente) e acesso à internet para baixar imagens e dependências.
- O limite de 3 minutos considera imagens já construídas; o tempo de construção inicial não entra nessa medida.
- Nenhum provedor pago é integrado nesta fatia. O controle de custo de provedores pagos fica fora da aplicação nesta fase: os limites de gasto são configurados nos painéis dos próprios provedores antes de cada um ser ligado, e a decisão será reavaliada antes da entrada do primeiro provedor pago (ver ADR de adiamento do controle de custo).
- A proteção da main e a varredura de segredos do serviço de hospedagem do repositório são configurações do repositório e devem ser documentadas para que possam ser reproduzidas.
- As escolhas de ferramentas já decididas (ADR-0012 de observabilidade, P-13 de segurança, P-14 de hooks) orientam o plano; qualquer ferramenta nova exige ADR antes do uso.

## Fora do escopo

- Login, contas e papéis.
- Coletores reais e provedores pagos reais.
- Controle e monitoramento de custo de provedores pagos dentro da aplicação (adiado; ver ADR de adiamento do controle de custo).
- Chamadas a LLM.
- Telas além da página de status.
- Deploy em nuvem.
- Canais de notificação (e-mail, in-app).
