#!/usr/bin/env bash
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

# Script to generate CHANGELOG.md based on Keep a Changelog format
# See: https://keepachangelog.com/en/1.1.0/
#
# Usage:
#   ./generate-changelog.sh [output_file]
#
#   If output_file is not provided, defaults to CHANGELOG.md in repo root

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_FILE="${1:-${REPO_ROOT}/CHANGELOG.md}"

# Get repository URL for links
REPO_URL=$(git -C "${REPO_ROOT}" remote get-url origin 2>/dev/null || echo "https://github.com/jabook/jabook")
# Convert SSH URL to HTTPS if needed
if [[ "${REPO_URL}" =~ ^git@ ]]; then
    # Convert git@github.com:user/repo.git to https://github.com/user/repo
    REPO_URL=$(echo "${REPO_URL}" | sed -E 's|git@([^:]+):([^/]+)/(.+)\.git|https://\1/\2/\3|')
    REPO_URL=$(echo "${REPO_URL}" | sed -E 's|git@([^:]+):(.+)\.git|https://\1/\2|')
fi
# Remove .git suffix if present
REPO_URL="${REPO_URL%.git}"

cd "${REPO_ROOT}"

# Function to check if commit should be skipped (e.g., CHANGELOG-only commits)
# Returns: 0 (true) if should skip, 1 (false) if should include
should_skip_commit() {
    local msg="$1"
    local upper_msg=$(echo "$msg" | tr '[:lower:]' '[:upper:]')
    
    # Skip commits that only update CHANGELOG
    # Patterns: "bump CHANGELOG", "update CHANGELOG", "[UPD] CHANGELOG", "[UPD] - bump CHANGELOG", etc.
    # Match patterns like: [UPD] bump CHANGELOG, [UPD] - bump CHANGELOG, bump CHANGELOG, etc.
    # Using grep for better compatibility across bash versions
    if echo "$upper_msg" | grep -qE '^\[?[A-Z]*\]?[[:space:]]*[-:]?[[:space:]]*(BUMP|UPDATE|UPD)[[:space:]]+.*CHANGELOG[[:space:]]*[;:]?[[:space:]]*$'; then
        return 0  # Skip
    fi
    
    # Skip if message contains only "CHANGELOG" with optional prefix/suffix
    # Patterns: "[UPD] CHANGELOG", "CHANGELOG", "[UPD] - CHANGELOG", etc.
    if echo "$upper_msg" | grep -qE '^\[?[A-Z]*\]?[[:space:]]*[-:]?[[:space:]]*CHANGELOG[[:space:]]*[;:]?[[:space:]]*$'; then
        return 0  # Skip
    fi
    
    return 1  # Don't skip
}

