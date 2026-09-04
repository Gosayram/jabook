#!/bin/bash
# Check that all Kotlin files have correct copyright header

set -euo pipefail

CURRENT_YEAR=$(date +%Y)
COPYRIGHT="Copyright $CURRENT_YEAR Jabook Contributors"
ERRORS=0

# Full header markers (all must be present for a correct header)
has_full_header() {
    local f="$1"
    grep -q "Copyright.*Jabook" "$f" 2>/dev/null && \
    grep -q "$COPYRIGHT" "$f" 2>/dev/null && \
    grep -q "Licensed under the Apache License" "$f" 2>/dev/null && \
    grep -q "http://www.apache.org/licenses/LICENSE-2.0" "$f" 2>/dev/null && \
    grep -q "WITHOUT WARRANTIES" "$f" 2>/dev/null && \
    grep -q "See the License for the specific language" "$f" 2>/dev/null
}

# Find all Kotlin files and check copyright
while IFS= read -r -d '' file; do
    # Check if file has copyright
    if ! grep -q "Copyright.*Jabook" "$file" 2>/dev/null; then
        echo "❌ Missing copyright in $file"
        ERRORS=$((ERRORS + 1))
    elif ! has_full_header "$file"; then
        # Has copyright but incomplete/wrong
        if ! grep -q "$COPYRIGHT" "$file" 2>/dev/null; then
            echo "⚠️  Wrong copyright year in $file"
        else
            echo "⚠️  Incomplete copyright header in $file"
        fi
        ERRORS=$((ERRORS + 1))
    else
        # Full header present and year correct — ok
        :
    fi
done < <(find . -name "*.kt" \
    -not -path "./.git/*" \
    -not -path "./hack/*" \
    -not -path "./build/*" \
    -not -path "./test_results/*" \
    -print0)

if [ $ERRORS -eq 0 ]; then
    echo "✅ All files have correct copyright"
    exit 0
else
    echo ""
    echo "❌ Found $ERRORS files with missing or incorrect copyright"
    exit 1
fi
