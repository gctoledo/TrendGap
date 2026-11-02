# 0007. Modelo inicial: regressão logística regularizada com calibração sigmoide

- Status: aceito
- Data: 2026-11-02

## Contexto
A probabilidade de tendência chegar ao Brasil vem de modelo estatístico, nunca do LLM (D-08).

## Opções consideradas
1. Regressão logística regularizada + calibração sigmoide (escolhida como modelo inicial).
2. LightGBM (adotado somente com centenas de rótulos e ganho no backtest).

## Decisão
Escolhemos regressão logística regularizada com calibração sigmoide como modelo inicial. LightGBM só será avaliado com centenas de rótulos e ganho comprovado no backtest.

## Consequências
- Positivas: a definir.
- Negativas: a definir.
