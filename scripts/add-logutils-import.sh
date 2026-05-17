#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TMP_FILE=$(mktemp)
trap 'rm -f "$TMP_FILE"' EXIT

# Find files with LogUtils usage but missing import
rg -l "LogUtils\." android/app/src/main/kotlin \
  --glob '!**/util/LogUtils.kt' \
  --glob '!**/compose/core/logger/AndroidLogger.kt' \
  | while IFS= read -r file; do
    if ! grep -q "import.*LogUtils" "$file"; then
      echo "$file" >> "$TMP_FILE"
    fi
  done

if [[ ! -s "$TMP_FILE" ]]; then
  echo "✅ All files have LogUtils import"
  exit 0
fi

echo "Found $(wc -l < "$TMP_FILE") files missing LogUtils import"

# Add import after package statement
while IFS= read -r file; do
  echo "Adding import to: $file"
  sed -i '' '/^package /a\
\
import com.jabook.app.jabook.util.LogUtils
' "$file"
done < "$TMP_FILE"

echo "✅ Import added to all files"