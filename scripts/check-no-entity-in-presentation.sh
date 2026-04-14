#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGETS=(
  "$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/feature"
  "$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/ui"
)

violations=0

# Use ripgrep if available, fall back to grep for CI environments without rg
if command -v rg >/dev/null 2>&1; then
    search_dir() { rg -n "$1" "$2"; }
else
    search_dir() {
      grep -rnE "$1" "$2"
      local status=$?
      # grep exits 1 when no matches are found (expected).
      if [ "$status" -eq 1 ]; then
        return 1
      fi
      return "$status"
    }
fi

for target in "${TARGETS[@]}"; do
  if [ ! -d "$target" ]; then
    continue
  fi

  if search_dir "import com\\.jabook\\.app\\.jabook\\.compose\\.data\\.local\\.entity\\." "$target"; then
    echo "❌ Entity import found in presentation layer: $target"
    violations=1
  fi

  if search_dir "com\\.jabook\\.app\\.jabook\\.compose\\.data\\.local\\.entity\\." "$target"; then
    echo "❌ Fully-qualified Entity reference found in presentation layer: $target"
    violations=1
  fi
done

if [ "$violations" -ne 0 ]; then
  echo "BP-9.2 violation: presentation layer must not depend on Room entities."
  exit 1
fi

echo "✅ Presentation layer entity guard passed"
