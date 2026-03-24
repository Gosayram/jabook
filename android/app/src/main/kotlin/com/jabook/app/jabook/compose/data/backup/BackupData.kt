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

package com.jabook.app.jabook.compose.data.backup

import kotlinx.serialization.Serializable

/**
 * Data model for app backup/restore.
 * Contains all exportable data: settings, books, favorites, history.
 */
@Serializable
public data class BackupData(
    val version: String, // App version (e.g. "1.0.0")
    val schemaVersion: String = "2.0.0", // Backup format version
    val timestamp: String,
    // NEW: App and device info
    val appInfo: AppInfo? = null,
    // NEW: Backup statistics
    val statistics: BackupStatistics? = null,
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
public data class AppSettings(
    // User Preferences
    val theme: String,
    val sortOrder: String = "BY_ACTIVITY",
    val viewMode: String = "LIST_COMPACT",
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
public data class BookBackup(
    val id: String,
    val title: String,
    val author: String,
    val lastPosition: Int = 0,
    val duration: Int = 0,
    val coverPath: String? = null,
    val totalProgress: Float = 0f,
    val isCompleted: Boolean = false,
    val downloadStatus: String = "NOT_DOWNLOADED",
    val addedDate: Int = 0,
    val rewindDuration: Int? = null,
    val forwardDuration: Int? = null,
    // Activity timestamps for sorting by activity
    val lastPlayedTimestamp: Int = 0, // When last played
    val completedTimestamp: Int = 0, // When completed (if completed)
    // NEW Phase 9B: Torrent metadata for re-download capability
    val torrentPath: String? = null, // Path to .torrent file
    val sourceUrl: String? = null, // RuTracker topic URL
    val magnetUrl: String? = null, // Magnet link
    val topicId: String? = null, // RuTracker topic ID
)

/**
 * Scan path backup.
 */
@Serializable
public data class ScanPathBackup(
    val path: String,
    val addedDate: Long,
)

/**
 * Search history backup.
 */
@Serializable
public data class SearchHistoryBackup(
    val query: String,
    val timestamp: Long,
    val resultCount: Int = 0,
)

/**
 * Favorite book backup.
 */
@Serializable
public data class FavoriteBackup(
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
public data class ImportStats(
    var settingsRestored: Boolean = false,
    var booksRestored: Int = 0,
    var favoritesRestored: Int = 0,
    var historyRestored: Int = 0,
) {
    override fun toString(): String =
        "Settings: ${if (settingsRestored) "✓" else "✗"}, Books: $booksRestored, Favorites: $favoritesRestored, History: $historyRestored"
}

/**
 * App version and build information.
 * Added in v2.0.0 for better backup tracking.
 */
@Serializable
public data class AppInfo(
    val versionName: String, // e.g. "1.0.0"
    val versionCode: Int, // Build number
    val flavor: String = "prod", // prod, dev, beta
    val platform: String = "Android",
    val androidVersion: Int, // SDK version (e.g. 34)
    val deviceModel: String? = null, // e.g. "Pixel 7"
    val deviceManufacturer: String? = null, // e.g. "Google"
)

/**
 * Backup statistics and metadata.
 * Helps understand backup size and contents.
 */
@Serializable
public data class BackupStatistics(
    val totalBooks: Int = 0,
    val downloadedBooks: Int = 0,
    val favoritesCount: Int = 0,
    val historyCount: Int = 0,
    val scanPathsCount: Int = 0,
    val totalDuration: Int = 0, // Total duration of all books in ms
    val backupSizeBytes: Int = 0, // Estimated backup size
)
