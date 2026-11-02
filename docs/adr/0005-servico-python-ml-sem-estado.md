# 0005. Serviço Python de ML sem estado, com contrato OpenAPI

- Status: aceito
- Data: 2026-11-02

## Contexto
Os cálculos de ML, estatística e embeddings precisam de um serviço separado do backend Java.

## Opções consideradas
1. Serviço Python (FastAPI, uv, Python 3.13) sem estado, só para ML, estatística e embeddings, com contrato OpenAPI (escolhida).
2. Outras opções: a definir.

## Decisão
Escolhemos um serviço Python (FastAPI, uv, Python 3.13) sem estado, restrito a ML, estatística e embeddings, com contrato OpenAPI. O Java é dono dos dados.

## Consequências
- Positivas: a definir.
- Negativas: a definir.
