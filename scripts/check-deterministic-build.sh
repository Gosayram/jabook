#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUILD_LOGIC_FILES=(
  "android/build.gradle.kts"
  "android/settings.gradle.kts"
  "android/app/build.gradle.kts"
  "android/gradle/libs.versions.toml"
)

TMP_HITS="$(mktemp)"
trap 'rm -f "$TMP_HITS"' EXIT

# 1) Non-deterministic time/random usage in build logic
rg -n \
  -e 'System\.currentTimeMillis\(' \
  -e 'Instant\.now\(' \
  -e 'LocalDateTime\.now\(' \
  -e 'UUID\.randomUUID\(' \
  "${BUILD_LOGIC_FILES[@]}" > "$TMP_HITS" || true

if [[ -s "$TMP_HITS" ]]; then
  echo "❌ Deterministic build check failed: time/random usage in build logic"
  cat "$TMP_HITS"
  exit 1
fi

# 2) Dynamic/changing dependency versions in build logic
rg -n \
  -e ':[^"'"'"']*\+["'"'"']' \
  -e 'version\s*=\s*"[^"]*SNAPSHOT[^"]*"' \
  -e 'version\s*=\s*"latest\.[^"]*"' \
  "${BUILD_LOGIC_FILES[@]}" > "$TMP_HITS" || true

if [[ -s "$TMP_HITS" ]]; then
  echo "❌ Deterministic build check failed: dynamic/changing dependency versions found"
  cat "$TMP_HITS"
  exit 1
fi

echo "✅ Deterministic build guard passed"
