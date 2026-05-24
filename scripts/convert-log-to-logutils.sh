#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TMP_FILE=$(mktemp)
trap 'rm -f "$TMP_FILE"' EXIT

# Find all files with android.util.Log calls
rg -n "android\.util\.Log\." android/app/src/main/kotlin \
  --glob '!**/util/LogUtils.kt' \
  --glob '!**/compose/core/logger/AndroidLogger.kt' \
  | cut -d: -f1 | sort -u > "$TMP_FILE"

if [[ ! -s "$TMP_FILE" ]]; then
  echo "✅ No files with android.util.Log found"
  exit 0
fi

echo "Found $(wc -l < "$TMP_FILE") files to convert"

# Process each file
while IFS= read -r file; do
  echo "Processing: $file"
  
  # Create backup
  cp "$file" "$file.bak"
  
  # Replace android.util.Log.v( with LogUtils.v(
  sed -i '' 's/android\.util\.Log\.v(/LogUtils.v(/g' "$file"
  
  # Replace android.util.Log.d( with LogUtils.d(
  sed -i '' 's/android\.util\.Log\.d(/LogUtils.d(/g' "$file"
  
  # Replace android.util.Log.i( with LogUtils.i(
  sed -i '' 's/android\.util\.Log\.i(/LogUtils.i(/g' "$file"
  
  # Replace android.util.Log.w( with LogUtils.w(
  sed -i '' 's/android\.util\.Log\.w(/LogUtils.w(/g' "$file"
  
  # Replace android.util.Log.e( with LogUtils.e(
  sed -i '' 's/android\.util\.Log\.e(/LogUtils.e(/g' "$file"
  
done < "$TMP_FILE"

echo "✅ Conversion complete. Review changes and run 'git checkout *.bak' to revert if needed."
echo "Don't forget to remove *.bak files when done!"