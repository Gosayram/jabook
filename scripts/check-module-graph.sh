#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_FILE="$ROOT_DIR/docs/architecture/module-deps.dot"
TMP_GRAPH="$(mktemp)"
trap 'rm -f "$TMP_GRAPH"' EXIT

if [[ ! -f "$GRAPH_FILE" ]]; then
  echo "❌ Module graph baseline missing: $GRAPH_FILE"
  echo "Run: ./scripts/generate-module-graph.sh"
  exit 1
fi

"$ROOT_DIR/scripts/generate-module-graph.sh" "$TMP_GRAPH" >/dev/null

if ! diff -u "$GRAPH_FILE" "$TMP_GRAPH" >/dev/null; then
  echo "❌ Module graph drift detected."
  echo "Run './scripts/generate-module-graph.sh' and commit updated docs/architecture/module-deps.dot"
  diff -u "$GRAPH_FILE" "$TMP_GRAPH" || true
  exit 1
fi

echo "✅ Module dependency graph is up to date"
