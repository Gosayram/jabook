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

package com.jabook.app.jabook.compose.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.domain.usecase.ListeningStatsUseCase
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.backup.BackupService
import com.jabook.app.jabook.compose.data.backup.ImportStats
import com.jabook.app.jabook.compose.data.cache.CacheManager
import com.jabook.app.jabook.compose.data.cache.CacheStatistics
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.data.network.MirrorHealth
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.preferences.UserPreferencesSerializer
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.UserEqPresetRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.compose.data.worker.LibraryScanWorker
import com.jabook.app.jabook.compose.data.worker.WorkConstraintsPolicy
import com.jabook.app.jabook.compose.domain.usecase.library.GetLibraryUseCase
import com.jabook.app.jabook.compose.feature.library.ProductivePeriod
import com.jabook.app.jabook.compose.feature.library.WeeklyRecapState
import com.jabook.app.jabook.compose.feature.library.YearRecapState
import com.jabook.app.jabook.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages both old preferences (UserPreferencesRepository) and new Proto DataStore settings.
 * Gradually migrating to Proto DataStore.
 */
@HiltViewModel
public class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val userPreferencesRepository: UserPreferencesRepository, // Keep for migration
        private val authRepository: com.jabook.app.jabook.compose.domain.repository.AuthRepository,
        private val booksRepository: BooksRepository,
        private val mirrorManager: MirrorManager,
        private val backupService: BackupService,
        private val cacheManager: CacheManager,
        private val updateBookSettingsUseCase: com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase,
        private val workManager: WorkManager,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val userEqPresetRepository: UserEqPresetRepository,
        private val torrentManager: TorrentManager,
        private val loggerFactory: LoggerFactory,
        private val getLibraryUseCase: GetLibraryUseCase,
        private val listeningStatsUseCase: ListeningStatsUseCase,
    ) : ViewModel() {
        private val logger = loggerFactory.get("SettingsViewModel")

        // Expose active downloads for the settings UI
        public val activeDownloads: StateFlow<List<TorrentDownload>> =
            torrentManager.downloadsFlow
                .map { downloadMap ->
                    downloadMap.values
                        .filter { download ->
                            download.state in
                                setOf(
                                    TorrentState.DOWNLOADING,
                                    TorrentState.SEEDING,
                                    TorrentState.QUEUED,
                                    TorrentState.CHECKING,
                                    TorrentState.DOWNLOADING_METADATA,
                                    TorrentState.PAUSED,
                                )
                        }.sortedByDescending { it.addedTime }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )
        public val activeDownloadsCount: StateFlow<Int> =
            activeDownloads
                .map { it.size }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 0,
                )
        public val scanProgress: StateFlow<ScanProgress> =
            booksRepository.getScanProgress().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ScanProgress.Idle,
            )

        public val weeklyRecapState: StateFlow<WeeklyRecapState?> =
            combine(
                getLibraryUseCase(com.jabook.app.jabook.compose.data.model.BookSortOrder.BY_ACTIVITY),
                listeningStatsUseCase.observeSummary(
                    fromEpochMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7),
                    toEpochMs = System.currentTimeMillis(),
                ),
            ) { books, summary ->
                val weeklyCompletedBooks =
                    books.count { it.isCompleted && (it.lastPlayedDate ?: 0L) >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) }
                WeeklyRecapState(
                    minutesListened = (summary.totalContentTimeMs / 1000L / 60L).toInt(),
                    booksCompleted = weeklyCompletedBooks,
                    productivePeriod = resolveProductivePeriod(books),
                    streakDays = summary.activeDays.coerceAtLeast(0),
                )
            }.catch {
                if (it is kotlinx.coroutines.CancellationException) throw it
                // Recap is optional — keep last known state on upstream failure
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

        public val yearRecapState: StateFlow<YearRecapState?> =
            combine(
                getLibraryUseCase(com.jabook.app.jabook.compose.data.model.BookSortOrder.BY_ACTIVITY),
                listeningStatsUseCase.observeSummary(
                    fromEpochMs = resolveYearStartEpochMs(),
                    toEpochMs = System.currentTimeMillis(),
                ),
            ) { books, summary ->
                val yearStartEpochMs = resolveYearStartEpochMs()
                val completedBooks =
                    books.count { it.isCompleted && (it.lastPlayedDate ?: 0L) >= yearStartEpochMs }
                val topAuthor =
                    books
                        .groupingBy { it.author.ifBlank { context.getString(R.string.unknownAuthor) } }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                        ?: context.getString(R.string.unknownAuthor)

                YearRecapState(
                    year =
                        java.time.LocalDate
                            .now()
                            .year,
                    totalMinutesListened = (summary.totalContentTimeMs / 1000L / 60L).toInt().coerceAtLeast(0),
                    booksCompleted = completedBooks.coerceAtLeast(0),
                    activeDays = summary.activeDays.coerceAtLeast(0),
                    sessions = summary.totalSessions.coerceAtLeast(0),
                    topAuthor = topAuthor,
                )
            }.catch {
                if (it is kotlinx.coroutines.CancellationException) throw it
                // Recap is optional — keep last known state on upstream failure
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

        /** Real minutes listened per day (from listening_sessions), feeding the settings heatmap. */
        public val dailyListeningMinutes: StateFlow<Map<java.time.LocalDate, Int>> =
            listeningStatsUseCase
                .observeDayStats(
                    fromEpochMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(26 * 7),
                    toEpochMs = System.currentTimeMillis(),
                ).map { dayStats ->
                    dayStats.associate { stat ->
                        java.time.LocalDate.parse(stat.day) to (stat.contentTimeMs / 60000L).toInt().coerceAtLeast(1)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyMap(),
                )

        public val booksForStats: StateFlow<List<com.jabook.app.jabook.compose.domain.model.Book>> =
            getLibraryUseCase(com.jabook.app.jabook.compose.data.model.BookSortOrder.BY_ACTIVITY)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        public fun scanLibrary() {
            viewModelScope.launch {
                // Check if scan folders are configured
                val scanFolders = scanPathDao.getAllPathsList()
                if (scanFolders.isEmpty()) {
                    // No folders configured - skip scan
                    logger.w { "Scan skipped: no folders configured" }
                    return@launch
                }

                // Folders configured - proceed with scan
                val workRequest =
                    OneTimeWorkRequestBuilder<LibraryScanWorker>()
                        .addTag(LibraryScanWorker.WORK_TAG)
                        .setConstraints(WorkConstraintsPolicy.libraryScan())
                        .build()
                workManager.enqueueUniqueWork(
                    LibraryScanWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest,
                )
            }
        }

        public fun cancelScan() {
            workManager.cancelUniqueWork(LibraryScanWorker.WORK_NAME)
        }

        // Exposure of auth status for UI
        public val authStatus: StateFlow<com.jabook.app.jabook.compose.domain.model.AuthStatus> =
            authRepository.authStatus.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = com.jabook.app.jabook.compose.domain.model.AuthStatus.Unauthenticated,
            )

        public fun logout() {
            viewModelScope.launch {
                authRepository.logout()
            }
        }

        /**
         * Old user preferences - for backward compatibility.
         */
        public val userPreferences: StateFlow<com.jabook.app.jabook.compose.data.model.UserData?> =
            userPreferencesRepository.userData.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

        /**
         * New Proto DataStore settings.
         */
        public val protoSettings: StateFlow<UserPreferences> =
            settingsRepository.userPreferences.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    com.jabook.app.jabook.compose.data.preferences
                        .UserPreferencesSerializer.defaultValue,
            )

        public val customEqBands: StateFlow<List<Int>> =
            settingsRepository.customEqBands.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = List(10) { 0 },
            )

        // ===== Old preferences API (kept for compatibility) =====

        public fun updateTheme(theme: com.jabook.app.jabook.compose.data.model.AppTheme) {
            viewModelScope.launch {
                userPreferencesRepository.setTheme(theme)
            }
        }

        public fun updateSortOrder(sortOrder: com.jabook.app.jabook.compose.data.model.BookSortOrder) {
            viewModelScope.launch {
                userPreferencesRepository.setSortOrder(sortOrder)
            }
        }

        public fun updateAutoPlayNext(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setAutoPlayNext(enabled)
            }
        }

        public fun updateFont(font: com.jabook.app.jabook.compose.data.model.AppFont) {
            viewModelScope.launch {
                userPreferencesRepository.setFont(font)
            }
        }

        public fun updateNormalizeChapterTitles(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setNormalizeChapterTitles(enabled)
            }
        }

        public fun updateHaptics(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setHapticsEnabled(enabled)
            }
        }

        public fun updatePlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaybackSpeed(speed)
            }
        }

        // ===== New Proto DataStore API =====

        public fun updateProtoTheme(themeMode: com.jabook.app.jabook.compose.data.preferences.ThemeMode) {
            viewModelScope.launch {
                settingsRepository.updateThemeMode(themeMode)
            }
        }

        public fun updateDynamicColors(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateDynamicColors(enabled)
            }
        }

        public fun updateAccentSwatchIndex(index: Int) {
            viewModelScope.launch {
                settingsRepository.updateAccentSwatchIndex(index)
            }
        }

        public fun updatePlayerCoverMode(mode: Int) {
            viewModelScope.launch {
                settingsRepository.updatePlayerCoverMode(mode)
            }
        }

        public fun updateAudioSettings(
            rewindSeconds: Int? = null,
            forwardSeconds: Int? = null,
            resumeRewindSeconds: Int? = null,
            resumeRewindMode: com.jabook.app.jabook.compose.data.preferences.ResumeRewindMode? = null,
            resumeRewindAggressiveness: Float? = null,
            sleepTimerShakeExtendEnabled: Boolean? = null,
            holdToBoostSpeed: Float? = null,
            autoPipEnabled: Boolean? = null,
            headsetAutoplayEnabled: Boolean? = null,
            volumeBoost: String? = null,
            drcLevel: String? = null,
            speechEnhancer: Boolean? = null,
            normalizeVolume: Boolean? = null,
            autoVolumeLeveling: Boolean? = null,
            skipSilence: Boolean? = null,
            skipSilenceThresholdDb: Float? = null,
            skipSilenceMinMs: Int? = null,
            skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode? = null,
            crossfadeEnabled: Boolean? = null,
            crossfadeDurationMs: Long? = null,
            singleClickAction: Int? = null,
            doubleClickAction: Int? = null,
            tripleClickAction: Int? = null,
            longPressAction: Int? = null,
            notificationActionSlots: List<Int>? = null,
        ) {
            viewModelScope.launch {
                settingsRepository.updateAudioSettings(
                    rewindSeconds = rewindSeconds,
                    forwardSeconds = forwardSeconds,
                    resumeRewindSeconds = resumeRewindSeconds,
                    resumeRewindMode = resumeRewindMode,
                    resumeRewindAggressiveness = resumeRewindAggressiveness,
                    sleepTimerShakeExtendEnabled = sleepTimerShakeExtendEnabled,
                    holdToBoostSpeed = holdToBoostSpeed,
                    autoPipEnabled = autoPipEnabled,
                    headsetAutoplayEnabled = headsetAutoplayEnabled,
                    volumeBoost = volumeBoost,
                    drcLevel = drcLevel,
                    speechEnhancer = speechEnhancer,
                    normalizeVolume = normalizeVolume,
                    autoVolumeLeveling = autoVolumeLeveling,
                    skipSilence = skipSilence,
                    skipSilenceThresholdDb = skipSilenceThresholdDb,
                    skipSilenceMinMs = skipSilenceMinMs,
                    skipSilenceMode = skipSilenceMode,
                    crossfadeEnabled = crossfadeEnabled,
                    crossfadeDurationMs = crossfadeDurationMs,
                    singleClickAction = singleClickAction,
                    doubleClickAction = doubleClickAction,
                    tripleClickAction = tripleClickAction,
                    longPressAction = longPressAction,
                    notificationActionSlots = notificationActionSlots,
                )
            }
        }

        public fun updateLanguage(languageCode: String) {
            viewModelScope.launch {
                userPreferencesRepository.setLanguage(languageCode)
            }
        }

        public fun updateEqualizerPreset(presetName: String) {
            viewModelScope.launch {
                settingsRepository.updateEqualizerPreset(presetName)
            }
        }

        public fun updateCustomEqBands(bands: List<Int>) {
            viewModelScope.launch {
                settingsRepository.updateCustomEqBands(bands)
            }
        }

        public fun saveEqPreset(
            name: String,
            bands: List<Int>,
            preampMillibels: Int,
        ) {
            viewModelScope.launch {
                userEqPresetRepository.savePreset(
                    name = name,
                    bands = bands,
                    preampMillibels = preampMillibels,
                )
            }
        }

        public fun updateNotifications(
            enabled: Boolean? = null,
            downloadNotifications: Boolean? = null,
            playerNotifications: Boolean? = null,
        ) {
            viewModelScope.launch {
                settingsRepository.updateNotificationSettings(
                    notificationsEnabled = enabled,
                    downloadNotifications = downloadNotifications,
                    playerNotifications = playerNotifications,
                )
            }
        }

        public fun resetToDefaults() {
            viewModelScope.launch {
                settingsRepository.resetToDefaults()
            }
        }

        // ===== Mirror Management =====

        /**
         * Current mirror domain from MirrorManager.
         */
        public val currentMirror: StateFlow<String> = mirrorManager.currentMirror

        /**
         * Available mirrors (default + custom).
         */
        public val availableMirrors: StateFlow<List<String>> = mirrorManager.availableMirrors

        /**
         * Update the selected mirror.
         */
        public fun updateMirror(domain: String) {
            viewModelScope.launch {
                mirrorManager.setMirror(domain)
            }
        }

        /**
         * Check mirror health and invoke callback with result.
         */
        public fun checkMirrorHealth(
            domain: String,
            onResult: (MirrorHealth) -> Unit,
        ) {
            viewModelScope.launch {
                val health = mirrorManager.checkMirrorHealth(domain)
                onResult(health)
            }
        }

        /**
         * Add a custom mirror domain.
         */
        public fun addCustomMirror(domain: String) {
            viewModelScope.launch {
                mirrorManager.addCustomMirror(domain)
            }
        }

        /**
         * Remove a custom mirror domain.
         */
        public fun removeCustomMirror(domain: String) {
            viewModelScope.launch {
                mirrorManager.removeCustomMirror(domain)
            }
        }

        /**
         * Update auto-switch mirror setting.
         */
        public fun updateAutoSwitch(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateAutoSwitchMirror(enabled)
            }
        }

        // ===== Download Settings =====

        public fun updateDownloadPath(uriString: String) {
            val path = resolvePathFromUri(uriString)
            viewModelScope.launch {
                settingsRepository.updateDownloadPath(path)
            }
        }

        private fun resolvePathFromUri(uriString: String): String {
            try {
                val uri = android.net.Uri.parse(uriString)
                if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
                    val path = uri.path ?: return uriString
                    val split = path.split(":")
                    if (split.size > 1) {
                        val type = split[0]
                        val relativePath = split[1]
                        if (type.endsWith("primary")) {
                            return "/storage/emulated/0/$relativePath"
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore parsing errors and return original
            }
            return uriString
        }

        public fun updateWifiOnly(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateWifiOnly(enabled)
            }
        }

        public fun updateAutoLoadCoversOnCellular(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateAutoLoadCoversOnCellular(enabled)
            }
        }

        private val _torrentStorageSize = MutableStateFlow<Long>(0L)
        public val torrentStorageSize: StateFlow<Long> = _torrentStorageSize.asStateFlow()

        public fun loadTorrentStorageSize() {
            viewModelScope.launch {
                val path = protoSettings.value.downloadPath
                if (path.isNotEmpty()) {
                    val size = FileUtils.getDirectorySize(File(path))
                    _torrentStorageSize.value = size
                }
            }
        }

        public fun deleteAllTorrents(deleteFiles: Boolean) {
            viewModelScope.launch {
                torrentManager.deleteAllTorrents(deleteFiles)
                // Refresh size after a short delay to allow file system ops
                kotlinx.coroutines.delay(500L)
                loadTorrentStorageSize()
            }
        }

        // ===== Backup & Restore =====

        private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
        public val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

        /**
         * Export app data to JSON backup file.
         */
        public fun exportData() {
            viewModelScope.launch {
                try {
                    _backupState.value = BackupUiState.Exporting
                    val uri = backupService.exportToFile()
                    _backupState.value = BackupUiState.ExportReady(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _backupState.value = BackupUiState.Error(e.message ?: context.getString(R.string.export_failed))
                }
            }
        }

        /**
         * Import app data from JSON backup file.
         */
        public fun importData(uri: android.net.Uri) {
            viewModelScope.launch {
                try {
                    _backupState.value = BackupUiState.Importing
                    val stats = backupService.importFromFile(uri)
                    _backupState.value = BackupUiState.ImportComplete(stats)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _backupState.value = BackupUiState.Error(e.message ?: context.getString(R.string.import_failed))
                }
            }
        }

        /**
         * Reset backup state to Idle.
         */
        public fun resetBackupState() {
            _backupState.value = BackupUiState.Idle
        }

        // ===== Cache Management =====

        private val _cacheStats = MutableStateFlow<CacheStatistics?>(null)
        public val cacheStats: StateFlow<CacheStatistics?> = _cacheStats.asStateFlow()

        private val _cacheOperation = MutableStateFlow<CacheOperationState>(CacheOperationState.Idle)
        public val cacheOperation: StateFlow<CacheOperationState> = _cacheOperation.asStateFlow()

        /**
         * Load cache statistics.
         */
        public fun loadCacheStatistics() {
            viewModelScope.launch {
                try {
                    _cacheOperation.value = CacheOperationState.Loading
                    val stats = cacheManager.getCacheStatistics()
                    _cacheStats.value = stats
                    _cacheOperation.value = CacheOperationState.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _cacheOperation.value = CacheOperationState.Error(e.message ?: context.getString(R.string.failed_to_load_cache_stats))
                }
            }
        }

        /**
         * Clear cache (all or specific type).
         */
        public fun clearCache(type: String? = null) {
            viewModelScope.launch {
                try {
                    _cacheOperation.value = CacheOperationState.Clearing
                    val success =
                        if (type != null) {
                            try {
                                val cacheType =
                                    com.jabook.app.jabook.compose.data.cache.CacheType
                                        .valueOf(type.uppercase())
                                cacheManager.clearCacheType(cacheType)
                            } catch (e: IllegalArgumentException) {
                                logger.e(e) { "Invalid cache type: $type" }
                                false
                            }
                        } else {
                            cacheManager.clearAllCache()
                        }

                    if (success) {
                        loadCacheStatistics() // Reload stats
                        _cacheOperation.value = CacheOperationState.Success
                    } else {
                        _cacheOperation.value = CacheOperationState.Error(context.getString(R.string.failed_to_clear_cache))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _cacheOperation.value = CacheOperationState.Error(e.message ?: context.getString(R.string.failed_to_clear_cache))
                }
            }
        }

        /**
         * Reset cache operation state.
         */
        public fun resetCacheOperation() {
            _cacheOperation.value = CacheOperationState.Idle
        }

        /**
         * Toggle download speed limiting.
         */
        public fun updateLimitDownloadSpeed(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateLimitDownloadSpeed(enabled)
            }
        }

        /**
         * Update max download speed in KB/s.
         */
        public fun updateMaxDownloadSpeed(speedKb: Int) {
            viewModelScope.launch {
                settingsRepository.updateMaxDownloadSpeed(speedKb)
            }
        }

        /**
         * Update max concurrent downloads.
         */
        public fun updateMaxConcurrentDownloads(count: Int) {
            viewModelScope.launch {
                settingsRepository.updateMaxConcurrentDownloads(count)
            }
        }

        /**
         * Resets all per-book custom seek settings to global defaults.
         */
        public fun resetAllBookSettings() {
            viewModelScope.launch {
                updateBookSettingsUseCase.resetAll()
            }
        }

        /**
         * Normalizes all chapter titles (e.g. "Chapter 1").
         */
        public fun normalizeAllChapters() {
            viewModelScope.launch {
                booksRepository.normalizeAllChapters()
            }
        }
    }

