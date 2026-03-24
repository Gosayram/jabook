#!/bin/bash
# Script to find and remove duplicate string resource keys in Android XML files
# Keeps the first occurrence, removes subsequent duplicates

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_RES="$PROJECT_ROOT/android/app/src/main/res"
BACKUP_DIR="$PROJECT_ROOT/.backup/strings"

echo "🔍 Checking and cleaning duplicate string resource keys in Android XML files..."
echo ""

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

# Function to check and clean duplicates in a strings.xml file
check_and_clean_duplicates() {
    local file_path="$1"
    local locale="$2"
    
    echo "Checking strings.xml (locale: $locale)..."
    
    # Create a temporary file in the same directory for atomic replace
    local temp_file="${file_path}.tmp"
    # Create backup with timestamp and locale name in backup directory
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="$BACKUP_DIR/strings_${locale}_${timestamp}.xml"
    
    # Use Python for better XML handling
    python3 -c '
import sys
import re
from collections import OrderedDict

file_path = sys.argv[1]
temp_file = sys.argv[2]  
backup_file = sys.argv[3]
locale = sys.argv[4]

# Read the file
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# Track seen normalized keys
seen_normalized_keys = {}
duplicates_found = []
output_lines = []

for i, line in enumerate(lines, 1):
    # Match string resource definition
    match = re.search(r"<string\s+name=\"([^\"]+)\"", line)
    
    if match:
        original_key = match.group(1)
        # Normalize key by removing trailing digits
        normalized_key = re.sub(r"\d+$", "", original_key)
        
        if normalized_key in seen_normalized_keys:
            # This is a duplicate (either exact match or numbered variant)
            first_occurrence = seen_normalized_keys[normalized_key]
            duplicates_found.append((i, original_key, first_occurrence["line"], first_occurrence["key"]))
            continue  # Don'"'"'t add to output
        else:
            # First occurrence - remember it
            seen_normalized_keys[normalized_key] = {"line": i, "key": original_key}
    
    output_lines.append(line)

# Report results
if duplicates_found:
    print(f"⚠️  Found {len(duplicates_found)} duplicate(s)")
    for line_num, key, first_line, first_key in duplicates_found:
        if key == first_key:
            print(f"   Line {line_num}: '"'"'{key}'"'"' (duplicate of line {first_line}) - REMOVED")
        else:
            print(f"   Line {line_num}: '"'"'{key}'"'"' (numbered duplicate of '"'"'{first_key}'"'"' at line {first_line}) - REMOVED")
    
    # Create backup
    with open(backup_file, "w", encoding="utf-8") as f:
        f.writelines(lines)
    
    # Write cleaned file
    with open(temp_file, "w", encoding="utf-8") as f:
        f.writelines(output_lines)
    
    # Replace original with cleaned
    import shutil
    shutil.move(temp_file, file_path)
    
    print(f"✅ Cleaned file saved")
    print(f"📦 Backup: {backup_file}")
    sys.exit(1)  # Exit with 1 to indicate changes were made
else:
    print(f"✅ No duplicates found")
    sys.exit(0)
' "$file_path" "$temp_file" "$backup_file" "$locale"
    
    return $?
}

had_duplicates=false

# Check values/strings.xml (default English)
if [ -f "$ANDROID_RES/values/strings.xml" ]; then
    if ! check_and_clean_duplicates "$ANDROID_RES/values/strings.xml" "values"; then
        had_duplicates=true
    fi
    echo ""
fi

# Check values-ru/strings.xml (Russian)
if [ -f "$ANDROID_RES/values-ru/strings.xml" ]; then
    if ! check_and_clean_duplicates "$ANDROID_RES/values-ru/strings.xml" "values-ru"; then
        had_duplicates=true
    fi
    echo ""
fi

# Search for any other locale strings.xml files
echo "Searching for additional locale strings.xml files..."
additional_files=$(find "$ANDROID_RES" -path "*/values-*/strings.xml" ! -path "*/values-ru/*" 2>/dev/null || true)

if [ -n "$additional_files" ]; then
    while IFS= read -r file; do
        if [ -f "$file" ]; then
            locale=$(basename "$(dirname "$file")")
            if ! check_and_clean_duplicates "$file" "$locale"; then
                had_duplicates=true
            fi
            echo ""
        fi
    done <<< "$additional_files"
else
    echo "ℹ️  No additional locale strings.xml files found"
    echo ""
fi

if [ "$had_duplicates" = true ]; then
    echo "🧹 Cleanup complete! Duplicates have been removed."
    echo "📦 Backup files saved to: $BACKUP_DIR"
    exit 1
else
    echo "✅ All strings.xml files are clean - no duplicate resource keys found."
    exit 0
fi
