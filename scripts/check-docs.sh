#!/usr/bin/env bash
# Keeps README.md and .kotlin-version in sync with the authoritative
# version sources in code:
#   - Room DB schema version  <- JabookDatabase.kt   (version = N,)
#   - Kotlin version          <- libs.versions.toml  (kotlin = "x.y.z")
# Fails (exit 1) on any mismatch so a code bump that forgets the docs is caught.
# Run via `make check-docs`.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_FILE="$ROOT_DIR/android/app/src/main/kotlin/com/jabook/app/jabook/compose/data/local/JabookDatabase.kt"
TOML_FILE="$ROOT_DIR/android/gradle/libs.versions.toml"
KT_VERSION_FILE="$ROOT_DIR/.kotlin-version"
README="$ROOT_DIR/README.md"

STATUS=0
fail() { echo "❌ $*"; STATUS=1; }

# --- Room DB schema version ---
DB_VER="$(grep -Eo 'version = [0-9]+,' "$DB_FILE" | grep -Eo '[0-9]+' | head -1 || true)"
if [[ -z "$DB_VER" ]]; then
    fail "Could not extract DB schema version from $DB_FILE"
else
    if ! grep -q "v$DB_VER" "$README"; then
        fail "README.md does not reference DB schema v$DB_VER (JabookDatabase.kt: version = $DB_VER)"
    fi
fi

# --- Kotlin version ---
KT_VER="$(grep -Eo '^kotlin = "[^"]+"' "$TOML_FILE" | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)"
if [[ -z "$KT_VER" ]]; then
    fail "Could not extract Kotlin version from $TOML_FILE"
else
    if ! grep -q "$KT_VER" "$README"; then
        fail "README.md does not reference Kotlin $KT_VER (libs.versions.toml: kotlin = \"$KT_VER\")"
    fi
    if [[ -f "$KT_VERSION_FILE" ]]; then
        FILE_VER="$(tr -d '[:space:]' < "$KT_VERSION_FILE")"
        if [[ "$FILE_VER" != "$KT_VER" ]]; then
            fail ".kotlin-version ($FILE_VER) != libs.versions.toml kotlin ($KT_VER)"
        fi
    fi
fi

if [[ "$STATUS" -eq 0 ]]; then
    echo "✅ Docs version sync passed (DB v$DB_VER, Kotlin $KT_VER)"
else
    echo "Update the version references above to match the code."
    echo "Sources of truth: JabookDatabase.kt, libs.versions.toml."
fi
exit "$STATUS"
