---
name: new-adr
description: Cria um ADR (formato MADR) em docs/adr/ com o próximo número. Use quando uma decisão de arquitetura, dependência ou padrão for tomada ou mudar.
---
# Criar ADR
1. Liste docs/adr/ e use o próximo número com 4 dígitos.
2. Copie docs/adr/template.md para docs/adr/NNNN-titulo-em-kebab-case.md.
3. Preencha em português: contexto, opções consideradas (pelo menos 2), decisão, consequências positivas e negativas, status e data.
4. Se substituir outro ADR, marque o antigo como "Substituído por NNNN" e ligue os dois.
5. Atualize o índice em docs/adr/README.md (número, título, status, data).
Um ADR deve caber em uma tela.
