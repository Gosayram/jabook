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

# Script to generate Android beta flavor-specific icons
# This places icons directly in android/app/src/beta/res/ without modifying pubspec.yaml

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BETA_ICONS_DIR="$PROJECT_ROOT/assets/icons/beta"
ANDROID_BETA_RES_DIR="$PROJECT_ROOT/android/app/src/beta/res"

# Check if beta icons exist
if [ ! -f "$BETA_ICONS_DIR/app_icon.png" ]; then
    echo "Error: Beta icons not found. Run scripts/generate-beta-icons.sh first."
    exit 1
fi

echo "Generating Android beta flavor-specific icons..."

# Create directory structure
mkdir -p "$ANDROID_BETA_RES_DIR/mipmap-mdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/mipmap-hdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/mipmap-xhdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/mipmap-xxhdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/mipmap-xxxhdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/mipmap-anydpi-v26"
mkdir -p "$ANDROID_BETA_RES_DIR/drawable-mdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/drawable-hdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/drawable-xhdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/drawable-xxhdpi"
mkdir -p "$ANDROID_BETA_RES_DIR/drawable-xxxhdpi"

# Check for image conversion tool
if command -v sips &> /dev/null; then
    echo "Using sips for image conversion..."
    
    # Generate mipmap icons (launcher icons)
    # mdpi: 48x48, hdpi: 72x72, xhdpi: 96x96, xxhdpi: 144x144, xxxhdpi: 192x192
    sips -z 48 48 "$BETA_ICONS_DIR/app_icon.png" --out "$ANDROID_BETA_RES_DIR/mipmap-mdpi/ic_launcher.png" > /dev/null
    sips -z 72 72 "$BETA_ICONS_DIR/app_icon.png" --out "$ANDROID_BETA_RES_DIR/mipmap-hdpi/ic_launcher.png" > /dev/null
    sips -z 96 96 "$BETA_ICONS_DIR/app_icon.png" --out "$ANDROID_BETA_RES_DIR/mipmap-xhdpi/ic_launcher.png" > /dev/null
    sips -z 144 144 "$BETA_ICONS_DIR/app_icon.png" --out "$ANDROID_BETA_RES_DIR/mipmap-xxhdpi/ic_launcher.png" > /dev/null
    sips -z 192 192 "$BETA_ICONS_DIR/app_icon.png" --out "$ANDROID_BETA_RES_DIR/mipmap-xxxhdpi/ic_launcher.png" > /dev/null
    
    # Generate adaptive icon foreground (different sizes for different densities)
    # mdpi: 108x108, hdpi: 162x162, xhdpi: 216x216, xxhdpi: 324x324, xxxhdpi: 432x432
    sips -z 108 108 "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" --out "$ANDROID_BETA_RES_DIR/drawable-mdpi/ic_launcher_foreground.png" > /dev/null
    sips -z 162 162 "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" --out "$ANDROID_BETA_RES_DIR/drawable-hdpi/ic_launcher_foreground.png" > /dev/null
    sips -z 216 216 "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" --out "$ANDROID_BETA_RES_DIR/drawable-xhdpi/ic_launcher_foreground.png" > /dev/null
    sips -z 324 324 "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" --out "$ANDROID_BETA_RES_DIR/drawable-xxhdpi/ic_launcher_foreground.png" > /dev/null
    sips -z 432 432 "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" --out "$ANDROID_BETA_RES_DIR/drawable-xxxhdpi/ic_launcher_foreground.png" > /dev/null
    
    # Generate adaptive icon background (solid color, different sizes)
    # Create solid color images for each density
    if command -v python3 &> /dev/null; then
        python3 -c "
from PIL import Image
sizes = [
    ('mdpi', 108, 108),
    ('hdpi', 162, 162),
    ('xhdpi', 216, 216),
    ('xxhdpi', 324, 324),
    ('xxxhdpi', 432, 432)
]
color = (169, 182, 95)  # #A9B65F
for density, w, h in sizes:
    img = Image.new('RGB', (w, h), color)
    img.save('$ANDROID_BETA_RES_DIR/drawable-${density}/ic_launcher_background.png', 'PNG')
    print(f'Created ic_launcher_background.png for ${density}')
" 2>/dev/null || echo "Note: PIL not available, background images need manual creation"
    fi
    
elif command -v convert &> /dev/null; then
    echo "Using ImageMagick for image conversion..."
    
    # Generate mipmap icons
    convert "$BETA_ICONS_DIR/app_icon.png" -resize 48x48 "$ANDROID_BETA_RES_DIR/mipmap-mdpi/ic_launcher.png"
    convert "$BETA_ICONS_DIR/app_icon.png" -resize 72x72 "$ANDROID_BETA_RES_DIR/mipmap-hdpi/ic_launcher.png"
    convert "$BETA_ICONS_DIR/app_icon.png" -resize 96x96 "$ANDROID_BETA_RES_DIR/mipmap-xhdpi/ic_launcher.png"
    convert "$BETA_ICONS_DIR/app_icon.png" -resize 144x144 "$ANDROID_BETA_RES_DIR/mipmap-xxhdpi/ic_launcher.png"
    convert "$BETA_ICONS_DIR/app_icon.png" -resize 192x192 "$ANDROID_BETA_RES_DIR/mipmap-xxxhdpi/ic_launcher.png"
    
    # Generate adaptive icon foreground
    convert "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" -resize 108x108 "$ANDROID_BETA_RES_DIR/drawable-mdpi/ic_launcher_foreground.png"
    convert "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" -resize 162x162 "$ANDROID_BETA_RES_DIR/drawable-hdpi/ic_launcher_foreground.png"
    convert "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" -resize 216x216 "$ANDROID_BETA_RES_DIR/drawable-xhdpi/ic_launcher_foreground.png"
    convert "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" -resize 324x324 "$ANDROID_BETA_RES_DIR/drawable-xxhdpi/ic_launcher_foreground.png"
    convert "$BETA_ICONS_DIR/app_icon_adaptive_fg.png" -resize 432x432 "$ANDROID_BETA_RES_DIR/drawable-xxxhdpi/ic_launcher_foreground.png"
    
    # Generate adaptive icon background (solid color)
    convert -size 108x108 xc:"#A9B65F" "$ANDROID_BETA_RES_DIR/drawable-mdpi/ic_launcher_background.png"
    convert -size 162x162 xc:"#A9B65F" "$ANDROID_BETA_RES_DIR/drawable-hdpi/ic_launcher_background.png"
    convert -size 216x216 xc:"#A9B65F" "$ANDROID_BETA_RES_DIR/drawable-xhdpi/ic_launcher_background.png"
    convert -size 324x324 xc:"#A9B65F" "$ANDROID_BETA_RES_DIR/drawable-xxhdpi/ic_launcher_background.png"
    convert -size 432x432 xc:"#A9B65F" "$ANDROID_BETA_RES_DIR/drawable-xxxhdpi/ic_launcher_background.png"
    
else
    echo "Error: No image conversion tool found (sips or ImageMagick)"
    exit 1
fi

# Copy adaptive icon XML configuration
cp "$PROJECT_ROOT/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" \
   "$ANDROID_BETA_RES_DIR/mipmap-anydpi-v26/ic_launcher.xml"

echo ""
echo "✅ Android beta flavor-specific icons generated successfully"
echo "Icons placed in: $ANDROID_BETA_RES_DIR"
echo ""
echo "Note: Android will automatically use these icons when building beta flavor"
echo "No changes to pubspec.yaml needed!"

