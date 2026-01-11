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

package com.jabook.app.jabook.audio

import android.os.Bundle
import androidx.media3.session.MediaConstants

/**
 * Helper for creating comprehensive Media3 metadata extras.
 *
 * Provides extensions for:
 * - Download status indicators (offline availability)
 * - Content presentation styles (list vs grid in Android Auto)
 * - Content grouping (book series)
 * - Explicit content flags (age ratings)
 * - Media art size hints (performance optimization)
 *
 * All features are optional and gracefully degrade if not supported.
 */
public object MediaMetadataExtrasHelper {
    /**
     * Add download status to metadata extras.
     *
     * Shows visual indicators in Android Auto and media browsers:
     * - ☁️ Not downloaded (stream only)
     * - ⬇️ Currently downloading
     * - ✅ Downloaded (offline available)
     *
     * @param bundle Existing Bundle to add extras to
     * @param isDownloaded True if file is available offline
     * @param isDownloading True if currently downloading
     */
    public fun Bundle.addDownloadStatus(
        isDownloaded: Boolean,
        isDownloading: Boolean = false,
    ) {
        val status =
            when {
                isDownloaded -> MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
                isDownloading -> MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADING
                else -> MediaConstants.EXTRAS_VALUE_STATUS_NOT_DOWNLOADED
            }
        putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, status)
    }

    /**
     * Add content presentation style hints for browsable items (folders).
     *
     * Tells Android Auto how to display folders:
     * - LIST: Detailed list view (for text-heavy content)
     * - GRID: Grid view with icons
     * - CATEGORY_LIST: List with smaller icons
     * - CATEGORY_GRID: Grid with smaller icons
     *
     * @param bundle Existing Bundle to add extras to
     * @param style One of MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_*
     */
    public fun Bundle.addBrowsableStyle(style: Int) {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
    }

    /**
     * Add content presentation style hints for playable items (books/chapters).
     *
     * @param bundle Existing Bundle to add extras to
     * @param style One of MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_*
     */
    public fun Bundle.addPlayableStyle(style: Int) {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, style)
    }

    /**
     * Add content group title for visual grouping.
     *
     * Items with the same group title are displayed together in Android Auto.
     * Perfect for book series like "Harry Potter", "Lord of the Rings", etc.
     *
     * @param bundle Existing Bundle to add extras to
     * @param groupTitle Human-readable group name (e.g., series name)
     */
    public fun Bundle.addContentGroup(groupTitle: String) {
        if (groupTitle.isNotBlank()) {
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle)
        }
    }

    /**
     * Mark content as explicit (parental advisory).
     *
     * Shows warning indicator in Android Auto and media browsers.
     *
     * @param bundle Existing Bundle to add extras to
     * @param isExplicit True if content has mature themes/language
     */
    public fun Bundle.addExplicitFlag(isExplicit: Boolean) {
        if (isExplicit) {
            putLong(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, MediaConstants.EXTRAS_VALUE_ATTRIBUTE_PRESENT)
        }
    }

    /**
     * Get recommended media art size from LibraryParams.
     *
     * Android Auto provides optimal image size to avoid bandwidth/memory waste.
     * Use this to load appropriately sized cover art.
     *
     * @param params LibraryParams from callback
     * @param defaultSize Fallback size if not specified (default: 512px)
     * @return Recommended size in pixels
     */
    public fun getRecommendedArtSize(
        params: androidx.media3.session.MediaLibraryService.LibraryParams?,
        defaultSize: Int = 512,
    ): Int = params?.extras?.getInt(MediaConstants.EXTRAS_KEY_MEDIA_ART_SIZE_PIXELS, defaultSize) ?: defaultSize

    /**
     * Create root extras with default Android Auto presentation hints.
     *
     * Configures library root to show:
     * - Playable items (books) in GRID (cover-focused)
     * - Browsable items (folders) in LIST (text-focused)
     *
     * @return Bundle with content style preferences
     */
    public fun createRootExtras(): Bundle =
        Bundle().apply {
            // Books/audiobooks: Grid view (cover art focus)
            addPlayableStyle(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            // Folders/categories: List view (text focus)
            addBrowsableStyle(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
}
