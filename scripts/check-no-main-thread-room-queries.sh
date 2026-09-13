#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_DIR="$ROOT_DIR/android/app/src"
PATTERN='allowMainThreadQueries[[:space:]]*\('

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "❌ Main-thread Room query guard failed: source directory not found: $SOURCE_DIR"
  exit 1
fi

if command -v rg >/dev/null 2>&1; then
  if rg -n --glob '*.{kt,java}' "$PATTERN" "$SOURCE_DIR"; then
    echo "❌ Room main-thread queries are forbidden in production and unit tests."
    exit 1
  fi
elif grep -R -n -E --include='*.kt' --include='*.java' "$PATTERN" "$SOURCE_DIR"; then
  echo "❌ Room main-thread queries are forbidden in production and unit tests."
  exit 1
fi

echo "✅ Room main-thread query guard passed"
