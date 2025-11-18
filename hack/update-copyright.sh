#!/bin/bash
# Update copyright year in all files

set -euo pipefail

CURRENT_YEAR=$(date +%Y)
OLD_COPYRIGHT="Copyright [0-9]\{4\} Jabook Contributors"
NEW_COPYRIGHT="Copyright $CURRENT_YEAR Jabook Contributors"
UPDATED=0

# Find all files that might have copyright
while IFS= read -r -d '' file; do
    # Check if file has copyright
    if grep -q "Copyright.*Jabook" "$file" 2>/dev/null; then
        # Check if year needs updating
        if ! grep -q "$NEW_COPYRIGHT" "$file" 2>/dev/null; then
            # Update copyright year (handle both // and # comment styles)
            if [[ "$file" == *.dart ]]; then
                # Dart files use // comments
                if [[ "$(uname)" == "Darwin" ]]; then
                    sed -i '' "s|// Copyright [0-9]\{4\} Jabook|// Copyright $CURRENT_YEAR Jabook|g" "$file"
                else
                    sed -i "s|// Copyright [0-9]\{4\} Jabook|// Copyright $CURRENT_YEAR Jabook|g" "$file"
                fi
            else
                # Other files use # comments
                if [[ "$(uname)" == "Darwin" ]]; then
                    sed -i '' "s|# Copyright [0-9]\{4\} Jabook|# Copyright $CURRENT_YEAR Jabook|g" "$file"
                else
                    sed -i "s|# Copyright [0-9]\{4\} Jabook|# Copyright $CURRENT_YEAR Jabook|g" "$file"
                fi
            fi
            echo "✓ Updated copyright in $file"
            UPDATED=$((UPDATED + 1))
        fi
    fi
done < <(find . -type f \( -name "*.dart" -o -name "*.sh" -o -name "*.yaml" -o -name "*.yml" -o -name "Dockerfile" -o -name "Makefile" \) \
    -not -path "./.dart_tool/*" \
    -not -path "./.git/*" \
    -not -path "./hack/*" \
    -not -path "./build/*" \
    -not -path "./.flutter-plugins*" \
    -not -path "./.packages" \
    -not -path "./pubspec.lock" \
    -print0)

if [ $UPDATED -eq 0 ]; then
    echo "✓ All copyrights are up to date ($CURRENT_YEAR)"
else
    echo ""
    echo "✓ Updated $UPDATED files to copyright year $CURRENT_YEAR"
fi
