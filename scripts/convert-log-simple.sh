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
  | while IFS= read -r file; do
    if ! grep -q "import.*LogUtils" "$file"; then
      echo "$file" >> "$TMP_FILE"
    fi
  done

if [[ ! -s "$TMP_FILE" ]]; then
  echo "✅ No files to convert"
  exit 0
fi

echo "Found $(wc -l < "$TMP_FILE") files to convert"

while IFS= read -r file; do
  echo "Processing: $file"
  
  # Create backup
  cp "$file" "$file.bak"
  
  # Replace Log. with LogUtils.
  sed -i '' 's/\bLog\.\(v\|d\|i\|w\|e\)(/LogUtils.\1(/g' "$file"
  
done < "$TMP_FILE"

echo "✅ Conversion complete. Add imports with add-logutils-import.sh if needed."