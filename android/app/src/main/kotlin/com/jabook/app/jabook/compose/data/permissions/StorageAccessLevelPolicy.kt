// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.permissions

/**
 * Policy defining storage access levels and their capabilities.
 *
 * Full Access is the primary and recommended path for JaBook because:
 * - Audiobooks are typically stored on external/USB/OTG storage
 * - Torrent downloads require full filesystem write access
 * - Library scanning needs to traverse arbitrary directories
 * - Migration between storage locations requires source + target access
 *
 * Limited mode is an explicit opt-in fallback that restricts the app
 * to app-specific storage with a clear UX explanation of lost capabilities.
 */
public enum class StorageAccessLevel {
    /**
     * Full filesystem access via MANAGE_EXTERNAL_STORAGE (Android 11+)
     * or WRITE_EXTERNAL_STORAGE (legacy).
     *
     * Capabilities:
     * - Read/write any directory on device
     * - USB/OTG external storage support
     * - Torrent download to any path
     * - Full library scanning across directories
     * - Storage migration between any locations
     */
    FULL,

    /**
     * Limited access via app-specific external storage.
     *
     * Restrictions:
     * - Only app-specific directories accessible
     * - No USB/OTG external storage support
     * - Downloads restricted to app-specific path
     * - Library scanning limited to app directory
     * - No cross-storage migration
     */
    LIMITED,
}

/**
 * Describes a capability that may be restricted in limited storage mode.
 *
 * @property label User-visible description of the capability
 * @property supportedInLimitedMode Whether this capability works in limited mode
 */
public data class StorageCapability(
    public val label: String,
    public val supportedInLimitedMode: Boolean,
)

/**
 * Policy object for storage access level decisions.
 *
 * Provides the canonical list of capabilities affected by storage mode
 * and the recommendation logic for UX presentation.
 */
public object StorageAccessLevelPolicy {
    /**
     * Ordered list of capabilities displayed to the user when explaining
     * the difference between Full and Limited access modes.
     *
     * Items are listed in priority order for UX presentation.
     */
    public val capabilities: List<StorageCapability> =
        listOf(
            StorageCapability(
                label = "Browse and play audiobooks from any folder",
                supportedInLimitedMode = false,
            ),
            StorageCapability(
                label = "USB/OTG external storage read/write",
                supportedInLimitedMode = false,
            ),
            StorageCapability(
                label = "Download torrents to any location",
                supportedInLimitedMode = false,
            ),
            StorageCapability(
                label = "Scan and import from external directories",
                supportedInLimitedMode = false,
            ),
            StorageCapability(
                label = "Transfer files between storage locations",
                supportedInLimitedMode = false,
            ),
            StorageCapability(
                label = "Play audiobooks from app storage",
                supportedInLimitedMode = true,
            ),
        )

    /**
     * Capabilities that are NOT available in limited mode.
     * Used to generate the "you will lose" warning in UX.
     */
    public val limitedModeRestrictions: List<StorageCapability>
        get() = capabilities.filter { !it.supportedInLimitedMode }

    /**
     * Determines the effective storage access level based on permission state.
     *
     * @param hasFullStoragePermission Whether MANAGE_EXTERNAL_STORAGE (or legacy write) is granted
     * @param hasStorageFallbackEnabled Whether user has explicitly opted into limited mode
     * @return The effective [StorageAccessLevel]
     */
    public fun resolveAccessLevel(
        hasFullStoragePermission: Boolean,
        hasStorageFallbackEnabled: Boolean,
    ): StorageAccessLevel =
        when {
            hasFullStoragePermission -> StorageAccessLevel.FULL
            hasStorageFallbackEnabled -> StorageAccessLevel.LIMITED
            // Default: no access resolved yet, but treat as limited for safety
            else -> StorageAccessLevel.LIMITED
        }

    /**
     * Whether Full Access mode should be presented as the primary/recommended
     * choice in the permission UX.
     *
     * Always returns true — Full Access is always the recommended path.
     */
    public fun isFullAccessRecommended(): Boolean = true

    /**
     * Whether the user should see a confirmation dialog before opting
     * into limited mode, explaining the restrictions.
     *
     * @param currentAccessLevel Current resolved access level
     * @return true if a confirmation warning should be shown
     */
    public fun shouldShowLimitedModeWarning(currentAccessLevel: StorageAccessLevel): Boolean =
        currentAccessLevel == StorageAccessLevel.LIMITED
}
