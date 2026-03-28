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

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.backup.BackupService
import com.jabook.app.jabook.compose.data.backup.ImportStats
import com.jabook.app.jabook.compose.data.cache.CacheManager
import com.jabook.app.jabook.compose.data.cache.CacheStatistics
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.preferences.UserPreferencesSerializer
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.compose.data.worker.LibraryScanWorker
import com.jabook.app.jabook.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
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
        private val torrentManager: TorrentManager,
        private val loggerFactory: LoggerFactory,
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
        public val scanProgress: StateFlow<ScanProgress> =
            booksRepository.getScanProgress().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ScanProgress.Idle,
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
                        .addTag("library_scan")
                        .build()
                workManager.enqueue(workRequest)
            }
        }

        public fun cancelScan() {
            workManager.cancelAllWorkByTag("library_scan")
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

        public fun updatePlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaybackSpeed(speed)
                // Also update in Proto DataStore
                settingsRepository.updatePlaybackSpeed(speed)
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

        public fun updateAudioSettings(
            rewindSeconds: Int? = null,
            forwardSeconds: Int? = null,
            resumeRewindSeconds: Int? = null,
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
        ) {
            viewModelScope.launch {
                settingsRepository.updateAudioSettings(
                    rewindSeconds = rewindSeconds,
                    forwardSeconds = forwardSeconds,
                    resumeRewindSeconds = resumeRewindSeconds,
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
                )
            }
        }

        public fun updateLanguage(languageCode: String) {
            viewModelScope.launch {
                settingsRepository.updateLanguage(languageCode)
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
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch {
                val isHealthy = mirrorManager.checkMirrorHealth(domain)
                onResult(isHealthy)
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
            } catch (e: Exception) {
                // Ignore parsing errors and return original
            }
            return uriString
        }

        public fun updateWifiOnly(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateWifiOnly(enabled)
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
                } catch (e: Exception) {
                    _backupState.value = BackupUiState.Error(e.message ?: "Export failed")
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
                } catch (e: Exception) {
                    _backupState.value = BackupUiState.Error(e.message ?: "Import failed: ${e.message}")
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
                } catch (e: Exception) {
                    _cacheOperation.value = CacheOperationState.Error(e.message ?: "Failed to load cache stats")
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
                        _cacheOperation.value = CacheOperationState.Error("Failed to clear cache")
                    }
                } catch (e: Exception) {
                    _cacheOperation.value = CacheOperationState.Error(e.message ?: "Failed to clear cache")
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
