#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS_FILE="$ROOT_DIR/android/gradle/libs.versions.toml"
SETTINGS_FILE="$ROOT_DIR/android/settings.gradle.kts"
VERIFY_FILE="$ROOT_DIR/android/gradle/verification-metadata.xml"

if [[ ! -f "$LIBS_FILE" || ! -f "$SETTINGS_FILE" ]]; then
  echo "❌ Version lock check failed: required files are missing"
  exit 1
fi

get_lib_version() {
  local key="$1"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\".*/\\1/p" "$LIBS_FILE" | head -n1
}

get_plugin_version() {
  local plugin_id="$1"
  sed -nE "s/.*id\\(\"${plugin_id}\"\\)[[:space:]]+version[[:space:]]+\"([^\"]+)\".*/\\1/p" "$SETTINGS_FILE" | head -n1
}

expected_agp="$(get_lib_version "androidGradlePlugin")"
expected_ksp="$(get_lib_version "ksp")"
expected_kotlin="$(get_lib_version "kotlin")"
expected_hilt="$(get_lib_version "hilt")"
expected_ktlint="$(get_lib_version "ktlint")"

actual_agp="$(get_plugin_version "com.android.application")"
actual_ksp="$(get_plugin_version "com.google.devtools.ksp")"
actual_kotlin_serialization="$(get_plugin_version "org.jetbrains.kotlin.plugin.serialization")"
actual_kotlin_compose="$(get_plugin_version "org.jetbrains.kotlin.plugin.compose")"
actual_hilt="$(get_plugin_version "com.google.dagger.hilt.android")"
actual_ktlint="$(get_plugin_version "org.jlleitschuh.gradle.ktlint")"

fail=0

check_match() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  if [[ -z "$expected" || -z "$actual" ]]; then
    echo "❌ Version lock check: missing value for $name (expected='$expected' actual='$actual')"
    fail=1
    return
  fi
  if [[ "$expected" != "$actual" ]]; then
    echo "❌ Version lock mismatch for $name: expected '$expected', actual '$actual'"
    fail=1
  fi
}

check_match "AGP" "$expected_agp" "$actual_agp"
check_match "KSP" "$expected_ksp" "$actual_ksp"
check_match "Kotlin serialization plugin" "$expected_kotlin" "$actual_kotlin_serialization"
check_match "Kotlin compose plugin" "$expected_kotlin" "$actual_kotlin_compose"
check_match "Hilt plugin" "$expected_hilt" "$actual_hilt"
check_match "Ktlint plugin" "$expected_ktlint" "$actual_ktlint"

if [[ ! -s "$VERIFY_FILE" ]]; then
  echo "❌ Version lock check failed: verification metadata is missing or empty at $VERIFY_FILE"
  fail=1
fi

require_component() {
  local description="$1"
  local pattern="$2"
  if ! rg -q "$pattern" "$VERIFY_FILE"; then
    echo "❌ Verification metadata is missing required component: $description"
    fail=1
  fi
}

require_component "AGP Gradle plugin" 'group="com\.android\.tools\.build" name="gradle"'
require_component "Kotlin Gradle plugin" 'group="org\.jetbrains\.kotlin" name="kotlin-gradle-plugin"'
require_component "KSP Gradle plugin" 'group="com\.google\.devtools\.ksp" name="symbol-processing-gradle-plugin"'
require_component "KTagLib (JitPack artifact)" 'group="com\.github\.timusus" name="KTagLib"'

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi

echo "✅ Plugin version lock check passed (settings plugins + verification metadata)"
