#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TMP_FILE=$(mktemp)
trap 'rm -f "$TMP_FILE"' EXIT

# Find files with Log. but not LogUtils
rg -l "Log\.(v|d|i|w|e)\(" android/app/src/main/kotlin \
  --glob '!**/util/LogUtils.kt' \
  --glob '!**/compose/core/logger/AndroidLogger.kt' \
  > "$TMP_FILE"

if [[ ! -s "$TMP_FILE" ]]; then
  echo "✅ No files to convert"
  exit 0
fi

echo "Found $(wc -l < "$TMP_FILE") files to convert"

while IFS= read -r file; do
  echo "Processing: $file"
  perl -i -pe 's/\bLog\.(v|d|i|w|e)\(/LogUtils.$1(/g' "$file"
done < "$TMP_FILE"

echo "✅ Conversion complete"