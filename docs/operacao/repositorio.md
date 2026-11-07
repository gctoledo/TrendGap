# Proteção do repositório

A `main` só recebe mudanças por pull request com o check `ci-ok` verde. Force push e exclusão da
branch são bloqueados. O ruleset versionado está em `.github/rulesets/main.json`.

## Aplicar o ruleset

Pela interface: *Settings → Rules → Rulesets → New ruleset → Import a ruleset* e escolha
`.github/rulesets/main.json`.

Pela CLI (precisa de permissão de admin no repositório):

```sh
gh api repos/:owner/:repo/rulesets --method POST --input .github/rulesets/main.json
gh api repos/:owner/:repo/rulesets --jq '.[].name'   # deve listar main-protection
```

O check `ci-ok` precisa já ter rodado ao menos uma vez em algum PR para aparecer na lista de
checks selecionáveis na interface.

## Secret scanning e push protection

*Settings → Code security*: ligue **Secret scanning** e **Push protection**. Pela CLI:

```sh
gh api repos/:owner/:repo --method PATCH --input - <<'JSON'
{"security_and_analysis":{"secret_scanning":{"status":"enabled"},"secret_scanning_push_protection":{"status":"enabled"}}}
JSON
gh api repos/:owner/:repo --jq '.security_and_analysis.secret_scanning.status'   # enabled
```

## Defesa local

Os hooks do lefthook (gitleaks no pre-commit) recusam segredos antes do commit; o job `gitleaks`
do CI repete a verificação e derruba o `ci-ok`.
