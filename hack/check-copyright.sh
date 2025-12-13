#!/bin/bash
# Check that all Dart files have correct copyright header

set -euo pipefail

CURRENT_YEAR=$(date +%Y)
COPYRIGHT="Copyright $CURRENT_YEAR Jabook Contributors"
ERRORS=0

# Find all Dart files and check copyright
while IFS= read -r -d '' file; do
    # Check if file has copyright
    if ! grep -q "Copyright.*Jabook" "$file" 2>/dev/null; then
        echo "❌ Missing copyright in $file"
        ERRORS=$((ERRORS + 1))
    else
        # Check if copyright is correct
        if ! grep -q "$COPYRIGHT" "$file" 2>/dev/null; then
            echo "⚠️  Wrong copyright in $file"
            ERRORS=$((ERRORS + 1))
        fi
    fi
done < <(find . -name "*.dart" \
    -not -path "./.dart_tool/*" \
    -not -path "./.git/*" \
    -not -path "./hack/*" \
    -not -path "./build/*" \
    -not -path "./lib/l10n/*" \
    -not -path "./.flutter-plugins*" \
    -not -path "./.packages" \
    -not -path "./packages/*" \
    -print0)

if [ $ERRORS -eq 0 ]; then
    echo "✅ All files have correct copyright"
    exit 0
else
    echo ""
    echo "❌ Found $ERRORS files with missing or incorrect copyright"
    exit 1
fi
