#!/usr/bin/env bash

set -euo pipefail

BASE_FILE="android/app/src/main/res/values/strings.xml"
RU_FILE="android/app/src/main/res/values-ru/strings.xml"

if [[ ! -f "${BASE_FILE}" ]]; then
  echo "ERROR: Missing base strings file: ${BASE_FILE}"
  exit 1
fi

if [[ ! -f "${RU_FILE}" ]]; then
  echo "ERROR: Missing RU strings file: ${RU_FILE}"
  exit 1
fi

extract_keys() {
  local input_file="$1"
  awk '
    /<string[[:space:]][^>]*name="/ {
      if ($0 ~ /translatable="false"/) next
      if (match($0, /name="[^"]+"/)) {
        key = substr($0, RSTART + 6, RLENGTH - 7)
        print "string:" key
      }
    }
    /<plurals[[:space:]][^>]*name="/ {
      if ($0 ~ /translatable="false"/) next
      if (match($0, /name="[^"]+"/)) {
        key = substr($0, RSTART + 6, RLENGTH - 7)
        print "plurals:" key
      }
    }
    /<string-array[[:space:]][^>]*name="/ {
      if ($0 ~ /translatable="false"/) next
      if (match($0, /name="[^"]+"/)) {
        key = substr($0, RSTART + 6, RLENGTH - 7)
        print "string-array:" key
      }
    }
  ' "${input_file}" | sort -u
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

base_keys_file="${tmp_dir}/base_keys.txt"
ru_keys_file="${tmp_dir}/ru_keys.txt"

extract_keys "${BASE_FILE}" > "${base_keys_file}"
extract_keys "${RU_FILE}" > "${ru_keys_file}"

missing_keys="$(comm -23 "${base_keys_file}" "${ru_keys_file}")"

if [[ -n "${missing_keys}" ]]; then
  echo "❌ Missing RU localization keys detected:"
  echo "${missing_keys}" | sed 's/^/  - /'
  echo
  echo "Please add the missing keys to ${RU_FILE}"
  exit 1
fi

echo "✅ i18n key check passed (values -> values-ru)"