private fun resolveProductivePeriod(books: List<com.jabook.app.jabook.compose.domain.model.Book>): ProductivePeriod {
    val hour =
        books
            .mapNotNull { it.lastPlayedDate }
            .maxOrNull()
            ?.let {
                java.time.Instant
                    .ofEpochMilli(it)
                    .atZone(java.time.ZoneId.systemDefault())
                    .hour
            }
            ?: return ProductivePeriod.UNKNOWN
    return when (hour) {
        in 5..11 -> ProductivePeriod.MORNING
        in 12..16 -> ProductivePeriod.DAY
        in 17..22 -> ProductivePeriod.EVENING
        else -> ProductivePeriod.NIGHT
    }
}

private fun resolveYearStartEpochMs(): Long =
    java.time.LocalDate
        .now()
        .withDayOfYear(1)
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/**
 * UI state for backup/restore operations.
 */
public sealed class BackupUiState {
    public data object Idle : BackupUiState()

    public data object Exporting : BackupUiState()

    public data class ExportReady(
        val uri: Uri,
    ) : BackupUiState()

    public data object Importing : BackupUiState()

    public data class ImportComplete(
        val stats: ImportStats,
    ) : BackupUiState()

    public data class Error(
        val message: String,
    ) : BackupUiState()
}

/**
 * UI state for cache operations.
 */
public sealed class CacheOperationState {
    public data object Idle : CacheOperationState()

    public data object Loading : CacheOperationState()

    public data object Clearing : CacheOperationState()

    public data object Success : CacheOperationState()

    public data class Error(
        val message: String,
    ) : CacheOperationState()
}