# Function to categorize commit message into changelog type
# Returns: Added, Changed, Deprecated, Removed, Fixed, Security, or empty
categorize_commit() {
    local msg="$1"
    local upper_msg=$(echo "$msg" | tr '[:lower:]' '[:upper:]')
    
    # Security fixes
    if [[ "$upper_msg" =~ (SEC|SECURITY|CVE|VULN) ]]; then
        echo "Security"
        return
    fi
    
    # Bug fixes
    if [[ "$upper_msg" =~ ^\[?(FIX|BUGFIX|BUG) ]]; then
        echo "Fixed"
        return
    fi
    
    # New features
    if [[ "$upper_msg" =~ ^\[?(FEATURE|FEAT|ADD|INIT)\[? ]]; then
        echo "Added"
        return
    fi
    
    # Performance improvements
    if [[ "$upper_msg" =~ ^\[?(PERF|PERFORMANCE) ]]; then
        echo "Changed"
        return
    fi
    
    # Refactoring
    if [[ "$upper_msg" =~ ^\[?(REFACTOR|REF) ]]; then
        echo "Changed"
        return
    fi
    
    # Updates/enhancements
    if [[ "$upper_msg" =~ ^\[?(UPD|UPDATE|ENHANCE|IMPROVE) ]]; then
        echo "Changed"
        return
    fi
    
    # Removals
    if [[ "$upper_msg" =~ ^\[?(REMOVE|DELETE|DROP) ]]; then
        echo "Removed"
        return
    fi
    
    # Deprecations
    if [[ "$upper_msg" =~ ^\[?(DEPRECATE|DEPRECATED) ]]; then
        echo "Deprecated"
        return
    fi
    
    # Documentation
    if [[ "$upper_msg" =~ ^\[?(DOCS|DOC|DOCUMENTATION) ]]; then
        echo "Changed"
        return
    fi
    
    # Tests
    if [[ "$upper_msg" =~ ^\[?(TEST|TESTS) ]]; then
        # Tests usually don't go in changelog, but if explicitly marked, include as Changed
        echo ""
        return
    fi
    
    # CI/Build/Chore
    if [[ "$upper_msg" =~ ^\[?(CI|BUILD|CHORE|LINT) ]]; then
        # Usually don't go in changelog unless significant
        echo ""
        return
    fi
    
    # Default: treat as Changed if it doesn't match anything
    echo "Changed"
}

# Function to clean commit message for changelog
clean_commit_message() {
    local msg="$1"
    # Remove common prefixes like [FEATURE], [FIX], etc. with optional dash/colon
    # Pattern: [TAG] - message -> message (most common)
    # Use basic regex for macOS compatibility
    msg=$(echo "$msg" | sed 's/^\[[A-Z]*\] *[-:] *//')
    # Pattern: [TAG] message -> message
    msg=$(echo "$msg" | sed 's/^\[[A-Z]*\] *//')
    # Remove any remaining leading dashes or colons with spaces (cleanup)
    msg=$(echo "$msg" | sed 's/^[-:] *//')
    # Fix common typos
    msg=$(echo "$msg" | sed 's/^[Uu]nitial/Initial/')
    # Remove trailing colon or semicolon
    msg=$(echo "$msg" | sed 's/[:;]$//')
    # Trim whitespace
    msg=$(echo "$msg" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    # Capitalize first letter (only if not empty)
    if [ -n "$msg" ]; then
        first_char=$(echo "$msg" | cut -c1)
        rest=$(echo "$msg" | cut -c2-)
        first_upper=$(echo "$first_char" | tr '[:lower:]' '[:upper:]')
        msg="${first_upper}${rest}"
    fi
    # Remove trailing period if present
    msg=$(echo "$msg" | sed 's/\.$//')
    echo "$msg"
}

# Function to get commits between two refs
get_commits() {
    local from_ref="${1:-}"
    local to_ref="${2:-HEAD}"
    
    if [ -z "$from_ref" ]; then
        git log --pretty=format:"%H|%ai|%s" "$to_ref"
    else
        git log --pretty=format:"%H|%ai|%s" "${from_ref}..${to_ref}"
    fi
}

# Function to format version section
# According to Keep a Changelog: every version should have an entry
format_version_section() {
    local version="$1"
    local date="$2"
    local commits="$3"
    
    # Always output version header with date (principle: entry for every version)
    echo "## [${version}] - ${date}"
    echo ""
    
    # Use temporary files for grouping (bash 3.2 compatible)
    local tmpdir=$(mktemp -d)
    local added_file="${tmpdir}/added"
    local changed_file="${tmpdir}/changed"
    local deprecated_file="${tmpdir}/deprecated"
    local removed_file="${tmpdir}/removed"
    local fixed_file="${tmpdir}/fixed"
    local security_file="${tmpdir}/security"
    
    touch "$added_file" "$changed_file" "$deprecated_file" "$removed_file" "$fixed_file" "$security_file"
    
    # Process commits and group by type
    while IFS='|' read -r hash date_str subject; do
        # Skip commits that only update CHANGELOG
        if should_skip_commit "$subject"; then
            continue
        fi
        
        category=$(categorize_commit "$subject")
        if [ -z "$category" ]; then
            continue
        fi
        
        cleaned=$(clean_commit_message "$subject")
        
        case "$category" in
            Added)
                echo "$cleaned" >> "$added_file"
                ;;
            Changed)
                echo "$cleaned" >> "$changed_file"
                ;;
            Deprecated)
                echo "$cleaned" >> "$deprecated_file"
                ;;
            Removed)
                echo "$cleaned" >> "$removed_file"
                ;;
            Fixed)
                echo "$cleaned" >> "$fixed_file"
                ;;
            Security)
                echo "$cleaned" >> "$security_file"
                ;;
        esac
    done <<< "$commits"
    
    # Output sections in order: Added, Changed, Deprecated, Removed, Fixed, Security
    # (Keep a Changelog standard order)
    if [ -s "$added_file" ]; then
        echo "### Added"
        sort -u "$added_file" | while read -r item; do
            echo "- ${item}"
        done
        echo ""
    fi
    
    if [ -s "$changed_file" ]; then
        echo "### Changed"
        sort -u "$changed_file" | while read -r item; do
            echo "- ${item}"
        done
        echo ""
    fi
    
    if [ -s "$deprecated_file" ]; then
        echo "### Deprecated"
        sort -u "$deprecated_file" | while read -r item; do
            echo "- ${item}"
        done
        echo ""
    fi
    
    if [ -s "$removed_file" ]; then
        echo "### Removed"
        sort -u "$removed_file" | while read -r item; do
            echo "- ${item}"
        done
        echo ""
    fi
    
    if [ -s "$fixed_file" ]; then
        echo "### Fixed"
        sort -u "$fixed_file" | while read -r item; do
            echo "- ${item}"
        done
        echo ""
    fi
    
    if [ -s "$security_file" ]; then
        echo "### Security"
        sort -u "$security_file" | while read -r item; do
            echo "- ${item}"
        done
        echo ""
    fi
    
    rm -rf "$tmpdir"
}

# Get all tags sorted by version (newest first)
# Tags should be in format vX.Y.Z or X.Y.Z
get_tags() {
    git tag -l | grep -E '^v?[0-9]+\.[0-9]+\.[0-9]+$' | sort -V -r || true
}

# Get date for a tag (or current date if no tag) in UTC
get_tag_date() {
    local tag="$1"
    if git rev-parse "$tag" >/dev/null 2>&1; then
        # Get commit date in UTC using --date=format with UTC timezone
        # This ensures the date is always in UTC regardless of commit's original timezone
        TZ=UTC git log -1 --date=format:'%Y-%m-%d' --format='%ad' "$tag"
    else
        # Use current date in UTC
        TZ=UTC date +%Y-%m-%d
    fi
}

# Generate changelog
{
    cat <<EOF
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Types of changes

- \`Added\` for new features.
- \`Changed\` for changes in existing functionality.
- \`Deprecated\` for soon-to-be removed features.
- \`Removed\` for now removed features.
- \`Fixed\` for any bug fixes.
- \`Security\` in case of vulnerabilities.

EOF

    # Get all tags (sorted by version, newest first)
    tags=($(get_tags))
    
    # Get commits for Unreleased section
    # Unreleased contains all commits after the last tag (or all commits if no tags exist)
    # When a new tag is created, commits from Unreleased will automatically move to that version
    if [ ${#tags[@]} -eq 0 ]; then
        # No tags exist yet - all commits go to Unreleased
        unreleased_commits=$(get_commits "" "HEAD")
        if [ -n "$unreleased_commits" ]; then
            echo "## [Unreleased]"
            echo ""
            
            # Use temporary files for grouping (bash 3.2 compatible)
            tmpdir=$(mktemp -d)
            added_file="${tmpdir}/added"
            changed_file="${tmpdir}/changed"
            deprecated_file="${tmpdir}/deprecated"
            removed_file="${tmpdir}/removed"
            fixed_file="${tmpdir}/fixed"
            security_file="${tmpdir}/security"
            
            touch "$added_file" "$changed_file" "$deprecated_file" "$removed_file" "$fixed_file" "$security_file"
            
            while IFS='|' read -r hash date_str subject; do
                # Skip commits that only update CHANGELOG
                if should_skip_commit "$subject"; then
                    continue
                fi
                
                category=$(categorize_commit "$subject")
                if [ -z "$category" ]; then
                    continue
                fi
                
                cleaned=$(clean_commit_message "$subject")
                
                case "$category" in
                    Added)
                        echo "$cleaned" >> "$added_file"
                        ;;
                    Changed)
                        echo "$cleaned" >> "$changed_file"
                        ;;
                    Deprecated)
                        echo "$cleaned" >> "$deprecated_file"
                        ;;
                    Removed)
                        echo "$cleaned" >> "$removed_file"
                        ;;
                    Fixed)
                        echo "$cleaned" >> "$fixed_file"
                        ;;
                    Security)
                        echo "$cleaned" >> "$security_file"
                        ;;
                esac
            done <<< "$unreleased_commits"
            
            # Output unreleased sections
            if [ -s "$added_file" ]; then
                echo "### Added"
                sort -u "$added_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$changed_file" ]; then
                echo "### Changed"
                sort -u "$changed_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$deprecated_file" ]; then
                echo "### Deprecated"
                sort -u "$deprecated_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$removed_file" ]; then
                echo "### Removed"
                sort -u "$removed_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$fixed_file" ]; then
                echo "### Fixed"
                sort -u "$fixed_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$security_file" ]; then
                echo "### Security"
                sort -u "$security_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            rm -rf "$tmpdir"
        fi
    else
        # Tags exist - get commits after the last (newest) tag
        # These commits will appear in [Unreleased] until a new tag is created
        last_tag="${tags[0]}"
        # Remove 'v' prefix if present for comparison
        last_tag_clean="${last_tag#v}"
        unreleased_commits=$(get_commits "$last_tag" "HEAD")
        
        if [ -n "$unreleased_commits" ]; then
            echo "## [Unreleased]"
            echo ""
            
            # Use temporary files for grouping (bash 3.2 compatible)
            tmpdir=$(mktemp -d)
            added_file="${tmpdir}/added"
            changed_file="${tmpdir}/changed"
            deprecated_file="${tmpdir}/deprecated"
            removed_file="${tmpdir}/removed"
            fixed_file="${tmpdir}/fixed"
            security_file="${tmpdir}/security"
            
            touch "$added_file" "$changed_file" "$deprecated_file" "$removed_file" "$fixed_file" "$security_file"
            
            while IFS='|' read -r hash date_str subject; do
                # Skip commits that only update CHANGELOG
                if should_skip_commit "$subject"; then
                    continue
                fi
                
                category=$(categorize_commit "$subject")
                if [ -z "$category" ]; then
                    continue
                fi
                
                cleaned=$(clean_commit_message "$subject")
                
                case "$category" in
                    Added)
                        echo "$cleaned" >> "$added_file"
                        ;;
                    Changed)
                        echo "$cleaned" >> "$changed_file"
                        ;;
                    Deprecated)
                        echo "$cleaned" >> "$deprecated_file"
                        ;;
                    Removed)
                        echo "$cleaned" >> "$removed_file"
                        ;;
                    Fixed)
                        echo "$cleaned" >> "$fixed_file"
                        ;;
                    Security)
                        echo "$cleaned" >> "$security_file"
                        ;;
                esac
            done <<< "$unreleased_commits"
            
            # Output unreleased sections
            if [ -s "$added_file" ]; then
                echo "### Added"
                sort -u "$added_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$changed_file" ]; then
                echo "### Changed"
                sort -u "$changed_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$deprecated_file" ]; then
                echo "### Deprecated"
                sort -u "$deprecated_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$removed_file" ]; then
                echo "### Removed"
                sort -u "$removed_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$fixed_file" ]; then
                echo "### Fixed"
                sort -u "$fixed_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            if [ -s "$security_file" ]; then
                echo "### Security"
                sort -u "$security_file" | while read -r item; do
                    echo "- ${item}"
                done
                echo ""
            fi
            
            rm -rf "$tmpdir"
        fi
    fi
    
    # Process each tag (latest version comes first - Keep a Changelog principle)
    for i in "${!tags[@]}"; do
        tag="${tags[$i]}"
        tag_clean="${tag#v}"  # Remove 'v' prefix if present
        
        # Get date for this tag (release date displayed - Keep a Changelog principle)
        tag_date=$(get_tag_date "$tag")
        
        # Get commits for this version
        # Every version should have an entry (Keep a Changelog principle)
        if [ $((i + 1)) -lt ${#tags[@]} ]; then
            # There's a next tag, get commits between this and next
            next_tag="${tags[$((i + 1))]}"
            commits=$(get_commits "$next_tag" "$tag")
        else
            # This is the oldest tag, get all commits up to this tag
            commits=$(get_commits "" "$tag")
        fi
        
        # Format version section (always outputs version header, even if no commits)
        format_version_section "$tag_clean" "$tag_date" "$commits"
    done
    
    # Add links section at the end
    echo ""
    
    # Unreleased link
    # According to Keep a Changelog: link should point to comparison between last release and HEAD
    if [ ${#tags[@]} -eq 0 ]; then
        # No tags yet - link from first commit to HEAD
        first_commit=$(git rev-list --max-parents=0 HEAD 2>/dev/null | head -1 || echo "")
        if [ -n "$first_commit" ]; then
            # Use short hash for readability
            first_commit_short=$(echo "$first_commit" | cut -c1-7)
            echo "[Unreleased]: ${REPO_URL}/compare/${first_commit_short}...HEAD"
        else
            echo "[Unreleased]: ${REPO_URL}/compare/HEAD...HEAD"
        fi
    else
        # Tags exist - link from last tag to HEAD
        last_tag="${tags[0]}"
        echo "[Unreleased]: ${REPO_URL}/compare/${last_tag}...HEAD"
    fi
    
    # Version links
    for i in "${!tags[@]}"; do
        tag="${tags[$i]}"
        tag_clean="${tag#v}"
        
        if [ $((i + 1)) -lt ${#tags[@]} ]; then
            next_tag="${tags[$((i + 1))]}"
            echo "[${tag_clean}]: ${REPO_URL}/compare/${next_tag}...${tag}"
        else
            # Oldest tag - link to first commit or releases page
            first_commit=$(git rev-list --max-parents=0 HEAD 2>/dev/null | head -1 || echo "")
            if [ -n "$first_commit" ]; then
                echo "[${tag_clean}]: ${REPO_URL}/compare/${first_commit}...${tag}"
            else
                echo "[${tag_clean}]: ${REPO_URL}/releases/tag/${tag}"
            fi
        fi
    done
    
} > "${OUTPUT_FILE}"

echo "Generated CHANGELOG.md at ${OUTPUT_FILE}"
