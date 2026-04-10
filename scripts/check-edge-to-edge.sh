#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/android/app/src/main/AndroidManifest.xml"
MAIN_ACTIVITY="$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/ComposeMainActivity.kt"

if [[ ! -f "$MANIFEST" || ! -f "$MAIN_ACTIVITY" ]]; then
  echo "❌ Edge-to-edge guard failed: required files are missing"
  exit 1
fi

if ! rg -q 'android:enableOnBackInvokedCallback="true"' "$MANIFEST"; then
  echo "❌ Edge-to-edge guard failed: AndroidManifest.xml must keep enableOnBackInvokedCallback=true"
  exit 1
fi

if ! rg -q '\benableEdgeToEdge\(\)' "$MAIN_ACTIVITY"; then
  echo "❌ Edge-to-edge guard failed: ComposeMainActivity must call enableEdgeToEdge()"
  exit 1
fi

echo "✅ Edge-to-edge guard passed"
