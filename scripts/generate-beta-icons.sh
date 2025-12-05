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

# Script to generate beta icon sizes from beta-logo.png

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_IMAGE="$PROJECT_ROOT/beta-logo.png"
OUTPUT_DIR="$PROJECT_ROOT/assets/icons/beta"

# Check if source image exists
if [ ! -f "$SOURCE_IMAGE" ]; then
    echo "Error: Source image not found at $SOURCE_IMAGE"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check if sips is available (macOS)
if command -v sips &> /dev/null; then
    echo "Using sips for image conversion..."
    
    # Generate all required sizes
    sizes=(16 32 48 64 128 192 256 512 1024)
    
    for size in "${sizes[@]}"; do
        output_file="$OUTPUT_DIR/app_icon_${size}.png"
        echo "Generating ${size}x${size} -> $output_file"
        sips -z "$size" "$size" "$SOURCE_IMAGE" --out "$output_file" > /dev/null
    done
    
    # Copy main icon (1024x1024)
    cp "$OUTPUT_DIR/app_icon_1024.png" "$OUTPUT_DIR/app_icon.png"
    
    # Generate adaptive icon background (solid color #A9B65F)
    echo "Generating adaptive icon background..."
    # Create a solid color image using sips
    # Note: sips doesn't directly create solid colors, so we'll use a workaround
    # Create a temporary image and fill it with the color
    temp_bg="/tmp/beta_bg_temp.png"
    # Use Python or another tool if available, otherwise create from source
    # For now, we'll create it from the source image with a color overlay
    # This is a simplified approach - in production, you might want to use ImageMagick or similar
    
    echo "Note: Adaptive icon background should be created manually or with ImageMagick"
    echo "Expected: 1024x1024 solid color #A9B65F (RGB: 169, 182, 95)"
    
    # Generate adaptive icon foreground (logo without background)
    echo "Generating adaptive icon foreground..."
    # For now, copy the main icon - in production, you'd remove the background
    cp "$OUTPUT_DIR/app_icon_1024.png" "$OUTPUT_DIR/app_icon_adaptive_fg.png"
    echo "Note: Adaptive icon foreground should have transparent background"
    echo "Consider using ImageMagick or similar tool to remove background"
    
elif command -v convert &> /dev/null; then
    echo "Using ImageMagick for image conversion..."
    
    # Generate all required sizes
    sizes=(16 32 48 64 128 192 256 512 1024)
    
    for size in "${sizes[@]}"; do
        output_file="$OUTPUT_DIR/app_icon_${size}.png"
        echo "Generating ${size}x${size} -> $output_file"
        convert "$SOURCE_IMAGE" -resize "${size}x${size}" "$output_file"
    done
    
    # Copy main icon
    cp "$OUTPUT_DIR/app_icon_1024.png" "$OUTPUT_DIR/app_icon.png"
    
    # Generate adaptive icon background (solid color #A9B65F)
    echo "Generating adaptive icon background..."
    convert -size 1024x1024 xc:"#A9B65F" "$OUTPUT_DIR/app_icon_adaptive_bg.png"
    
    # Generate adaptive icon foreground (logo without background)
    echo "Generating adaptive icon foreground..."
    # This assumes the logo has a transparent or removable background
    # Adjust the command based on your image processing needs
    convert "$SOURCE_IMAGE" -resize 1024x1024 -background transparent -gravity center -extent 1024x1024 "$OUTPUT_DIR/app_icon_adaptive_fg.png"
    
else
    echo "Error: No image conversion tool found (sips or ImageMagick)"
    echo "Please install ImageMagick or use macOS with sips"
    exit 1
fi

echo ""
echo "✅ Beta icons generated successfully in $OUTPUT_DIR"
echo ""
echo "Generated files:"
ls -lh "$OUTPUT_DIR"/*.png | awk '{print "  " $9 " (" $5 ")"}'
echo ""
echo "⚠️  Note: Adaptive icon components may need manual adjustment:"
echo "  - app_icon_adaptive_bg.png should be solid color #A9B65F"
echo "  - app_icon_adaptive_fg.png should have transparent background"

