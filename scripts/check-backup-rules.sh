#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/android/app/src/main/AndroidManifest.xml"
DATA_EXTRACTION="$ROOT_DIR/android/app/src/main/res/xml/data_extraction_rules.xml"
BACKUP_RULES="$ROOT_DIR/android/app/src/main/res/xml/backup_rules.xml"

required_manifest_patterns=(
  'android:allowBackup="true"'
  'android:dataExtractionRules="@xml/data_extraction_rules"'
  'android:fullBackupContent="@xml/backup_rules"'
)

required_xml_patterns=(
  'datastore/secure_credentials.preferences_pb'
  'datastore/cookies.preferences_pb'
  'secure_credentials_prefs.xml'
)

for pattern in "${required_manifest_patterns[@]}"; do
  if ! rg -n --fixed-strings "$pattern" "$MANIFEST" >/dev/null; then
    echo "❌ Backup manifest audit failed: missing '$pattern' in $MANIFEST"
    exit 1
  fi
done

for pattern in "${required_xml_patterns[@]}"; do
  if ! rg -n --fixed-strings "$pattern" "$DATA_EXTRACTION" >/dev/null; then
    echo "❌ Data extraction audit failed: missing '$pattern' in $DATA_EXTRACTION"
    exit 1
  fi
  if ! rg -n --fixed-strings "$pattern" "$BACKUP_RULES" >/dev/null; then
    echo "❌ Backup rules audit failed: missing '$pattern' in $BACKUP_RULES"
    exit 1
  fi
done

echo "✅ Backup/data extraction audit passed"
