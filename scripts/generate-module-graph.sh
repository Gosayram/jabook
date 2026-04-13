#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS_FILE="$ROOT_DIR/android/settings.gradle.kts"
OUT_FILE="${1:-$ROOT_DIR/docs/architecture/module-deps.dot}"

if [[ ! -f "$SETTINGS_FILE" ]]; then
  echo "❌ settings.gradle.kts not found: $SETTINGS_FILE"
  exit 1
fi

TMP_MODULES="$(mktemp)"
trap 'rm -f "$TMP_MODULES"' EXIT

# Use ripgrep if available, fall back to grep for CI environments without rg
if command -v rg >/dev/null 2>&1; then
    rg -n 'include\(' "$SETTINGS_FILE"
else
    grep -nE 'include\(' "$SETTINGS_FILE" || true
fi \
  | sed -E 's/.*include\((.*)\).*/\1/' \
  | tr ',' '\n' \
  | sed -E 's/^[[:space:]]*//; s/[[:space:]]*$//' \
  | sed -E 's/^"([^"]+)"$/\1/' \
  | sed -E 's/^'\''([^'\'']+)'\''$/\1/' \
  | sed -E 's/^://g' \
  | sed '/^$/d' \
  | sort -u > "$TMP_MODULES"

mkdir -p "$(dirname "$OUT_FILE")"

{
  echo "digraph ModuleDeps {"
  echo "  rankdir=LR;"
  echo "  \"root\" [shape=box,style=filled,fillcolor=\"#eef6ff\"];"
  while IFS= read -r module; do
    echo "  \"$module\" [shape=box];"
    echo "  \"root\" -> \"$module\";"
  done < "$TMP_MODULES"
  echo "}"
} > "$OUT_FILE"

echo "✅ Module graph generated: $OUT_FILE"
