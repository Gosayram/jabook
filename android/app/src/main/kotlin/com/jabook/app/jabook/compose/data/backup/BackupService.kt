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
class BackupService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val database: JabookDatabase,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val protoSettingsRepository: ProtoSettingsRepository,
        private val playerPersistenceManager: com.jabook.app.jabook.audio.PlayerPersistenceManager,
    ) {
        companion object {
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
                    val backupData =
                        BackupData(
                            version = CURRENT_VERSION,
                            timestamp = DateTimeFormatter.formatCurrentISO8601(),
                            settings = collectSettings(),
                            bookMetadata = collectBookMetadata(),
                            favorites = collectFavorites(),
                            searchHistory = collectSearchHistory(),
                            scanPaths = collectScanPaths(),
                        )

                    // 2. Serialize to JSON
                    val jsonString = json.encodeToString(backupData)
                    Log.d(TAG, "Serialized backup: ${jsonString.length} bytes")

                    // 3. Write to file
                    val timestamp = DateTimeFormatter.formatCurrentForFilename()
                    val fileName = "jabook_backup_$timestamp.json"
                    val file = File(context.cacheDir, fileName)
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

                    // 3. Validate version
                    if (!isCompatibleVersion(backupData.version)) {
                        throw IllegalArgumentException(
                            "Incompatible backup version: ${backupData.version}. " +
                                "Expected version starting with '1.'",
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
         * Collects current app settings.
         */
        private suspend fun collectSettings(): AppSettings {
            val userPrefs = userPreferencesRepository.userData.first()
            val protoSettings = protoSettingsRepository.userPreferences.first()

            // Defaults for empty values
            val defaultDownloadPath = "JabookAudio"
            val defaultMirrors =
                listOf(
                    "https://rutracker.org",
                    "https://rutracker.net",
                    "https://rutracker.nl",
                )

            return AppSettings(
                theme = userPrefs.theme.name,
                autoPlayNext = userPrefs.autoPlayNext,
                playbackSpeed = userPrefs.playbackSpeed,
                font = userPrefs.font.name,
                wifiOnlyDownload = protoSettings.wifiOnlyDownload,
                // Use default if empty
                downloadPath = protoSettings.downloadPath.ifEmpty { defaultDownloadPath },
                // Use first custom mirror or first default mirror if empty
                currentMirror =
                    protoSettings.selectedMirror.ifEmpty {
                        protoSettings.customMirrorsList.firstOrNull() ?: defaultMirrors.first()
                    },
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
         * Currently accepts any 1.x.x version.
         */
        private fun isCompatibleVersion(version: String): Boolean = version.startsWith("1.")
    }
