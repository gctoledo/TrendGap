# 0017. Qualidade estática do backend

- Status: aceito
- Data: 2026-11-07

## Contexto
O backend Java 25 precisa de formatação, análise de nulidade e cobertura verificadas no build (`./mvnw spotless:apply verify`) e no CI. O código gerado do cliente do ML não deve entrar nessas conta. As anotações JSpecify só protegem se houver um verificador que as use (plan.md, Desenho do backend, e research.md, R-09).

## Opções consideradas
1. Spotless (google-java-format) em `verify`; Error Prone 2.52 + NullAway 0.14 com JSpecify como erro; JaCoCo com `check` de 80% de linhas, excluindo código gerado e `TrendGapApplication`.
2. Anotar com JSpecify sem verificador de nulidade, só como documentação.
3. Sem análise de nulidade no build: apenas formatação e testes.

## Decisão
Escolhemos a opção 1. A nulidade é verificada pelo compilador, então as anotações valem em tempo de build. O formatador fixo evita discutir estilo em revisão. A cobertura de 80% vale para o código escrito à mão.

O `jacoco.check.skip=true` fica ativo até a primeira tarefa com lógica (T016), para o esqueleto compilar sem regra de negócio.

## Consequências
- Positivas: erros de nulidade e de formatação falham o build local e o CI; o limite de cobertura passa a valer quando há lógica.
- Negativas: Error Prone no JDK 25 exige `--add-exports` em `.mvn/jvm.config`; o pacote raiz precisa de `@NullMarked`; a lista de exclusões do código gerado precisa ser mantida junto com o `pom.xml`.
