#!/bin/bash
# Check for duplicate string resource keys in Android strings.xml files

set -e

# Android strings.xml locations
DEFAULT_STRINGS="android/app/src/main/res/values/strings.xml"
RU_STRINGS="android/app/src/main/res/values-ru/strings.xml"

EXIT_CODE=0

echo "Checking for duplicate string resource keys in Android XML files..."
echo ""

# Function to find duplicates in a strings.xml file
check_duplicates() {
    local file=$1
    local file_name=$(basename "$file")
    local dir=$(dirname "$file")
    local locale=$(basename "$dir")
    
    if [ ! -f "$file" ]; then
        echo "ℹ️  $file not found (locale: $locale) - skipping"
        return
    fi
    
    echo "Checking $file_name (locale: $locale)..."
    
    # Extract string resource names from <string name="key"> tags
    local duplicates=$(awk '
    BEGIN {
        duplicates = ""
    }
    {
        # Skip comments and empty lines
        if ($0 ~ /^[[:space:]]*<!--/ || $0 ~ /^[[:space:]]*$/) {
            next
        }
        
        # Match <string name="key"> pattern
        if (match($0, /<string[^>]+name="([^"]+)"/, arr)) {
            key = arr[1]
            
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
        
        # Also check <string-array name="key"> and <plurals name="key">
        if (match($0, /<(string-array|plurals)[^>]+name="([^"]+)"/, arr)) {
            key = arr[2]
            
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
    END {
        for (key in dups) {
            print key ":" dups[key]
        }
    }
    ' "$file")
    
    if [ -n "$duplicates" ]; then
        echo "❌ Found duplicate string resource keys in $file_name (locale: $locale):"
        echo "$duplicates" | while IFS=: read -r key lines; do
            echo "  - $key (lines: $lines)"
            # Show the actual lines
            echo "$lines" | tr ',' '\n' | while read line_num; do
                sed -n "${line_num}p" "$file" | sed 's/^/    /'
            done
        done
        EXIT_CODE=1
    else
        echo "✅ No duplicate string resource keys found in $file_name (locale: $locale)"
    fi
}

# Check default strings.xml
check_duplicates "$DEFAULT_STRINGS"
echo ""

# Check Russian strings.xml
check_duplicates "$RU_STRINGS"
echo ""

# Check if there are any other values-* directories
echo "Searching for additional locale strings.xml files..."
FOUND_ADDITIONAL=false
for values_dir in android/app/src/main/res/values-*/; do
    if [ -d "$values_dir" ]; then
        strings_file="${values_dir}strings.xml"
        locale=$(basename "$values_dir")
        if [ -f "$strings_file" ] && [ "$locale" != "values-ru" ]; then
            FOUND_ADDITIONAL=true
            check_duplicates "$strings_file"
            echo ""
        fi
    fi
done

if [ "$FOUND_ADDITIONAL" = false ]; then
    echo "ℹ️  No additional locale strings.xml files found"
    echo ""
fi

if [ $EXIT_CODE -eq 1 ]; then
    echo "❌ Duplicate string resource keys detected! Please fix them before committing."
    echo "   Android will use the last value for duplicate keys, which can cause unexpected behavior."
    exit 1
else
    echo "✅ All strings.xml files are clean - no duplicate resource keys found."
fi
