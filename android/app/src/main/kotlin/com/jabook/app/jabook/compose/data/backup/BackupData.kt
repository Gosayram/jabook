// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.data.backup

import kotlinx.serialization.Serializable

/**
 * Data model for app backup/restore.
 * Contains all exportable data: settings, books, favorites, history.
 */
@Serializable
data class BackupData(
    val version: String = "1.0.0",
    val timestamp: String,
    val settings: AppSettings,
    val bookMetadata: List<BookBackup> = emptyList(),
    val favorites: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
)

/**
 * App settings backup.
 * Includes user preferences and proto settings.
 */
@Serializable
data class AppSettings(
    // User Preferences
    val theme: String,
    val autoPlayNext: Boolean,
    val playbackSpeed: Float,
    // Proto Settings
    val wifiOnlyDownload: Boolean,
    val downloadPath: String,
    val currentMirror: String,
    val autoSwitchMirror: Boolean,
)

/**
 * Book metadata backup.
 * Includes playback progress and basic info.
 */
@Serializable
data class BookBackup(
    val id: String,
    val title: String,
    val author: String,
    val lastPosition: Long = 0,
    val duration: Long = 0,
    val coverPath: String? = null,
)

/**
 * Statistics from import operation.
 */
data class ImportStats(
    var settingsRestored: Boolean = false,
    var booksRestored: Int = 0,
    var favoritesRestored: Int = 0,
    var historyRestored: Int = 0,
) {
    override fun toString(): String =
        "Settings: ${if (settingsRestored) "✓" else "✗"}, Books: $booksRestored, Favorites: $favoritesRestored, History: $historyRestored"
}
