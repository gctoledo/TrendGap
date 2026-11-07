# 0018. Ferramentas do frontend

- Status: aceito
- Data: 2026-11-07

## Contexto
A SPA em React e TypeScript (ADR-0009) precisa de estado de servidor com consulta periódica a cada 5 s, lint com regras de tipo, formatação e testes de componente. As versões foram verificadas em 2026-11-03 (research.md, R-12).

## Opções consideradas
1. TanStack Query 5.105 para estado de servidor; ESLint 10 flat com typescript-eslint 8.72 (`strictTypeChecked`); Prettier 3.9; Vitest 5.1 com Testing Library 16.3 e jsdom; TypeScript 6.0.3.
2. Estado de servidor com `fetch` em `useEffect` e `setInterval`, sem biblioteca.
3. Mock de rede com MSW nos testes.
4. TypeScript 7.0.

## Decisão
Escolhemos a opção 1. O TanStack Query cobre `refetchInterval: 5000` e `refetchIntervalInBackground` sem timers manuais. Os mocks de rede usam `vi.stubGlobal('fetch', ...)`; o MSW fica de fora por não ser necessário agora (YAGNI).

O TypeScript fica na 6.0.3 porque o `typescript-eslint` 8.72 declara peer `typescript` menor que 6.1.0. O Dependabot propõe a troca para a 7 quando o `typescript-eslint` suportar; o CI barra se quebrar.

## Consequências
- Positivas: polling e reexecução sem código próprio de timer; regras de tipo no lint; testes sem dependência de rede.
- Negativas: o TypeScript fica atrás da 7 até o `typescript-eslint` suportar; `frontend/` tem lockfile próprio, sem workspace na raiz, e precisa ser atualizado à parte.
- Dependências de apoio: `@testing-library/dom` (peer do Testing Library) e `@types/node` (tipos do `vite.config.ts`); `@eslint/js` e `eslint/config` compõem a configuração flat do ESLint.
