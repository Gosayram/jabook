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
    val favorites: List<FavoriteBackup> = emptyList(),
    val searchHistory: List<SearchHistoryBackup> = emptyList(),
    val scanPaths: List<ScanPathBackup> = emptyList(),
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
    val font: String = "DEFAULT",
    val normalizeChapterTitles: Boolean = true,
    val limitDownloadSpeed: Boolean = false,
    val maxDownloadSpeedKb: Int = 0,
    val maxConcurrentDownloads: Int = 1,
    val rewindDurationSeconds: Int = 10,
    val forwardDurationSeconds: Int = 30,
    val languageCode: String = "system",
    val useDynamicColors: Boolean = true,
    // Notifications
    val notificationsEnabled: Boolean = true,
    val downloadNotifications: Boolean = true,
    val playerNotifications: Boolean = true,
    // Proto Settings
    val wifiOnlyDownload: Boolean,
    val downloadPath: String,
    val currentMirror: String,
    val customMirrors: List<String> = emptyList(),
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
    val totalProgress: Float = 0f,
    val isCompleted: Boolean = false,
    val downloadStatus: String = "NOT_DOWNLOADED",
    val addedDate: Long = 0,
    val rewindDuration: Int? = null,
    val forwardDuration: Int? = null,
    // Activity timestamps for sorting by activity
    val lastPlayedTimestamp: Long = 0, // When last played
    val completedTimestamp: Long = 0, // When completed (if completed)
)

/**
 * Scan path backup.
 */
@Serializable
data class ScanPathBackup(
    val path: String,
    val addedDate: Long,
)

/**
 * Search history backup.
 */
@Serializable
data class SearchHistoryBackup(
    val query: String,
    val timestamp: Long,
    val resultCount: Int = 0,
)

/**
 * Favorite book backup.
 */
@Serializable
data class FavoriteBackup(
    val bookId: String,
    val title: String,
    val author: String,
    val category: String,
    val size: String,
    val magnetUrl: String,
    val coverUrl: String? = null,
    val performer: String? = null,
    val genres: String? = null,
    val addedDate: Long,
    val duration: String? = null,
    val bitrate: String? = null,
    val audioCodec: String? = null,
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
