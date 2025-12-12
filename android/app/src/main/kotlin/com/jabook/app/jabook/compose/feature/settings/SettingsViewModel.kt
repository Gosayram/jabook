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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.ThemeMode
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    }
