#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGETS=(
  "$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/feature"
  "$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/ui"
)

violations=0

for target in "${TARGETS[@]}"; do
  if [ ! -d "$target" ]; then
    continue
  fi

  if rg -n "import com\\.jabook\\.app\\.jabook\\.compose\\.data\\.local\\.entity\\." "$target"; then
    echo "❌ Entity import found in presentation layer: $target"
    violations=1
  fi

  if rg -n "com\\.jabook\\.app\\.jabook\\.compose\\.data\\.local\\.entity\\." "$target"; then
    echo "❌ Fully-qualified Entity reference found in presentation layer: $target"
    violations=1
  fi
done

if [ "$violations" -ne 0 ]; then
  echo "BP-9.2 violation: presentation layer must not depend on Room entities."
  exit 1
fi

echo "✅ Presentation layer entity guard passed"
