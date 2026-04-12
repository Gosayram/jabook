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

# Use ripgrep if available, fall back to grep for CI environments without rg
if command -v rg >/dev/null 2>&1; then
    search_patterns() { rg -n "$@" "${BUILD_LOGIC_FILES[@]}" > "$TMP_HITS" || true; }
else
    search_patterns() {
        : > "$TMP_HITS"
        for pattern in "$@"; do
            for file in "${BUILD_LOGIC_FILES[@]}"; do
                grep -nE "$pattern" "$file" >> "$TMP_HITS" || true
            done
        done
    }
fi

# 1) Non-deterministic time/random usage in build logic
search_patterns \
  -e 'System\.currentTimeMillis\(' \
  -e 'Instant\.now\(' \
  -e 'LocalDateTime\.now\(' \
  -e 'UUID\.randomUUID\('

if [[ -s "$TMP_HITS" ]]; then
  echo "❌ Deterministic build check failed: time/random usage in build logic"
  cat "$TMP_HITS"
  exit 1
fi

# 2) Dynamic/changing dependency versions in build logic
search_patterns \
  -e ':[^"'"'"']*\+["'"'"']' \
  -e 'version\s*=\s*"[^"]*SNAPSHOT[^"]*"' \
  -e 'version\s*=\s*"latest\.[^"]*"'

if [[ -s "$TMP_HITS" ]]; then
  echo "❌ Deterministic build check failed: dynamic/changing dependency versions found"
  cat "$TMP_HITS"
  exit 1
fi

echo "✅ Deterministic build guard passed"