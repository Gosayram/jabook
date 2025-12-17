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

package com.jabook.app.jabook.compose.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.backup.BackupService
import com.jabook.app.jabook.compose.data.backup.ImportStats
import com.jabook.app.jabook.compose.data.cache.CacheManager
import com.jabook.app.jabook.compose.data.cache.CacheStatistics
import com.jabook.app.jabook.compose.data.cache.CacheType
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.ThemeMode
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages both old preferences (UserPreferencesRepository) and new Proto DataStore settings.
 * Gradually migrating to Proto DataStore.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val userPreferencesRepository: UserPreferencesRepository, // Keep for migration
        private val authRepository: com.jabook.app.jabook.compose.domain.repository.AuthRepository,
        private val mirrorManager: MirrorManager,
        private val backupService: BackupService,
        private val cacheManager: CacheManager,
        private val updateBookSettingsUseCase: com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase,
    ) : ViewModel() {
        // Exposure of auth status for UI
        val authStatus =
            authRepository.authStatus.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = com.jabook.app.jabook.compose.domain.model.AuthStatus.Unauthenticated,
            )

        fun logout() {
            viewModelScope.launch {
                authRepository.logout()
            }
        }

        /**
         * Old user preferences - for backward compatibility.
         */
        val userPreferences =
            userPreferencesRepository.userData.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

        /**
         * New Proto DataStore settings.
         */
        val protoSettings: StateFlow<UserPreferences> =
            settingsRepository.userPreferences.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue =
                    com.jabook.app.jabook.compose.data.preferences
                        .UserPreferencesSerializer.defaultValue,
            )

        // ===== Old preferences API (kept for compatibility) =====

        fun updateTheme(theme: AppTheme) {
            viewModelScope.launch {
                userPreferencesRepository.setTheme(theme)
            }
        }

        fun updateSortOrder(sortOrder: BookSortOrder) {
            viewModelScope.launch {
                userPreferencesRepository.setSortOrder(sortOrder)
            }
        }

        fun updateAutoPlayNext(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setAutoPlayNext(enabled)
            }
        }

        fun updateFont(font: com.jabook.app.jabook.compose.data.model.AppFont) {
            viewModelScope.launch {
                userPreferencesRepository.setFont(font)
            }
        }

        fun updatePlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaybackSpeed(speed)
                // Also update in Proto DataStore
                settingsRepository.updatePlaybackSpeed(speed)
            }
        }

        // ===== New Proto DataStore API =====

        fun updateProtoTheme(themeMode: ThemeMode) {
            viewModelScope.launch {
                settingsRepository.updateThemeMode(themeMode)
            }
        }

        fun updateDynamicColors(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateDynamicColors(enabled)
            }
        }

        fun updateAudioSettings(
            rewindSeconds: Int? = null,
            forwardSeconds: Int? = null,
            volumeBoost: String? = null,
            drcLevel: String? = null,
            speechEnhancer: Boolean? = null,
            normalizeVolume: Boolean? = null,
        ) {
            viewModelScope.launch {
                settingsRepository.updateAudioSettings(
                    rewindSeconds = rewindSeconds,
                    forwardSeconds = forwardSeconds,
                    volumeBoost = volumeBoost,
                    drcLevel = drcLevel,
                    speechEnhancer = speechEnhancer,
                    normalizeVolume = normalizeVolume,
                )
            }
        }

        fun updateLanguage(languageCode: String) {
            viewModelScope.launch {
                settingsRepository.updateLanguage(languageCode)
            }
        }

        fun updateNotifications(
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

        fun resetToDefaults() {
            viewModelScope.launch {
                settingsRepository.resetToDefaults()
            }
        }

        // ===== Mirror Management =====

        /**
         * Current mirror domain from MirrorManager.
         */
        val currentMirror: StateFlow<String> = mirrorManager.currentMirror

        /**
         * Available mirrors (default + custom).
         */
        val availableMirrors: StateFlow<List<String>> = mirrorManager.availableMirrors

        /**
         * Update the selected mirror.
         */
        fun updateMirror(domain: String) {
            viewModelScope.launch {
                mirrorManager.setMirror(domain)
            }
        }

        /**
         * Check mirror health and invoke callback with result.
         */
        fun checkMirrorHealth(
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
        fun addCustomMirror(domain: String) {
            viewModelScope.launch {
                mirrorManager.addCustomMirror(domain)
            }
        }

        /**
         * Remove a custom mirror domain.
         */
        fun removeCustomMirror(domain: String) {
            viewModelScope.launch {
                mirrorManager.removeCustomMirror(domain)
            }
        }

        /**
         * Update auto-switch mirror setting.
         */
        fun updateAutoSwitch(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateAutoSwitchMirror(enabled)
            }
        }

        // ===== Download Settings =====

        fun updateDownloadPath(uriString: String) {
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

        fun updateWifiOnly(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateWifiOnly(enabled)
            }
        }

        // ===== Backup & Restore =====

        private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
        val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

        /**
         * Export app data to JSON backup file.
         */
        fun exportData() {
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
        fun importData(uri: Uri) {
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
        fun resetBackupState() {
            _backupState.value = BackupUiState.Idle
        }

        // ===== Cache Management =====

        private val _cacheStats = MutableStateFlow<CacheStatistics?>(null)
        val cacheStats: StateFlow<CacheStatistics?> = _cacheStats.asStateFlow()

        private val _cacheOperation = MutableStateFlow<CacheOperationState>(CacheOperationState.Idle)
        val cacheOperation: StateFlow<CacheOperationState> = _cacheOperation.asStateFlow()

        /**
         * Load cache statistics.
         */
        fun loadCacheStatistics() {
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
        fun clearCache(type: CacheType? = null) {
            viewModelScope.launch {
                try {
                    _cacheOperation.value = CacheOperationState.Clearing
                    val success =
                        if (type != null) {
                            cacheManager.clearCacheType(type)
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
        fun resetCacheOperation() {
            _cacheOperation.value = CacheOperationState.Idle
        }

        /**
         * Toggle download speed limiting.
         */
        fun updateLimitDownloadSpeed(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.updateLimitDownloadSpeed(enabled)
            }
        }

        /**
         * Update max download speed in KB/s.
         */
        fun updateMaxDownloadSpeed(speedKb: Int) {
            viewModelScope.launch {
                settingsRepository.updateMaxDownloadSpeed(speedKb)
            }
        }

        /**
         * Update max concurrent downloads.
         */
        fun updateMaxConcurrentDownloads(count: Int) {
            viewModelScope.launch {
                settingsRepository.updateMaxConcurrentDownloads(count)
            }
        }

        /**
         * Resets all per-book custom seek settings to global defaults.
         */
        fun resetAllBookSettings() {
            viewModelScope.launch {
                updateBookSettingsUseCase.resetAll()
            }
        }
    }

/**
 * UI state for backup/restore operations.
 */
sealed class BackupUiState {
    data object Idle : BackupUiState()

    data object Exporting : BackupUiState()

    data class ExportReady(
        val uri: Uri,
    ) : BackupUiState()

    data object Importing : BackupUiState()

    data class ImportComplete(
        val stats: ImportStats,
    ) : BackupUiState()

    data class Error(
        val message: String,
    ) : BackupUiState()
}

/**
 * UI state for cache operations.
 */
sealed class CacheOperationState {
    data object Idle : CacheOperationState()

    data object Loading : CacheOperationState()

    data object Clearing : CacheOperationState()

    data object Success : CacheOperationState()

    data class Error(
        val message: String,
    ) : CacheOperationState()
}
