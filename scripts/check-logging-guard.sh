#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE_FILE="$ROOT_DIR/scripts/logging-direct-usage-baseline.txt"

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "❌ Logging baseline file is missing: $BASELINE_FILE"
  exit 1
fi

TMP_CURRENT="$(mktemp)"
TMP_EXTRA="$(mktemp)"
trap 'rm -f "$TMP_CURRENT" "$TMP_EXTRA"' EXIT

cd "$ROOT_DIR"

SEARCH_PATTERN='println\(|android\.util\.Log\.|(^|[^A-Za-z0-9_])Log\.(v|d|i|w|e)\('

if command -v rg >/dev/null 2>&1; then
  rg -n "$SEARCH_PATTERN" \
    android/app/src/main/kotlin \
    --glob '!**/util/LogUtils.kt' \
    --glob '!**/compose/core/logger/AndroidLogger.kt' \
    | cut -d: -f1 | sort -u > "$TMP_CURRENT"
else
  grep -R -n -E "$SEARCH_PATTERN" android/app/src/main/kotlin \
    --exclude='LogUtils.kt' \
    --exclude='AndroidLogger.kt' \
    | cut -d: -f1 | sort -u > "$TMP_CURRENT" || true
fi

comm -23 "$TMP_CURRENT" "$BASELINE_FILE" > "$TMP_EXTRA" || true

if [[ -s "$TMP_EXTRA" ]]; then
  echo "❌ New direct Log/println usages detected outside baseline:"
  cat "$TMP_EXTRA"
  echo
  echo "Use LoggerFactory/LogUtils instead of direct android.util.Log / println."
  exit 1
fi

echo "✅ Logging guard passed (no new direct Log/println usages)"
