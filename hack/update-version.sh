#!/bin/bash
# Copyright 2025 Jabook Contributors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Script to update version in pubspec.yaml (SemVer style, patch 0-9)
#
# Usage:
#   ./update-version.sh [version]
#
#   If version is provided, updates version in pubspec.yaml to that version.
#   If not provided, extracts current version and increments patch version (0-9).
#   Version format: X.Y.Z (no build number, SemVer style)
#   Examples:
#     1.2.0 -> 1.2.1 -> ... -> 1.2.9 -> 1.3.0
#     1.9.9 -> 2.0.0

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PUBSPEC_FILE="${REPO_ROOT}/pubspec.yaml"

if [ ! -f "${PUBSPEC_FILE}" ]; then
    echo "Error: pubspec.yaml not found at ${PUBSPEC_FILE}" >&2
    exit 1
fi

# Extract current version from pubspec.yaml
# Format: version: 1.2.3+4
CURRENT_VERSION_LINE=$(grep -E "^version:" "${PUBSPEC_FILE}" || echo "")
if [ -z "${CURRENT_VERSION_LINE}" ]; then
    echo "Error: Could not find version line in ${PUBSPEC_FILE}" >&2
    exit 1
fi

# Extract version (ignore build number if present)
CURRENT_VERSION_FULL=$(echo "${CURRENT_VERSION_LINE}" | sed 's/^version:[[:space:]]*//' | tr -d '[:space:]')
CURRENT_VERSION=$(echo "${CURRENT_VERSION_FULL}" | cut -d+ -f1)

# If version is provided as argument, use it
if [ $# -ge 1 ]; then
    NEW_VERSION="$1"
    # Validate version format (X.Y.Z)
    if ! [[ "${NEW_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Error: Invalid version format: ${NEW_VERSION}. Expected format: X.Y.Z" >&2
        exit 1
    fi
else
    # Auto-increment version with rollover logic (SemVer style, patch 0-9)
    # Format: major.minor.patch, patch goes from 0 to 9
    # 1.2.0 -> 1.2.1 -> ... -> 1.2.9 -> 1.3.0
    # 1.9.9 -> 2.0.0
    MAJOR=$(echo "${CURRENT_VERSION}" | cut -d. -f1)
    MINOR=$(echo "${CURRENT_VERSION}" | cut -d. -f2)
    PATCH=$(echo "${CURRENT_VERSION}" | cut -d. -f3)
    
    # Validate patch is a number
    if ! [[ "${PATCH}" =~ ^[0-9]+$ ]]; then
        echo "Error: Invalid patch version: ${PATCH}" >&2
        exit 1
    fi
    
    # Increment with rollover logic (patch 0-9)
    if [ "${PATCH}" -lt 9 ]; then
        # Increment patch version (0-8 -> 1-9)
        PATCH=$((PATCH + 1))
    elif [ "${MINOR}" -lt 9 ]; then
        # Patch reached 9, increment minor, reset patch to 0
        MINOR=$((MINOR + 1))
        PATCH=0
    else
        # Minor reached 9, increment major, reset minor and patch to 0
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
    fi
    
    NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
fi

# Update pubspec.yaml (without build number, SemVer style)
# Use sed to replace the version line
if [[ "$(uname)" == "Darwin" ]]; then
    # macOS requires empty string for backup extension
    sed -i '' "s|^version:.*|version: ${NEW_VERSION}|" "${PUBSPEC_FILE}"
else
    sed -i "s|^version:.*|version: ${NEW_VERSION}|" "${PUBSPEC_FILE}"
fi

echo "Updated pubspec.yaml version to ${NEW_VERSION}"
echo "Previous version: ${CURRENT_VERSION}"
echo "File: ${PUBSPEC_FILE}"
