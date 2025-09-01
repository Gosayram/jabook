#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_FILE="android/app/build.gradle.kts"
TEMP_FILE="$GRADLE_FILE.tmp"

# Check if gradle file exists
if [[ ! -f "$GRADLE_FILE" ]]; then
    echo "Error: Gradle file $GRADLE_FILE not found"
    exit 1
fi

# Check if signing config creation already exists
if grep -q "signingConfigs.*{" "$GRADLE_FILE"; then
    echo "Signing config creation already exists in $GRADLE_FILE"
    exit 0
fi

# Create backup
cp "$GRADLE_FILE" "$GRADLE_FILE.backup"

# Find the buildTypes section and add signing config before it
awk '
/buildTypes/ {
    # Insert signing config before buildTypes
    print "    signingConfigs {"
    print "        create(\"release\") {"
    print "            val keystorePropertiesFile = rootProject.file(\"key.properties\")"
    print "            if (keystorePropertiesFile.exists()) {"
    print "                val keystoreProperties = java.util.Properties()"
    print "                keystoreProperties.load(keystorePropertiesFile.inputStream())"
    print "                "
    print "                storeFile = file(keystoreProperties.getProperty(\"storeFile\"))"
    print "                storePassword = keystoreProperties.getProperty(\"storePassword\")"
    print "                keyAlias = keystoreProperties.getProperty(\"keyAlias\")"
    print "                keyPassword = keystoreProperties.getProperty(\"keyPassword\")"
    print "            }"
    print "        }"
    print "    }"
    print ""
    print $0
    next
}
/release.*signingConfig.*debug/ {
    # Replace debug signing with release signing
    gsub(/signingConfig.*debug/, "signingConfig = signingConfigs.getByName(\"release\")")
    print $0
    next
}
/signingConfigs\.getByName.*debug/ {
    # Replace debug signing with release signing
    gsub(/signingConfigs\.getByName.*debug/, "signingConfigs.getByName(\"release\")")
    print $0
    next
}
/signingConfig.*debug/ {
    # Replace debug signing with release signing
    gsub(/signingConfig.*debug/, "signingConfig = signingConfigs.getByName(\"release\")")
    print $0
    next
}
/signingConfig.*release.*"\)"$/ {
    # Remove extra quote if present
    gsub(/\"\)\"$/, "\")")
    print $0
    next
}
{ print }
' "$GRADLE_FILE" > "$TEMP_FILE"

mv "$TEMP_FILE" "$GRADLE_FILE"

echo "Successfully patched $GRADLE_FILE with signing configuration"