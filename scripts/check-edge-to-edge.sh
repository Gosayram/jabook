#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/android/app/src/main/AndroidManifest.xml"
MAIN_ACTIVITY="$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/ComposeMainActivity.kt"

if [[ ! -f "$MANIFEST" || ! -f "$MAIN_ACTIVITY" ]]; then
  echo "❌ Edge-to-edge guard failed: required files are missing"
  exit 1
fi

# Use ripgrep if available, fall back to grep for CI environments without rg
if command -v rg >/dev/null 2>&1; then
    search_file() { rg -q "$1" "$2"; }
else
    search_file() { grep -qE "$1" "$2"; }
fi

if ! search_file 'android:enableOnBackInvokedCallback="true"' "$MANIFEST"; then
  echo "❌ Edge-to-edge guard failed: AndroidManifest.xml must keep enableOnBackInvokedCallback=true"
  exit 1
fi

if ! search_file '\benableEdgeToEdge\(\)' "$MAIN_ACTIVITY"; then
  echo "❌ Edge-to-edge guard failed: ComposeMainActivity must call enableEdgeToEdge()"
  exit 1
fi

MAIN_SRC="$ROOT_DIR/android/app/src/main"

# Use ripgrep if available, fall back to grep for CI environments without rg
if command -v rg >/dev/null 2>&1; then
    search_src() { rg -q "$1" "$2" --type kotlin; }
else
    search_src() { grep -rE "$1" --include='*.kt' "$2"; }
fi

# Legacy system-UI APIs break mandatory edge-to-edge on API 35+; fail if any creep back in.
if search_src 'systemUiVisibility' "$MAIN_SRC"; then
  echo "❌ Edge-to-edge guard failed: systemUiVisibility usage found (deprecated, breaks edge-to-edge)"
  exit 1
fi

if search_src 'statusBarColor[[:space:]]*=|navigationBarColor[[:space:]]*=' "$MAIN_SRC"; then
  echo "❌ Edge-to-edge guard failed: window statusBarColor/navigationBarColor manipulation found"
  exit 1
fi

if search_src 'setDecorFitsSystemWindows\([^)]*true' "$MAIN_SRC"; then
  echo "❌ Edge-to-edge guard failed: setDecorFitsSystemWindows(true) found"
  exit 1
fi

echo "✅ Edge-to-edge guard passed"
