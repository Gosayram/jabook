#!/bin/bash
# Script to increment build number in .release-version
# Format: version+build (e.g., 1.2.7+8 -> 1.2.7+9)

set -e

RELEASE_VERSION_FILE=".release-version"

if [ ! -f "$RELEASE_VERSION_FILE" ]; then
    echo "Error: $RELEASE_VERSION_FILE not found"
    exit 1
fi

# Read current version
CURRENT_VERSION=$(cat "$RELEASE_VERSION_FILE")

# Extract version and build number
VERSION=$(echo "$CURRENT_VERSION" | cut -d+ -f1)
BUILD=$(echo "$CURRENT_VERSION" | cut -d+ -f2)

# Validate format
if [ -z "$VERSION" ] || [ -z "$BUILD" ]; then
    echo "Error: Invalid version format in $RELEASE_VERSION_FILE"
    echo "Expected format: x.y.z+build (e.g., 1.2.7+8)"
    exit 1
fi

# Increment build number
NEW_BUILD=$((BUILD + 1))
NEW_VERSION="${VERSION}+${NEW_BUILD}"

# Update version file
echo "$NEW_VERSION" > "$RELEASE_VERSION_FILE"

echo "✅ Build number incremented: $CURRENT_VERSION -> $NEW_VERSION"
