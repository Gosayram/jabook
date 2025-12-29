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

package com.jabook.app.jabook.compose.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.debug.DebugLogService
import com.jabook.app.jabook.compose.data.network.MirrorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Debug screen.
 * Manages log collection, mirror testing, and cache statistics.
 */
@HiltViewModel
class DebugViewModel
    @Inject
    constructor(
        private val debugLogService: DebugLogService,
        private val mirrorManager: MirrorManager,
        private val authService: com.jabook.app.jabook.compose.data.auth.RutrackerAuthService,
        private val rutrackerRepository: com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<DebugUiState>(DebugUiState.Initial)
        val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

        private val _logs = MutableStateFlow<String>("")
        val logs: StateFlow<String> = _logs.asStateFlow()

        private val _authDebugInfo = MutableStateFlow<com.jabook.app.jabook.compose.data.debug.AuthDebugInfo?>(null)
        val authDebugInfo: StateFlow<com.jabook.app.jabook.compose.data.debug.AuthDebugInfo?> = _authDebugInfo.asStateFlow()

        init {
            loadLogs()
            loadCacheStats()
        }

        fun loadLogs() {
            viewModelScope.launch {
                try {
                    _uiState.value = DebugUiState.Loading
                    val logContent = debugLogService.collectLogs()
                    _logs.value = logContent
                    _uiState.value = DebugUiState.Success
                } catch (e: Exception) {
                    _uiState.value = DebugUiState.Error(e.message ?: "Failed to load logs")
                }
            }
        }

        fun shareLogs() {
            viewModelScope.launch {
                try {
                    _uiState.value = DebugUiState.Loading
                    debugLogService.shareLogs()
                    _uiState.value = DebugUiState.Success
                } catch (e: Exception) {
                    _uiState.value = DebugUiState.Error(e.message ?: "Failed to share logs")
                }
            }
        }

        fun clearOldLogFiles() {
            viewModelScope.launch {
                try {
                    debugLogService.clearOldLogFiles()
                } catch (e: Exception) {
                    android.util.Log.e("DebugViewModel", "Failed to clear old log files", e)
                }
            }
        }

        fun testAllMirrors() {
            viewModelScope.launch {
                try {
                    _uiState.value = DebugUiState.Loading
                    refreshAuthDebugInfo()
                    _uiState.value = DebugUiState.Success
                } catch (e: Exception) {
                    _uiState.value = DebugUiState.Error(e.message ?: "Failed to test mirrors")
                }
            }
        }

        fun refreshDebugData() {
            loadLogs()
            refreshAuthDebugInfo()
            loadCacheStats()
        }

        fun refreshAuthDebugInfo() {
            viewModelScope.launch {
                try {
                    // Get fresh auth status by validating
                    val isAuthenticated = authService.validateAuth("debug_refresh")
                    val connectivity = checkAllMirrors()

                    // Get last auth error from service
                    val lastError = authService.lastAuthError

                    // Create debug info with fresh validation results
                    val info =
                        com.jabook.app.jabook.compose.data.debug.AuthDebugInfo(
                            isAuthenticated = isAuthenticated,
                            lastAuthAttempt = System.currentTimeMillis(),
                            lastAuthError = lastError,
                            mirrorConnectivity = connectivity,
                            validationResults =
                                com.jabook.app.jabook.compose.data.debug.ValidationResults(
                                    profilePageCheck = isAuthenticated,
                                    searchPageCheck = isAuthenticated,
                                    indexPageCheck = connectivity.values.any { it },
                                    lastValidation = System.currentTimeMillis(),
                                ),
                        )
                    _authDebugInfo.value = info
                } catch (e: Exception) {
                    // Handle errors gracefully
                    android.util.Log.e("DebugViewModel", "Failed to refresh auth debug info", e)
                }
            }
        }

        private suspend fun checkAllMirrors(): Map<String, Boolean> {
            val mirrors = mirrorManager.availableMirrors.value
            return mirrors.associateWith { mirror ->
                mirrorManager.checkMirrorHealth(mirror)
            }
        }

        private val _cacheStats = MutableStateFlow<com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache.CacheStatistics?>(null)
        val cacheStats: StateFlow<com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache.CacheStatistics?> =
            _cacheStats
                .asStateFlow()

        fun loadCacheStats() {
            viewModelScope.launch {
                try {
                    val stats = rutrackerRepository.getCacheStatistics()
                    _cacheStats.value = stats
                } catch (e: NullPointerException) {
                    android.util.Log.e(
                        "DebugViewModel",
                        "NullPointerException while loading cache stats - repository or cache may not be initialized",
                        e,
                    )
                    _cacheStats.value = null
                } catch (e: Exception) {
                    android.util.Log.e("DebugViewModel", "Failed to load cache stats", e)
                    _cacheStats.value = null
                }
            }
        }

        fun clearCache() {
            viewModelScope.launch {
                try {
                    rutrackerRepository.clearSearchCache()
                    loadCacheStats()
                } catch (e: Exception) {
                    android.util.Log.e("DebugViewModel", "Failed to clear cache", e)
                }
            }
        }
    }

/**
 * UI state for Debug screen.
 */
sealed class DebugUiState {
    data object Initial : DebugUiState()

    data object Loading : DebugUiState()

    data object Success : DebugUiState()

    data class Error(
        val message: String,
    ) : DebugUiState()
}
