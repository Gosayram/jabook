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

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.util.DateTimeFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for backing up and restoring app data.
 * Exports/imports settings, book metadata, favorites, and search history.
 */
@Singleton
public class BackupService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val database: JabookDatabase,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val protoSettingsRepository: ProtoSettingsRepository,
        private val playerPersistenceManager: com.jabook.app.jabook.audio.PlayerPersistenceManager,
        private val mirrorManager: com.jabook.app.jabook.compose.data.network.MirrorManager,
    ) {
        public companion object {
            private const val TAG = "BackupService"
            private const val CURRENT_VERSION = "1.0.0"
        }

        private val json =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }

        /**
         * Exports all app data to a JSON file.
         * Returns FileProvider URI for sharing.
         */
        suspend fun exportToFile(): Uri =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting data export")

                    // 1. Collect data
                    val backupData = collectData()

                    // 2. Serialize to JSON
                    val jsonString = json.encodeToString(backupData)
                    Log.d(TAG, "Serialized backup: ${jsonString.length} bytes")

                    // 3. Write to file
                    public val timestamp = DateTimeFormatter.formatCurrentForFilename()
                    public val fileName: String = "jabook_backup_$timestamp.json"
                    public val file = File(context.cacheDir, fileName)
                    file.writeText(jsonString)

                    Log.d(TAG, "Backup written to ${file.absolutePath}")

                    // 4. Return FileProvider URI
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Export failed", e)
                    throw e
                }
            }

        /**
         * Imports app data from a JSON backup file.
         * Returns statistics about imported items.
         */
        suspend fun importFromFile(uri: Uri): ImportStats =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting data import from $uri")

                    // 1. Read file
                    val jsonString =
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        } ?: throw IOException("Cannot read backup file")

                    Log.d(TAG, "Read backup file: ${jsonString.length} bytes")

                    // 2. Parse JSON
                    val backupData = json.decodeFromString<BackupData>(jsonString)
                    Log.d(TAG, "Parsed backup version ${backupData.version}")

                    // 3. Validate schema version (support both 1.x and 2.x)
                    val schemaVersion = backupData.schemaVersion ?: backupData.version // Fallback for v1.0.0
                    if (!isCompatibleVersion(schemaVersion)) {
                        throw IllegalArgumentException(
                            "Incompatible backup schema version: $schemaVersion. " +
                                "Expected version 1.x or 2.x",
                        )
                    }

                    // 4. Import data
                    val stats = ImportStats()

                    // Import settings
                    restoreSettings(backupData.settings)
                    stats.settingsRestored = true
                    Log.d(TAG, "Settings restored")

                    // Import book metadata
                    restoreBooks(backupData.bookMetadata)
                    stats.booksRestored = backupData.bookMetadata.size

                    // Import favorites
                    restoreFavorites(backupData.favorites)
                    stats.favoritesRestored = backupData.favorites.size

                    // Import search history
                    restoreSearchHistory(backupData.searchHistory)
                    stats.historyRestored = backupData.searchHistory.size

                    // Import scan paths
                    restoreScanPaths(backupData.scanPaths)

                    Log.d(TAG, "Import complete: $stats")
                    stats
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed", e)
                    throw e
                }
            }

        /**
         * Collects all backup data including app info and statistics.
         */
        private suspend fun collectData(): BackupData =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "Collecting data for backup...")

                val timestamp = DateTimeFormatter.formatCurrentISO8601()

                // Collect all data
                val appInfo = collectAppInfo()
                val settings = collectSettings()
                val books = collectBookMetadata()
                val favorites = collectFavorites()
                val searchHistory = collectSearchHistory()
                val scanPaths = collectScanPaths()
                val statistics = collectStatistics(books, favorites, searchHistory, scanPaths)

                Log.d(
                    TAG,
                    "Collected: ${books.size} books, ${favorites.size} favorites, " +
                        "${searchHistory.size} history items, ${scanPaths.size} scan paths",
                )
                Log.d(TAG, "App info: ${appInfo.versionName} (${appInfo.versionCode})")
                Log.d(TAG, "Statistics: ${statistics.totalBooks} books, ${statistics.totalDuration}ms duration")

                BackupData(
                    version = appInfo.versionName, // App version
                    schemaVersion = "2.0.0", // Backup format version
                    timestamp = timestamp,
                    appInfo = appInfo,
                    statistics = statistics,
                    settings = settings,
                    bookMetadata = books,
                    favorites = favorites,
                    searchHistory = searchHistory,
                    scanPaths = scanPaths,
                )
            }

        /**
         * Collects app version and device information.
         */
        private fun collectAppInfo(): AppInfo {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

            val flavor =
                try {
                    // Try to get flavor from BuildConfig.APPLICATION_ID
                    // Each flavor has a different applicationId suffix:
                    // - dev: .dev
                    // - stage: .stage
                    // - beta: .beta
                    // - prod: no suffix
                    val buildConfigClass = Class.forName("com.jabook.app.jabook.BuildConfig")
                    val applicationId = buildConfigClass.getField("APPLICATION_ID").get(null) as? String

                    when {
                        applicationId?.endsWith(".dev") == true -> "dev"
                        applicationId?.endsWith(".stage") == true -> "stage"
                        applicationId?.endsWith(".beta") == true -> "beta"
                        applicationId == "com.jabook.app.jabook" -> "prod"
                        else -> {
                            // Fallback: try to get from versionName suffix
                            when {
                                versionName.endsWith("-dev") -> "dev"
                                versionName.endsWith("-stage") -> "stage"
                                versionName.endsWith("-beta") -> "beta"
                                else -> "prod" // Default to prod if no suffix
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get BuildConfig.APPLICATION_ID, using versionName fallback", e)
                    // Fallback: determine from versionName suffix
                    when {
                        versionName.endsWith("-dev") -> "dev"
                        versionName.endsWith("-stage") -> "stage"
                        versionName.endsWith("-beta") -> "beta"
                        else -> "prod" // Default to prod if no suffix
                    }
                }

            return AppInfo(
                versionName = versionName,
                versionCode = versionCode,
                flavor = flavor,
                platform = "Android",
                androidVersion = android.os.Build.VERSION.SDK_INT,
                deviceModel = android.os.Build.MODEL,
                deviceManufacturer = android.os.Build.MANUFACTURER,
            )
        }

        /**
         * Collects statistics about the backup.
         */
        private fun collectStatistics(
            books: List<BookBackup>,
            favorites: List<FavoriteBackup>,
            searchHistory: List<SearchHistoryBackup>,
            scanPaths: List<ScanPathBackup>,
        ): BackupStatistics {
            val downloadedBooks = books.count { it.downloadStatus == "DOWNLOADED" }
            val totalDuration = books.sumOf { it.duration }

            return BackupStatistics(
                totalBooks = books.size,
                downloadedBooks = downloadedBooks,
                favoritesCount = favorites.size,
                historyCount = searchHistory.size,
                scanPathsCount = scanPaths.size,
                totalDuration = totalDuration,
                backupSizeBytes = 0, // Will be filled after serialization
            )
        }

        /**
         * Collects current app settings.
         */
        private suspend fun collectSettings(): AppSettings {
            val userPrefs = userPreferencesRepository.userData.first()
            val protoSettings = protoSettingsRepository.userPreferences.first()

            // Defaults for empty values
            public val defaultDownloadPath: String =
                "JabookAudio"

            public val defaultMirrors =
                listOf(
                    "https://rutracker.org",
                    "https://rutracker.net",
                    "https://rutracker.nl",
                )

            // FIX: Get ACTUAL current mirror from MirrorManager instead of guessing
            val actualMirror = mirrorManager.currentMirror.value

            return AppSettings(
                theme = userPrefs.theme.name,
                sortOrder = userPrefs.sortOrder.name,
                viewMode = userPrefs.viewMode.name,
                autoPlayNext = userPrefs.autoPlayNext,
                playbackSpeed = userPrefs.playbackSpeed,
                font = userPrefs.font.name,
                normalizeChapterTitles = userPrefs.normalizeChapterTitles,
                wifiOnlyDownload = protoSettings.wifiOnlyDownload,
                // Use default if empty
                downloadPath = protoSettings.downloadPath.ifEmpty { defaultDownloadPath },
                // FIXED: Use actual mirror from MirrorManager
                currentMirror = actualMirror.ifEmpty { defaultMirrors.first() },
                autoSwitchMirror = protoSettings.autoSwitchMirror,
                limitDownloadSpeed = protoSettings.limitDownloadSpeed,
                maxDownloadSpeedKb = protoSettings.maxDownloadSpeedKb,
                maxConcurrentDownloads = protoSettings.maxConcurrentDownloads,
                rewindDurationSeconds = protoSettings.rewindDurationSeconds,
                forwardDurationSeconds = protoSettings.forwardDurationSeconds,
                languageCode = protoSettings.languageCode,
                useDynamicColors = protoSettings.useDynamicColors,
                notificationsEnabled = protoSettings.notificationsEnabled,
                downloadNotifications = protoSettings.downloadNotifications,
                playerNotifications = protoSettings.playerNotifications,
                customMirrors = protoSettings.customMirrorsList,
            )
        }

        /**
         * Collects book metadata from database.
         */
        private suspend fun collectBookMetadata(): List<BookBackup> {
            val books = database.booksDao().getAllBooksFlow().first()
            return books.map { entity ->
                // Read timestamps from PlayerPersistence
                val playerState =
                    try {
                        playerPersistenceManager.getPlayerState(entity.id)
                    } catch (e: Exception) {
                        null
                    }

                BookBackup(
                    id = entity.id,
                    title = entity.title,
                    author = entity.author,
                    lastPosition = entity.currentPosition,
                    duration = entity.totalDuration,
                    coverPath = entity.coverUrl,
                    totalProgress = entity.totalProgress,
                    isCompleted = entity.currentPosition >= entity.totalDuration * 0.98,
                    downloadStatus = entity.downloadStatus,
                    addedDate = entity.addedDate,
                    rewindDuration = entity.rewindDuration,
                    forwardDuration = entity.forwardDuration,
                    // Save activity timestamps
                    lastPlayedTimestamp = playerState?.lastPlayedTimestamp ?: 0L,
                    completedTimestamp = playerState?.completedTimestamp ?: 0L,
                    // NEW Phase 9B: Torrent metadata (not yet in entity, null for now)
                    torrentPath = null, // TODO: Add to BookEntity when torrent download is implemented
                    sourceUrl = null,
                    magnetUrl = null,
                    topicId = null,
                )
            }
        }

        /**
         * Collects scan paths.
         */
        private suspend fun collectScanPaths(): List<ScanPathBackup> =
            database.scanPathDao().getAllPathsList().map { entity ->
                ScanPathBackup(
                    path = entity.path,
                    addedDate = entity.addedDate,
                )
            }

        private suspend fun collectFavorites(): List<FavoriteBackup> {
            try {
                return database.favoriteDao().getAllFavorites().first().map { entity ->
                    FavoriteBackup(
                        bookId = entity.topicId,
                        title = entity.title,
                        author = entity.author,
                        category = entity.category,
                        size = entity.size,
                        magnetUrl = entity.magnetUrl,
                        coverUrl = entity.coverUrl,
                        performer = entity.performer,
                        genres = entity.genres,
                        addedDate = DateTimeFormatter.parseISO8601ToMillis(entity.addedDate),
                        duration = entity.duration,
                        bitrate = entity.bitrate,
                        audioCodec = entity.audioCodec,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to collect favorites", e)
                return emptyList()
            }
        }

        /**
         * Collects search history.
         */
        private suspend fun collectSearchHistory(): List<SearchHistoryBackup> =
            database.searchHistoryDao().getRecentSearches(limit = 1000).first().map { entity ->
                SearchHistoryBackup(
                    query = entity.query,
                    timestamp = entity.timestamp,
                    resultCount = entity.resultCount,
                )
            }

        /**
         * Restores settings from backup.
         */
        private suspend fun restoreSettings(settings: AppSettings) {
            try {
                // Restore UserPreferences
                try {
                    val theme = AppTheme.valueOf(settings.theme)
                    userPreferencesRepository.setTheme(theme)
                } catch (e: Exception) {
                    // Ignore invalid theme enum
                }

                // Restore sort order
                try {
                    val sortOrder =
                        com.jabook.app.jabook.compose.data.model.BookSortOrder
                            .valueOf(settings.sortOrder)
                    userPreferencesRepository.setSortOrder(sortOrder)
                } catch (e: Exception) {
                    // Ignore invalid sort order enum, keep default
                }

                // Restore view mode
                try {
                    val viewMode =
                        com.jabook.app.jabook.compose.data.model.LibraryViewMode
                            .valueOf(settings.viewMode)
                    userPreferencesRepository.setViewMode(viewMode)
                } catch (e: Exception) {
                    // Ignore invalid view mode enum, keep default
                }

                userPreferencesRepository.setAutoPlayNext(settings.autoPlayNext)
                userPreferencesRepository.setPlaybackSpeed(settings.playbackSpeed)

                // Restore font preference
                try {
                    val font =
                        com.jabook.app.jabook.compose.data.model.AppFont
                            .valueOf(settings.font)
                    userPreferencesRepository.setFont(font)
                } catch (e: Exception) {
                    // Ignore invalid font enum, keep default
                }

                // Restore normalize chapter titles
                userPreferencesRepository.setNormalizeChapterTitles(settings.normalizeChapterTitles)

                // Restore ProtoSettings
                protoSettingsRepository.updateWifiOnly(settings.wifiOnlyDownload)
                protoSettingsRepository.updateDownloadPath(settings.downloadPath)
                protoSettingsRepository.updateSelectedMirror(settings.currentMirror)
                protoSettingsRepository.updateAutoSwitchMirror(settings.autoSwitchMirror)
                protoSettingsRepository.updateLimitDownloadSpeed(settings.limitDownloadSpeed)
                protoSettingsRepository.updateMaxDownloadSpeed(settings.maxDownloadSpeedKb)
                protoSettingsRepository.updateMaxConcurrentDownloads(settings.maxConcurrentDownloads)
                protoSettingsRepository.updateAudioSettings(
                    rewindSeconds = settings.rewindDurationSeconds,
                    forwardSeconds = settings.forwardDurationSeconds,
                )
                protoSettingsRepository.updateLanguage(settings.languageCode)
                protoSettingsRepository.updateDynamicColors(settings.useDynamicColors)
                protoSettingsRepository.updateNotificationSettings(
                    notificationsEnabled = settings.notificationsEnabled,
                    downloadNotifications = settings.downloadNotifications,
                    playerNotifications = settings.playerNotifications,
                )
                settings.customMirrors.forEach {
                    protoSettingsRepository.addCustomMirror(it)
                }

                Log.d(TAG, "All settings restored successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore some settings", e)
                // Don't throw, allow partial restore
            }
        }

        /**
         * Restores books to database.
         */
        private suspend fun restoreBooks(books: List<BookBackup>) {
            val dao = database.booksDao()

            books.forEach { backup ->
                val existing = dao.getBookById(backup.id)
                if (existing != null) {
                    // Update existing book
                    dao.updatePlaybackProgress(
                        bookId = backup.id,
                        position = backup.lastPosition,
                        progress = backup.totalProgress,
                        chapterIndex = 0,
                        timestamp = backup.lastPlayedTimestamp.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    )
                    dao.updateBookSettings(
                        bookId = backup.id,
                        rewindDuration = backup.rewindDuration,
                        forwardDuration = backup.forwardDuration,
                    )

                    // Restore timestamps to PlayerPersistence
                    try {
                        playerPersistenceManager.savePlayerState(
                            com.jabook.app.jabook.audio.PlayerState(
                                bookId = backup.id,
                                positionMs = backup.lastPosition,
                                durationMs = backup.duration,
                                filePaths = emptyList(),
                                lastPlayedTimestamp = backup.lastPlayedTimestamp,
                                completedTimestamp = backup.completedTimestamp,
                            ),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore timestamps for ${backup.id}", e)
                    }
                } else {
                    // Insert new book (stub for history)
                    dao.insertBook(
                        BookEntity(
                            id = backup.id,
                            title = backup.title,
                            author = backup.author,
                            coverUrl = backup.coverPath,
                            description = null,
                            totalDuration = backup.duration,
                            currentPosition = backup.lastPosition,
                            totalProgress = backup.totalProgress,
                            downloadStatus = "NOT_DOWNLOADED",
                            addedDate = backup.addedDate,
                            rewindDuration = backup.rewindDuration,
                            forwardDuration = backup.forwardDuration,
                            isFavorite = false,
                        ),
                    )

                    // Restore timestamps for new book too
                    try {
                        playerPersistenceManager.savePlayerState(
                            com.jabook.app.jabook.audio.PlayerState(
                                bookId = backup.id,
                                positionMs = backup.lastPosition,
                                durationMs = backup.duration,
                                filePaths = emptyList(),
                                lastPlayedTimestamp = backup.lastPlayedTimestamp,
                                completedTimestamp = backup.completedTimestamp,
                            ),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore timestamps for new book ${backup.id}", e)
                    }
                }
            }
        }

        /**
         * Restores favorites.
         */
        private suspend fun restoreFavorites(favorites: List<FavoriteBackup>) {
            val bookDao = database.booksDao()
            val favoriteDao = database.favoriteDao()

            favorites.forEach { fav ->
                // 1. Mark as favorite in BooksDao (if exists as a book)
                bookDao.updateFavoriteStatus(fav.bookId, true)

                // 2. Insert into FavoriteDao (for remote/search results)
                val addedDateStr = DateTimeFormatter.formatISO8601(fav.addedDate)
                favoriteDao.insertFavorite(
                    com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity(
                        topicId = fav.bookId,
                        title = fav.title,
                        author = fav.author,
                        category = fav.category,
                        size = fav.size,
                        magnetUrl = fav.magnetUrl,
                        coverUrl = fav.coverUrl,
                        performer = fav.performer,
                        genres = fav.genres,
                        addedDate = addedDateStr,
                        addedToFavorites = addedDateStr, // Use addedDate as fallback
                        duration = fav.duration,
                        bitrate = fav.bitrate,
                        audioCodec = fav.audioCodec,
                    ),
                )
            }
        }

        /**
         * Restores search history.
         */
        private suspend fun restoreSearchHistory(history: List<SearchHistoryBackup>) {
            val dao = database.searchHistoryDao()
            history.forEach { item ->
                dao.insertSearch(
                    SearchHistoryEntity(
                        query = item.query,
                        timestamp = item.timestamp,
                        resultCount = item.resultCount,
                    ),
                )
            }
        }

        /**
         * Restores scan paths.
         */
        private suspend fun restoreScanPaths(paths: List<ScanPathBackup>) {
            val dao = database.scanPathDao()
            paths.forEach { item ->
                dao.insertPath(
                    ScanPathEntity(
                        path = item.path,
                        addedDate = item.addedDate,
                    ),
                )
            }
        }

        /**
         * Checks if backup version is compatible.
         * Accepts v1.x.x (legacy) and v2.x.x (current) versions.
         */
        private fun isCompatibleVersion(version: String): Boolean = version.startsWith("1.") || version.startsWith("2.")
    }
