#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_FILE="android/app/build.gradle.kts"
TEMP_FILE="$GRADLE_FILE.tmp"

# Check if gradle file exists
if [[ ! -f "$GRADLE_FILE" ]]; then
    echo "Error: Gradle file $GRADLE_FILE not found"
    exit 1
fi

# Check if minSdk is already set to 21
if grep -qE "minSdk[[:space:]]*=[[:space:]]*21" "$GRADLE_FILE"; then
    echo "minSdk is already set to 21 in $GRADLE_FILE"
    exit 0
fi

# Create backup
cp "$GRADLE_FILE" "$GRADLE_FILE.backup"

# Replace minSdk = flutter.minSdkVersion with minSdk = 21
# This ensures we support Android 5.0+ (API 21) instead of Flutter's default API 24
# Use sed for simpler and more reliable pattern matching (works on both Linux and macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS sed requires empty string after -i
    sed -i '' 's/minSdk[[:space:]]*=[[:space:]]*flutter\.minSdkVersion/minSdk = 21/g' "$GRADLE_FILE"
else
    # Linux sed
    sed -i 's/minSdk[[:space:]]*=[[:space:]]*flutter\.minSdkVersion/minSdk = 21/g' "$GRADLE_FILE"
fi

echo "Successfully patched $GRADLE_FILE: minSdk set to 21 (Android 5.0+)"

