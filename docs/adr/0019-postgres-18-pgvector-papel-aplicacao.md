# 0019. PostgreSQL 18 com pgvector e papel de aplicação sem superusuário

- Status: aceito
- Data: 2026-11-07

## Contexto
O banco guarda os dados do TrendGap, e o Java é dono desses dados (ADR-0005). Ele precisa da extensão `vector`. Essa extensão não é *trusted*: só um superusuário a cria. O backend não deve rodar como superusuário (constituição, princípio VI). Ver research.md, R-04 e R-05.

## Opções consideradas
1. Imagem `pgvector/pgvector:0.8.7-pg18-trixie` no Compose e nos testes; papel de aplicação sem superusuário criado por `infra/postgres/init/01-app-role.sh`; migração V1 com `CREATE EXTENSION IF NOT EXISTS vector`.
2. PostgreSQL 17 (`pgvector/pgvector:0.8.7-pg17-trixie`): alternativa, que vira fallback se o teste de integração falhar com o PG 18.
3. Backend conectado com o superusuário da imagem: descartada, porque viola o menor privilégio.

## Decisão
Escolhemos a opção 1. O superusuário da imagem existe só dentro do contêiner do Postgres. O script de inicialização cria o banco `trendgap`, o papel `TRENDGAP_DB_USERNAME` (LOGIN, sem SUPERUSER, CREATEDB ou CREATEROLE, dono do schema `public`) e a extensão.

A migração V1 é no-op no Compose, porque a extensão já existe, e cria a extensão nos testes com Testcontainers. Assim a migração continua verificável. O volume nomeado monta em `/var/lib/postgresql`, por causa do layout do PG 18.

## Consequências
- Positivas: menor privilégio no backend; migração verificável nos testes.
- Negativas: a tag da imagem aparece no Compose e nos testes; o plano centraliza a leitura nos testes numa propriedade Maven (`pgvector.image`), mas o Dependabot só atualiza a do Compose; se o PG 18 falhar, a troca para pg17 é feita nos dois lugares.
