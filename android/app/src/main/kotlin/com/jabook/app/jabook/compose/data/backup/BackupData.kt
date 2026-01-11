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
    public val version: String, // App version (e.g. "1.0.0")
    public val schemaVersion: String = "2.0.0", // Backup format version
    public val timestamp: String,
    // NEW: App and device info
    public val appInfo: AppInfo? = null,
    // NEW: Backup statistics
    public val statistics: BackupStatistics? = null,
    public val settings: AppSettings,
    public val bookMetadata: List<BookBackup> = emptyList(),
    public val favorites: List<FavoriteBackup> = emptyList(),
    public val searchHistory: List<SearchHistoryBackup> = emptyList(),
    public val scanPaths: List<ScanPathBackup> = emptyList(),
)

/**
 * App settings backup.
 * Includes user preferences and proto settings.
 */
@Serializable
public data class AppSettings(
    // User Preferences
    public val theme: String,
    public val sortOrder: String = "BY_ACTIVITY",
    public val viewMode: String = "LIST_COMPACT",
    public val autoPlayNext: Boolean,
    public val playbackSpeed: Float,
    public val font: String = "DEFAULT",
    public val normalizeChapterTitles: Boolean = true,
    public val limitDownloadSpeed: Boolean = false,
    public val maxDownloadSpeedKb: Int = 0,
    public val maxConcurrentDownloads: Int = 1,
    public val rewindDurationSeconds: Int = 10,
    public val forwardDurationSeconds: Int = 30,
    public val languageCode: String = "system",
    public val useDynamicColors: Boolean = true,
    // Notifications
    public val notificationsEnabled: Boolean = true,
    public val downloadNotifications: Boolean = true,
    public val playerNotifications: Boolean = true,
    // Proto Settings
    public val wifiOnlyDownload: Boolean,
    public val downloadPath: String,
    public val currentMirror: String,
    public val customMirrors: List<String> = emptyList(),
    public val autoSwitchMirror: Boolean,
)

/**
 * Book metadata backup.
 * Includes playback progress and basic info.
 */
@Serializable
public data class BookBackup(
    public val id: String,
    public val title: String,
    public val author: String,
    public val lastPosition: Int = ,
    public val duration: Int = ,
    public val coverPath: String? = null,
    public val totalProgress: Float = 0f,
    public val isCompleted: Boolean = false,
    public val downloadStatus: String = "NOT_DOWNLOADED",
    public val addedDate: Int = ,
    public val rewindDuration: Int? = null,
    public val forwardDuration: Int? = null,
    // Activity timestamps for sorting by activity
    public val lastPlayedTimestamp: Int = , // When last played
    public val completedTimestamp: Int = , // When completed (if completed)
    // NEW Phase 9B: Torrent metadata for re-download capability
    public val torrentPath: String? = null, // Path to .torrent file
    public val sourceUrl: String? = null, // RuTracker topic URL
    public val magnetUrl: String? = null, // Magnet link
    public val topicId: String? = null, // RuTracker topic ID
)

/**
 * Scan path backup.
 */
@Serializable
public data class ScanPathBackup(
    public val path: String,
    public val addedDate: Long,
)

/**
 * Search history backup.
 */
@Serializable
public data class SearchHistoryBackup(
    public val query: String,
    public val timestamp: Long,
    public val resultCount: Int = 0,
)

/**
 * Favorite book backup.
 */
@Serializable
public data class FavoriteBackup(
    public val bookId: String,
    public val title: String,
    public val author: String,
    public val category: String,
    public val size: String,
    public val magnetUrl: String,
    public val coverUrl: String? = null,
    public val performer: String? = null,
    public val genres: String? = null,
    public val addedDate: Long,
    public val duration: String? = null,
    public val bitrate: String? = null,
    public val audioCodec: String? = null,
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
    public val versionName: String, // e.g. "1.0.0"
    public val versionCode: Int, // Build number
    public val flavor: String = "prod", // prod, dev, beta
    public val platform: String = "Android",
    public val androidVersion: Int, // SDK version (e.g. 34)
    public val deviceModel: String? = null, // e.g. "Pixel 7"
    public val deviceManufacturer: String? = null, // e.g. "Google"
)

/**
 * Backup statistics and metadata.
 * Helps understand backup size and contents.
 */
@Serializable
public data class BackupStatistics(
    public val totalBooks: Int = 0,
    public val downloadedBooks: Int = 0,
    public val favoritesCount: Int = 0,
    public val historyCount: Int = 0,
    public val scanPathsCount: Int = 0,
    public val totalDuration: Int = , // Total duration of all books in ms
    public val backupSizeBytes: Int = , // Estimated backup size
)
