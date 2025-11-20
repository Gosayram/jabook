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

# Script to update version in pubspec.yaml
#
# Usage:
#   ./update-version.sh [version] [build_number]
#
#   If version is provided, updates version in pubspec.yaml.
#   If not provided, extracts current version and increments patch version.
#   build_number is optional and defaults to incrementing current build number or 1.

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

# Extract version and build number
CURRENT_VERSION_FULL=$(echo "${CURRENT_VERSION_LINE}" | sed 's/^version:[[:space:]]*//' | tr -d '[:space:]')
CURRENT_VERSION=$(echo "${CURRENT_VERSION_FULL}" | cut -d+ -f1)
CURRENT_BUILD=$(echo "${CURRENT_VERSION_FULL}" | cut -d+ -f2)

if [ -z "${CURRENT_BUILD}" ] || [ "${CURRENT_BUILD}" = "${CURRENT_VERSION_FULL}" ]; then
    CURRENT_BUILD="1"
fi

# If version is provided as argument, use it
if [ $# -ge 1 ]; then
    NEW_VERSION="$1"
    # Validate version format (X.Y.Z)
    if ! [[ "${NEW_VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Error: Invalid version format: ${NEW_VERSION}. Expected format: X.Y.Z" >&2
        exit 1
    fi
    
    # If build number is provided, use it; otherwise increment current
    if [ $# -ge 2 ]; then
        NEW_BUILD="$2"
        if ! [[ "${NEW_BUILD}" =~ ^[0-9]+$ ]]; then
            echo "Error: Invalid build number format: ${NEW_BUILD}. Expected number." >&2
            exit 1
        fi
    else
        # Increment build number
        NEW_BUILD=$((CURRENT_BUILD + 1))
    fi
else
    # Auto-increment patch version
    MAJOR=$(echo "${CURRENT_VERSION}" | cut -d. -f1)
    MINOR=$(echo "${CURRENT_VERSION}" | cut -d. -f2)
    PATCH=$(echo "${CURRENT_VERSION}" | cut -d. -f3)
    
    # Increment patch version
    PATCH=$((PATCH + 1))
    NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
    
    # Increment build number
    NEW_BUILD=$((CURRENT_BUILD + 1))
fi

# Update pubspec.yaml
# Use sed to replace the version line
if [[ "$(uname)" == "Darwin" ]]; then
    # macOS requires empty string for backup extension
    sed -i '' "s|^version:.*|version: ${NEW_VERSION}+${NEW_BUILD}|" "${PUBSPEC_FILE}"
else
    sed -i "s|^version:.*|version: ${NEW_VERSION}+${NEW_BUILD}|" "${PUBSPEC_FILE}"
fi

echo "Updated pubspec.yaml version to ${NEW_VERSION}+${NEW_BUILD}"
echo "Previous version: ${CURRENT_VERSION}+${CURRENT_BUILD}"
echo "File: ${PUBSPEC_FILE}"
