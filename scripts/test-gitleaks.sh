#!/usr/bin/env bash
# Self-test for the secret scan (SC-004): a fake secret must make gitleaks fail and
# point at the file; a clean commit must pass. The secret is generated at run time,
# assembled by concatenation, and only ever lives in a throwaway repository.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG="$ROOT/.gitleaks.toml"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

new_repo() {
  git init -q "$1"
  git -C "$1" config user.name "selftest"
  git -C "$1" config user.email "selftest@example.invalid"
  git -C "$1" config commit.gpgsign false
}

scan() { gitleaks git --config "$CONFIG" --redact --verbose --no-banner "$1" 2>&1; }

# 1. Repository with a fake secret: must fail and name the file.
LEAKY="$WORK/leaky"
new_repo "$LEAKY"
SUFFIX="$(openssl rand -hex 18)"
FAKE="ghp""_${SUFFIX}"
echo "token = \"${FAKE}\"" >"$LEAKY/fake-secret.txt"
git -C "$LEAKY" add . && git -C "$LEAKY" commit -q -m "test: leaky"

if OUT="$(scan "$LEAKY")"; then
  echo "FAIL: gitleaks accepted a repository containing a secret" >&2
  exit 1
fi
if ! grep -q "fake-secret.txt" <<<"$OUT"; then
  echo "FAIL: gitleaks failed but did not point at fake-secret.txt" >&2; echo "$OUT" | head -20 >&2
  exit 1
fi

# 2. Clean repository: must pass.
CLEAN="$WORK/clean"
new_repo "$CLEAN"
echo "hello" >"$CLEAN/readme.txt"
git -C "$CLEAN" add . && git -C "$CLEAN" commit -q -m "test: clean"
if ! scan "$CLEAN" >/dev/null; then
  echo "FAIL: gitleaks rejected a clean repository" >&2
  exit 1
fi

echo "OK: gitleaks blocks secrets and accepts clean commits"
