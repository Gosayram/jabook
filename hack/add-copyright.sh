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

# Helper: check if file has correct complete header
has_full_header() {
    local f="$1"
    grep -q "$COPYRIGHT" "$f" 2>/dev/null && \
    grep -q "Licensed under the Apache License" "$f" 2>/dev/null && \
    grep -q "http://www.apache.org/licenses/LICENSE-2.0" "$f" 2>/dev/null && \
    grep -q "WITHOUT WARRANTIES" "$f" 2>/dev/null && \
    grep -q "See the License for the specific language" "$f" 2>/dev/null
}

# Find all Kotlin files
find . -name "*.kt" \
    -not -path "./.git/*" \
    -not -path "./hack/*" \
    -not -path "./build/*" \
    -not -path "./test_results/*" | while read -r file; do
    # Check if file already has correct complete header
    if has_full_header "$file"; then
        echo "✓ $file (already has correct copyright)"
        continue
    fi

    # Check if file has package/import declaration or is a valid Kotlin file
    # Kotlin files typically start with package, import, or class/fun definitions
    if ! grep -qE "^(package|import|class|interface|object|fun|val|var|data|sealed|enum)" "$file" 2>/dev/null; then
        echo "⊘ Skipping $file (does not appear to be a valid Kotlin file)"
        continue
    fi

    # If has broken/incomplete header, strip it first
    if grep -q "Copyright.*Jabook" "$file" 2>/dev/null; then
        # Strip existing broken header lines (handles // with any spacing)
        tmpfile=$(mktemp)
        awk '
            BEGIN { in_header=0; }
            NR==1 && /^\/\/ Copyright.*Jabook/ { in_header=1; next; }
            in_header && /Licensed under the Apache License|you may not use this file|You may obtain a copy|http:\/\/www.apache.org|Unless required|WITHOUT WARRANTIES|distributed on an|See the License/ { next; }
            in_header && /^\/\/ *$/ { next; }
            in_header && /^\/\/ Copyright/ { next; }
            in_header && /^$/ { in_header=0; next; }
            { in_header=0; print; }
        ' "$file" > "$tmpfile"
        # Use stripped content as base
        tmpfile2=$(mktemp)
        echo "$KOTLIN_HEADER" > "$tmpfile2"
        echo "" >> "$tmpfile2"
        cat "$tmpfile" >> "$tmpfile2"
        mv "$tmpfile2" "$file"
        rm -f "$tmpfile" 2>/dev/null || true
        echo "✓ Fixed copyright in $file"
    else
        # No header at all — prepend
        tmpfile=$(mktemp)
        echo "$KOTLIN_HEADER" > "$tmpfile"
        echo "" >> "$tmpfile"
        cat "$file" >> "$tmpfile"
        mv "$tmpfile" "$file"
        echo "✓ Added copyright to $file"
    fi
done

echo ""
echo "Copyright headers check completed"
