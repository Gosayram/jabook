#!/bin/bash
# Check for duplicate top-level keys in ARB localization files

set -e

EN_FILE="lib/l10n/app_en.arb"
RU_FILE="lib/l10n/app_ru.arb"

EXIT_CODE=0

echo "Checking for duplicate top-level keys in ARB files..."
echo ""

# Function to find duplicates in a file
check_duplicates() {
    local file=$1
    local file_name=$(basename "$file")
    
    if [ ! -f "$file" ]; then
        echo "⚠️  $file_name not found"
        return
    fi
    
    echo "Checking $file_name..."
    
    # Extract top-level keys (keys at root level, indent <= 2 spaces)
    # Match pattern: "key": or "@key": at the beginning of line (after optional spaces)
    local duplicates=$(awk '
    BEGIN {
        duplicates = ""
    }
    {
        # Skip comments and empty lines
        if ($0 ~ /^[[:space:]]*\/\// || $0 ~ /^[[:space:]]*$/) {
            next
        }
        
        # Check if line has <= 2 spaces indent (root level)
        match($0, /^[[:space:]]*/)
        indent = RLENGTH
        
        if (indent <= 2) {
            # Match "key": or "@key": pattern
            if (match($0, /^[[:space:]]*"(@?[^"]+)":/)) {
                key = substr($0, RSTART + indent, RLENGTH - indent)
                gsub(/^[[:space:]]*"/, "", key)
                gsub(/":.*$/, "", key)
                
                if (key in seen) {
                    if (!(key in dups)) {
                        dups[key] = seen[key] "," NR
                    } else {
                        dups[key] = dups[key] "," NR
                    }
                } else {
                    seen[key] = NR
                }
            }
        }
    }
    END {
        for (key in dups) {
            print key ":" dups[key]
        }
    }
    ' "$file")
    
    if [ -n "$duplicates" ]; then
        echo "❌ Found duplicate top-level keys in $file_name:"
        echo "$duplicates" | while IFS=: read -r key lines; do
            echo "  - $key (lines: $lines)"
            # Show the actual lines
            echo "$lines" | tr ',' '\n' | while read line_num; do
                sed -n "${line_num}p" "$file" | sed 's/^/    /'
            done
        done
        EXIT_CODE=1
    else
        echo "✅ No duplicate top-level keys found in $file_name"
    fi
}

# Check both files
check_duplicates "$EN_FILE"
echo ""
check_duplicates "$RU_FILE"
echo ""

if [ $EXIT_CODE -eq 1 ]; then
    echo "❌ Duplicate top-level keys detected! Please fix them before committing."
    echo "   Note: JSON parsers automatically use the last value for duplicate keys."
    echo "   This can cause unexpected behavior, so duplicates should be removed."
    exit 1
else
    echo "✅ All ARB files are clean - no duplicate top-level keys found."
fi

