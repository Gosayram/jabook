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
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.debug.DebugRuntimeOverrides
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.permissions.StorageHealthChecker
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.data.storage.AtomicFileWriter
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
        private val backupRuntimeSecurity: BackupRuntimeSecurity,
        private val debugRuntimeOverrides: DebugRuntimeOverrides,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("BackupService")

        public companion object {
            private const val CURRENT_VERSION = "1.0.0"
            private val DEFAULT_CONFLICT_POLICY: ConflictResolutionPolicy = ConflictResolutionPolicy.KEEP_NEWER
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
        public suspend fun exportToFile(): Uri =
            withContext(Dispatchers.IO) {
                try {
                    logger.d { "Starting data export" }
                    val storageHealthChecker =
                        StorageHealthChecker(
                            forceLowStorageProvider = { debugRuntimeOverrides.isForceLowStorageEnabled() },
                        )
                    val health = storageHealthChecker.check(context.cacheDir)
                    if (!health.isHealthy) {
                        throw IOException(health.warningMessage ?: "Low storage: backup export aborted")
                    }

                    // 1. Collect data
                    val backupData = collectData()

                    // 2. Serialize to JSON
                    val payloadJson = json.encodeToString(backupData)
                    val integrityMetadata = backupRuntimeSecurity.createIntegrityMetadata(payloadJson)
                    val jsonString =
                        if (integrityMetadata == null) {
                            payloadJson
                        } else {
                            json.encodeToString(
                                BackupIntegrityEnvelope(
                                    payload = backupData,
                                    integrity = integrityMetadata,
                                ),
                            )
                        }
                    logger.d { "Serialized backup: ${jsonString.length} bytes" }

                    // 3. Write to file
                    val timestamp = DateTimeFormatter.formatCurrentForFilename()
                    val fileName: String = "jabook_backup_$timestamp.json"
                    val file = File(context.cacheDir, fileName)
                    val encoded = jsonString.toByteArray(Charsets.UTF_8)
                    AtomicFileWriter.writeWithLock(file) { output ->
                        output.write(encoded)
                        encoded.size.toLong()
                    }

                    logger.d { "Backup written to ${file.absolutePath}" }

                    // 4. Return FileProvider URI
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } catch (e: Exception) {
                    logger.e({ "Export failed" }, e)
                    throw e
                }
            }

        /**
         * Imports app data from a JSON backup file.
         * Returns statistics about imported items.
         */
        public suspend fun importFromFile(uri: Uri): ImportStats =
            withContext(Dispatchers.IO) {
                try {
                    logger.d { "Starting data import from $uri" }

                    // 1. Read file
                    val jsonString =
                        context.contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        } ?: throw IOException("Cannot read backup file")

                    logger.d { "Read backup file: ${jsonString.length} bytes" }

                    // 2. Parse JSON
                    val backupData = decodeBackupData(jsonString)
                    logger.d { "Parsed backup version ${backupData.version}" }

                    // 3. Validate schema version (support both 1.x and 2.x)
                    val schemaVersion = backupData.schemaVersion
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
                    logger.d { "Settings restored" }

                    // Import book metadata
                    restoreBooks(backupData.bookMetadata, DEFAULT_CONFLICT_POLICY)
                    stats.booksRestored = backupData.bookMetadata.size

                    // Import favorites
                    restoreFavorites(backupData.favorites, DEFAULT_CONFLICT_POLICY)
                    stats.favoritesRestored = backupData.favorites.size

                    // Import search history
                    restoreSearchHistory(backupData.searchHistory, DEFAULT_CONFLICT_POLICY)
                    stats.historyRestored = backupData.searchHistory.size

                    // Import scan paths
                    restoreScanPaths(backupData.scanPaths, DEFAULT_CONFLICT_POLICY)

                    logger.d { "Import complete: $stats" }
                    stats
                } catch (e: Exception) {
                    logger.e({ "Import failed" }, e)
                    throw e
                }
            }

        private fun decodeBackupData(rawJson: String): BackupData {
            val integrityEnvelope =
                runCatching {
                    json.decodeFromString<BackupIntegrityEnvelope>(rawJson)
                }.getOrNull()

            if (integrityEnvelope == null) {
                return json.decodeFromString(rawJson)
            }

            val payloadJson = json.encodeToString(integrityEnvelope.payload)
            val integrity = integrityEnvelope.integrity
            if (integrity == null) {
                logger.w { "Backup integrity metadata is missing, proceeding with payload import" }
                return integrityEnvelope.payload
            }

            return when (
                backupRuntimeSecurity.verifyIntegrity(
                    payloadJson = payloadJson,
                    metadata = integrity,
                )
            ) {
                BackupIntegrityVerificationResult.VERIFIED -> integrityEnvelope.payload
                BackupIntegrityVerificationResult.KEY_UNAVAILABLE -> {
                    logger.w { "Backup signature key is unavailable, importing payload without local verification" }
                    integrityEnvelope.payload
                }
                BackupIntegrityVerificationResult.UNSUPPORTED_ALGORITHM -> {
                    logger.w { "Unsupported backup integrity algorithm ${integrity.algorithm}, importing payload" }
                    integrityEnvelope.payload
                }
                BackupIntegrityVerificationResult.SIGNATURE_INVALID -> {
                    throw SecurityException("Backup integrity verification failed. The file may be tampered.")
                }
            }
        }

        /**
         * Collects all backup data including app info and statistics.
         */
        private suspend fun collectData(): BackupData =
            withContext(Dispatchers.IO) {
                logger.d { "Collecting data for backup..." }

                val timestamp = DateTimeFormatter.formatCurrentISO8601()

                // Collect all data
                val appInfo = collectAppInfo()
                val settings = collectSettings()
                val books = collectBookMetadata()
                val favorites = collectFavorites()
                val searchHistory = collectSearchHistory()
                val scanPaths = collectScanPaths()
                val statistics = collectStatistics(books, favorites, searchHistory, scanPaths)

                logger.d {
                    "Collected: ${books.size} books, ${favorites.size} favorites, " +
                        "${searchHistory.size} history items, ${scanPaths.size} scan paths"
                }
                logger.d { "App info: ${appInfo.versionName} (${appInfo.versionCode})" }
                logger.d { "Statistics: ${statistics.totalBooks} books, ${statistics.totalDuration}ms duration" }

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
                    logger.e({ "Could not get BuildConfig.APPLICATION_ID, using versionName fallback" }, e)
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
            val defaultDownloadPath: String =
                "JabookAudio"

            val defaultMirrors =
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
                autoLoadCoversOnCellular = protoSettings.autoLoadCoversOnCellular,
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
                    lastPosition = entity.currentPosition.toInt(),
                    duration = entity.totalDuration.toInt(),
                    coverPath = entity.coverUrl,
                    totalProgress = entity.totalProgress,
                    isCompleted = entity.currentPosition >= entity.totalDuration * 0.98,
                    downloadStatus = entity.downloadStatus,
                    addedDate = entity.addedDate.toInt(),
                    rewindDuration = entity.rewindDuration,
                    forwardDuration = entity.forwardDuration,
                    // Save activity timestamps
                    lastPlayedTimestamp = (playerState?.lastPlayedTimestamp ?: 0L).toInt(),
                    completedTimestamp = (playerState?.completedTimestamp ?: 0L).toInt(),
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
                logger.e({ "Failed to collect favorites" }, e)
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
                protoSettingsRepository.updateAutoLoadCoversOnCellular(settings.autoLoadCoversOnCellular)
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

                logger.d { "All settings restored successfully" }
            } catch (e: Exception) {
                logger.e({ "Failed to restore some settings" }, e)
                // Don't throw, allow partial restore
            }
        }

        /**
         * Restores books to database.
         */
        private suspend fun restoreBooks(
            books: List<BookBackup>,
            policy: ConflictResolutionPolicy,
        ) {
            val dao = database.booksDao()

            books.forEach { backup ->
                val existing = dao.getBookById(backup.id)
                if (existing != null) {
                    val localTimestamp = existing.lastPlayedDate ?: existing.addedDate
                    val incomingTimestamp =
                        backup.lastPlayedTimestamp
                            .takeIf { it > 0 }
                            ?.toLong()
                            ?: backup.addedDate.toLong()
                    val shouldApplyIncoming =
                        ConflictResolutionResolver.shouldUseIncoming(
                            policy = policy,
                            localExists = true,
                            localTimestamp = localTimestamp,
                            incomingTimestamp = incomingTimestamp,
                        )

                    if (!shouldApplyIncoming) {
                        return@forEach
                    }

                    // Update existing book
                    dao.updatePlaybackProgress(
                        bookId = backup.id,
                        position = backup.lastPosition.toLong(),
                        progress = backup.totalProgress,
                        chapterIndex = 0,
                        timestamp =
                            backup.lastPlayedTimestamp
                                .takeIf {
                                    it > 0
                                }?.toLong() ?: System.currentTimeMillis(),
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
                                positionMs = backup.lastPosition.toLong(),
                                durationMs = backup.duration.toLong(),
                                filePaths = emptyList(),
                                lastPlayedTimestamp = backup.lastPlayedTimestamp.toLong(),
                                completedTimestamp = backup.completedTimestamp.toLong(),
                            ),
                        )
                    } catch (e: Exception) {
                        logger.e({ "Failed to restore timestamps for ${backup.id}" }, e)
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
                            totalDuration = backup.duration.toLong(),
                            currentPosition = backup.lastPosition.toLong(),
                            totalProgress = backup.totalProgress,
                            downloadStatus = "NOT_DOWNLOADED",
                            addedDate = backup.addedDate.toLong(),
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
                                positionMs = backup.lastPosition.toLong(),
                                durationMs = backup.duration.toLong(),
                                filePaths = emptyList(),
                                lastPlayedTimestamp = backup.lastPlayedTimestamp.toLong(),
                                completedTimestamp = backup.completedTimestamp.toLong(),
                            ),
                        )
                    } catch (e: Exception) {
                        logger.e({ "Failed to restore timestamps for new book ${backup.id}" }, e)
                    }
                }
            }
        }

        /**
         * Restores favorites.
         */
        private suspend fun restoreFavorites(
            favorites: List<FavoriteBackup>,
            policy: ConflictResolutionPolicy,
        ) {
            val bookDao = database.booksDao()
            val favoriteDao = database.favoriteDao()

            favorites.forEach { fav ->
                val existing = favoriteDao.getFavoriteById(fav.bookId)
                val localTimestamp =
                    existing?.addedToFavorites?.let { dateStr ->
                        runCatching { DateTimeFormatter.parseISO8601ToMillis(dateStr) }.getOrNull()
                    } ?: 0L
                val shouldApplyIncoming =
                    ConflictResolutionResolver.shouldUseIncoming(
                        policy = policy,
                        localExists = existing != null,
                        localTimestamp = localTimestamp,
                        incomingTimestamp = fav.addedDate,
                    )
                if (!shouldApplyIncoming) {
                    return@forEach
                }

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
        private suspend fun restoreSearchHistory(
            history: List<SearchHistoryBackup>,
            policy: ConflictResolutionPolicy,
        ) {
            val dao = database.searchHistoryDao()
            val existingByQuery =
                dao
                    .getRecentSearches(limit = 10000)
                    .first()
                    .groupBy { it.query }
                    .mapValues { entry -> entry.value.maxOfOrNull { it.timestamp } ?: 0L }

            database.withTransaction {
                history.forEach { item ->
                    val localTimestamp = existingByQuery[item.query] ?: 0L
                    val localExists = localTimestamp > 0L
                    val shouldApplyIncoming =
                        ConflictResolutionResolver.shouldUseIncoming(
                            policy = policy,
                            localExists = localExists,
                            localTimestamp = localTimestamp,
                            incomingTimestamp = item.timestamp,
                        )
                    if (!shouldApplyIncoming) {
                        return@forEach
                    }

                    if (localExists) {
                        dao.deleteByQuery(item.query)
                    }
                    dao.insertSearch(
                        SearchHistoryEntity(
                            query = item.query,
                            timestamp = item.timestamp,
                            resultCount = item.resultCount,
                        ),
                    )
                }
            }
        }

        /**
         * Restores scan paths.
         */
        private suspend fun restoreScanPaths(
            paths: List<ScanPathBackup>,
            policy: ConflictResolutionPolicy,
        ) {
            val dao = database.scanPathDao()
            val existingByPath = dao.getAllPathsList().associateBy({ it.path }, { it.addedDate })
            database.withTransaction {
                paths.forEach { item ->
                    val localTimestamp = existingByPath[item.path] ?: 0L
                    val localExists = existingByPath.containsKey(item.path)
                    val shouldApplyIncoming =
                        ConflictResolutionResolver.shouldUseIncoming(
                            policy = policy,
                            localExists = localExists,
                            localTimestamp = localTimestamp,
                            incomingTimestamp = item.addedDate,
                        )
                    if (!shouldApplyIncoming) {
                        return@forEach
                    }

                    if (localExists) {
                        dao.deletePathByString(item.path)
                    }
                    dao.insertPath(
                        ScanPathEntity(
                            path = item.path,
                            addedDate = item.addedDate,
                        ),
                    )
                }
            }
        }

        /**
         * Checks if backup version is compatible.
         * Accepts v1.x.x (legacy) and v2.x.x (current) versions.
         */
        private fun isCompatibleVersion(version: String): Boolean = version.startsWith("1.") || version.startsWith("2.")
    }
