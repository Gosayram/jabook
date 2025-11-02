#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CFG_FILE=".key-generate.conf"
[[ -f "$CFG_FILE" ]] || { echo ">> Create $CFG_FILE from .key-generate.example.conf and fill it"; exit 1; }

# --- helpers ---
trim_quotes() { local v="$1"; v="${v%\"}"; v="${v#\"}"; v="${v%\'}"; v="${v#\'}"; printf "%s" "$v"; }
get() {
  local k="$1"
  local line
  line="$(grep -E "^${k}=" "$CFG_FILE" | tail -n1 || true)"
  [[ -n "$line" ]] || return 1
  local val="${line#*=}"
  # trim leading/trailing spaces and surrounding quotes
  val="$(echo -n "$val" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  val="$(trim_quotes "$val")"
  printf "%s" "$val"
}

need() {
  local k="$1"
  local v; v="$(get "$k" || true)"
  [[ -n "${v:-}" ]] || { echo "Missing required key: $k in $CFG_FILE"; exit 1; }
  printf "%s" "$v"
}

# --- read config (required) ---
KEYSTORE_PATH="$(need KEYSTORE_PATH)"
KEY_ALIAS="$(need KEY_ALIAS)"
STORE_PASS="$(need STORE_PASS)"
KEY_PASS="$(need KEY_PASS)"
CN="$(need CN)"
OU="$(need OU)"
O="$(need O)"
L="$(need L)"
ST="$(need ST)"
C="$(need C)"

# optional with defaults
VALID_DAYS="$(get VALID_DAYS || true)"; VALID_DAYS="${VALID_DAYS:-36500}"
KEY_SIZE="$(get KEY_SIZE || true)";     KEY_SIZE="${KEY_SIZE:-4096}"

# ensure dirs
mkdir -p "$(dirname "$KEYSTORE_PATH")"

# generate keystore once
if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo ">> Keystore not found, generating..."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE_PATH" \
    -storetype PKCS12 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize "$KEY_SIZE" \
    -sigalg SHA256withRSA \
    -dname "CN=${CN}, OU=${OU}, O=${O}, L=${L}, ST=${ST}, C=${C}" \
    -validity "$VALID_DAYS"
  echo ">> Keystore created: $KEYSTORE_PATH"
else
  echo ">> Keystore exists: $KEYSTORE_PATH"
fi

# write android/key.properties (absolute path to storeFile)
ABS_STORE_FILE="$(cd "$(dirname "$KEYSTORE_PATH")" && pwd)/$(basename "$KEYSTORE_PATH")"
mkdir -p android
cat > android/key.properties <<EOF
storeFile=$ABS_STORE_FILE
storePassword=$STORE_PASS
keyPassword=$KEY_PASS
keyAlias=$KEY_ALIAS
EOF
echo ">> android/key.properties updated"

# optional: quick print of cert fingerprint (SHA-256)
if command -v keytool >/dev/null 2>&1; then
  echo ">> Certificate SHA-256 (for records):"
  keytool -list -v -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASS" 2>/dev/null | awk '/SHA256:/{print; exit}'
fi