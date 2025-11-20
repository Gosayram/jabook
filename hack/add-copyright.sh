#!/bin/bash
# Add or update copyright header in Dart files

set -euo pipefail

CURRENT_YEAR=$(date +%Y)
COPYRIGHT="Copyright $CURRENT_YEAR Jabook Contributors"

# Dart file copyright header (using // comments)
DART_HEADER="// Copyright $CURRENT_YEAR Jabook Contributors
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

# Find all Dart files
find . -name "*.dart" \
    -not -path "./.dart_tool/*" \
    -not -path "./.git/*" \
    -not -path "./hack/*" \
    -not -path "./build/*" \
    -not -path "./.flutter-plugins*" \
    -not -path "./.packages" | while read -r file; do
    # Check if file already has correct copyright
    if grep -q "$COPYRIGHT" "$file" 2>/dev/null; then
        echo "✓ $file (already has correct copyright)"
        continue
    fi
    
    # Check if file has library/import/part declaration or is a valid Dart file
    # Dart files typically start with library, import, part, or class/function definitions
    if ! grep -qE "^(library|import|part|class|enum|typedef|mixin|extension|abstract|final|const|void|int|String|bool|double|List|Map|Set|Future|Stream)" "$file" 2>/dev/null; then
        echo "⊘ Skipping $file (does not appear to be a valid Dart file)"
        continue
    fi
    
    # Add copyright at the beginning of the file
    tmpfile=$(mktemp)
    echo "$DART_HEADER" > "$tmpfile"
    echo "" >> "$tmpfile"
    cat "$file" >> "$tmpfile"
    mv "$tmpfile" "$file"
    echo "✓ Added copyright to $file"
done

echo ""
echo "Copyright headers check completed"
