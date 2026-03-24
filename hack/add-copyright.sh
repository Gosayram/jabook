#!/bin/bash
# Add or update copyright header in Kotlin files

set -euo pipefail

CURRENT_YEAR=$(date +%Y)
COPYRIGHT="Copyright $CURRENT_YEAR Jabook Contributors"

# Kotlin file copyright header (using // comments)
KOTLIN_HEADER="// Copyright $CURRENT_YEAR Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the \"License\");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an \"AS IS\" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License."

# Find all Kotlin files
find . -name "*.kt" \
    -not -path "./.git/*" \
    -not -path "./hack/*" \
    -not -path "./build/*" \
    -not -path "./test_results/*" | while read -r file; do
    # Check if file already has correct copyright
    if grep -q "$COPYRIGHT" "$file" 2>/dev/null; then
        echo "✓ $file (already has correct copyright)"
        continue
    fi
    
    # Check if file has package/import declaration or is a valid Kotlin file
    # Kotlin files typically start with package, import, or class/fun definitions
    if ! grep -qE "^(package|import|class|interface|object|fun|val|var|data|sealed|enum)" "$file" 2>/dev/null; then
        echo "⊘ Skipping $file (does not appear to be a valid Kotlin file)"
        continue
    fi
    
    # Add copyright at the beginning of the file
    tmpfile=$(mktemp)
    echo "$KOTLIN_HEADER" > "$tmpfile"
    echo "" >> "$tmpfile"
    cat "$file" >> "$tmpfile"
    mv "$tmpfile" "$file"
    echo "✓ Added copyright to $file"
done

echo ""
echo "Copyright headers check completed"
