#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/android/app/src/main/AndroidManifest.xml"

if [[ ! -f "$MANIFEST" ]]; then
  echo "❌ Receiver export guard failed: manifest not found at $MANIFEST"
  exit 1
fi

awk '
  function check_receiver(block,    name, has_permission, allow_media_button_only, found_action, n, i, line) {
    if (block !~ /android:exported="true"/) {
      return
    }

    name = "unknown"
    line = block
    sub(/^.*android:name="/, "", line)
    sub(/".*$/, "", line)
    if (line != block) {
      name = line
    }

    has_permission = (block ~ /android:permission="/)
    if (has_permission) {
      return
    }

    allow_media_button_only = 1
    found_action = 0
    n = split(block, lines, "\n")
    for (i = 1; i <= n; i++) {
      line = lines[i]
      if (line ~ /<action android:name="/) {
        found_action = 1
        sub(/^.*<action android:name="/, "", line)
        sub(/".*$/, "", line)
        if (line != "android.intent.action.MEDIA_BUTTON") {
          allow_media_button_only = 0
          break
        }
      }
    }

    if (!found_action || !allow_media_button_only) {
      printf("❌ Exported receiver '%s' is not protected by permission and is not MEDIA_BUTTON-only.\n", name)
      fail = 1
    }
  }

  /<receiver[[:space:]]/ {
    in_receiver = 1
    block = $0 "\n"
    next
  }

  in_receiver {
    block = block $0 "\n"
    if ($0 ~ /<\/receiver>/) {
      check_receiver(block)
      in_receiver = 0
      block = ""
    }
  }

  END {
    if (fail) {
      exit 1
    }
    print "✅ Receiver export guard passed"
  }
' "$MANIFEST"
