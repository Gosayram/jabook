#!/bin/bash
# Copyright 2025 Jabook Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Script to verify that beta icons are different from prod icons

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BETA_ICON="$PROJECT_ROOT/android/app/src/beta/res/mipmap-hdpi/ic_launcher.png"
PROD_ICON="$PROJECT_ROOT/android/app/src/main/res/mipmap-hdpi/ic_launcher.png"

echo "Verifying beta icons..."

if [ ! -f "$BETA_ICON" ]; then
    echo "❌ Error: Beta icon not found at $BETA_ICON"
    echo "   Run: scripts/generate-android-beta-icons.sh"
    exit 1
fi

if [ ! -f "$PROD_ICON" ]; then
    echo "❌ Error: Prod icon not found at $PROD_ICON"
    exit 1
fi

# Check if files are different
if cmp -s "$BETA_ICON" "$PROD_ICON"; then
    echo "❌ Error: Beta and prod icons are identical!"
    echo "   Beta icon: $BETA_ICON"
    echo "   Prod icon: $PROD_ICON"
    echo "   Run: scripts/generate-android-beta-icons.sh"
    exit 1
else
    echo "✅ Beta and prod icons are different (correct)"
    echo "   Beta icon MD5: $(md5 -q "$BETA_ICON")"
    echo "   Prod icon MD5: $(md5 -q "$PROD_ICON")"
fi

# Check adaptive icon files
BETA_BG="$PROJECT_ROOT/android/app/src/beta/res/drawable-hdpi/ic_launcher_background.png"
PROD_BG="$PROJECT_ROOT/android/app/src/main/res/drawable-hdpi/ic_launcher_background.png"

if [ -f "$BETA_BG" ] && [ -f "$PROD_BG" ]; then
    if cmp -s "$BETA_BG" "$PROD_BG"; then
        echo "⚠️  Warning: Beta and prod adaptive backgrounds are identical"
    else
        echo "✅ Beta and prod adaptive backgrounds are different (correct)"
    fi
fi

echo ""
echo "✅ Beta icons verification passed"
echo "   Android will automatically use beta icons from android/app/src/beta/res/"
echo "   when building with --flavor beta"

