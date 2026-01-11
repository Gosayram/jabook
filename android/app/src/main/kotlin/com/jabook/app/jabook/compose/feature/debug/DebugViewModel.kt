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

package com.jabook.app.jabook.compose.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.core.util.StructuredLogger
import com.jabook.app.jabook.compose.data.debug.DebugLogService
import com.jabook.app.jabook.compose.data.network.MirrorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * ViewModel for Debug screen.
 * Manages log collection, mirror testing, and cache statistics.
 */
@HiltViewModel
public class DebugViewModel
    @Inject
    constructor(
        private val debugLogService: DebugLogService,
        private val mirrorManager: MirrorManager,
        private val authService: com.jabook.app.jabook.compose.data.auth.RutrackerAuthService,
        private val rutrackerRepository: com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository,
    ) : ViewModel() {
        private val logger = StructuredLogger("DebugViewModel")
        private val _uiState = MutableStateFlow<DebugUiState>(DebugUiState.Initial)
        public val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

        private val _logs = MutableStateFlow<String>("")
        public val logs: StateFlow<String> = _logs.asStateFlow()

        private val _authDebugInfo = MutableStateFlow<com.jabook.app.jabook.compose.data.debug.AuthDebugInfo?>(null)
        public val authDebugInfo: StateFlow<com.jabook.app.jabook.compose.data.debug.AuthDebugInfo?> = _authDebugInfo.asStateFlow()

        init {
            // Delay initialization until viewModelScope is fully ready
            // Post initialization to ensure ViewModel is fully constructed
            // Use Handler to post initialization to the next message loop iteration
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        // Now viewModelScope should be ready
                        loadLogs()
                        loadCacheStats()
                        refreshAuthDebugInfo()
                    } catch (e: Exception) {
                        android.util.Log.e("DebugViewModel", "Failed to initialize debug data", e)
                        _uiState.value = DebugUiState.Error("Initialization failed: ${e.message ?: "Unknown error"}")
                    }
                }
            } catch (e: Exception) {
                // Handle case when initialization fails
                android.util.Log.e("DebugViewModel", "Failed to post initialization", e)
                _uiState.value = DebugUiState.Error("Initialization failed: ${e.message ?: "Unknown error"}")
            }
        }

        public fun loadLogs() : Unit {
            try {
                viewModelScope.launch {
                    try {
                        logger.withOperation("loadLogs") { operationId ->
                            try {
                                _uiState.value = DebugUiState.Loading
                                public val logContent = debugLogService.collectLogs()
                                _logs.value = logContent
                                _uiState.value = DebugUiState.Success
                            } catch (e: Exception) {
                                logger.logError(operationId, "Failed to load logs", e)
                                _uiState.value = DebugUiState.Error(e.message ?: "Failed to load logs")
                            }
                        }
                    } catch (e: Exception) {
                        // Handle case when logger.withOperation itself throws an exception
                        android.util.Log.e("DebugViewModel", "Failed to initialize log loading operation", e)
                        _uiState.value = DebugUiState.Error("Failed to initialize log loading: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // Handle case when viewModelScope.launch fails (e.g., viewModelScope not ready)
                android.util.Log.e("DebugViewModel", "Failed to launch loadLogs coroutine", e)
                _uiState.value = DebugUiState.Error("Failed to start log loading: ${e.message}")
            }
        }

        /**
         * Shares logs via Android Share API.
         * Requires Activity context to start the share intent.
         *
         * @param activity Activity context for starting the share intent
         */
        public fun shareLogs(activity: android.app.Activity) {
            viewModelScope.launch {
                try {
                    _uiState.value = DebugUiState.Loading
                    debugLogService.shareLogs(activity)
                    _uiState.value = DebugUiState.Success
                } catch (e: Exception) {
                    _uiState.value = DebugUiState.Error(e.message ?: "Failed to share logs")
                }
            }
        }

        public fun clearOldLogFiles() : Unit {
            viewModelScope.launch {
                try {
                    debugLogService.clearOldLogFiles()
                } catch (e: Exception) {
                    android.util.Log.e("DebugViewModel", "Failed to clear old log files", e)
                }
            }
        }

        public fun testAllMirrors() : Unit {
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

        public fun refreshDebugData() : Unit {
            loadLogs()
            refreshAuthDebugInfo()
            loadCacheStats()
        }

        public fun refreshAuthDebugInfo() : Unit {
            try {
                viewModelScope.launch {
                    try {
                        logger.withOperation("refreshAuthDebugInfo") { operationId ->
                            try {
                                // Get fresh auth status by validating (with timeout protection)
                                public val isAuthenticated =
                                    try {
                                        kotlinx.coroutines.withTimeout(20000L) {
                                            authService.validateAuth(operationId)
                                        }
                                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                        android.util.Log.w("DebugViewModel", "Auth validation timed out")
                                        false
                                    }

                                // Check mirrors (already has timeout protection)
                                public val connectivity = checkAllMirrors()

                                // Get last auth error from service
                                public val lastError = authService.lastAuthError

                                // Create debug info with fresh validation results
                                public val info =
                                    com.jabook.app.jabook.compose.data.debug.AuthDebugInfo(
                                        isAuthenticated = isAuthenticated,
                                        lastAuthAttempt = System.currentTimeMillis(),
                                        lastAuthError = lastError,
                                        mirrorConnectivity = connectivity,
                                        validationResults =
                                            com.jabook.app.jabook.compose.data.debug.ValidationResults(
                                                profilePageCheck = isAuthenticated,
                                                searchPageCheck = isAuthenticated,
                                                indexPageCheck = connectivity.isNotEmpty() && connectivity.values.any { it },
                                                lastValidation = System.currentTimeMillis(),
                                            ),
                                    )
                                _authDebugInfo.value = info
                                logger.logSuccess(operationId, "Auth debug info refreshed")
                            } catch (e: Exception) {
                                // Handle errors gracefully - update authDebugInfo with error state
                                // Use WARNING for individual failures, not ERROR
                                android.util.Log.w("DebugViewModel", "Auth debug info refresh incomplete: ${e.message}")
                                logger.logError(operationId, "Auth debug info refresh incomplete", e)
                                public val errorInfo =
                                    com.jabook.app.jabook.compose.data.debug.AuthDebugInfo(
                                        isAuthenticated = false,
                                        lastAuthAttempt = System.currentTimeMillis(),
                                        lastAuthError = e.message ?: "Unknown error",
                                        mirrorConnectivity = emptyMap(),
                                        validationResults = null,
                                    )
                                _authDebugInfo.value = errorInfo
                            }
                        }
                    } catch (e: Exception) {
                        // Handle case when logger.withOperation itself throws an exception
                        android.util.Log.e("DebugViewModel", "Failed to initialize auth debug info operation", e)
                        public val errorInfo =
                            com.jabook.app.jabook.compose.data.debug.AuthDebugInfo(
                                isAuthenticated = false,
                                lastAuthAttempt = System.currentTimeMillis(),
                                lastAuthError = "Failed to initialize: ${e.message}",
                                mirrorConnectivity = emptyMap(),
                                validationResults = null,
                            )
                        _authDebugInfo.value = errorInfo
                    }
                }
            } catch (e: Exception) {
                // Handle case when viewModelScope.launch fails (e.g., viewModelScope not ready)
                android.util.Log.e("DebugViewModel", "Failed to launch refreshAuthDebugInfo coroutine", e)
                public val errorInfo =
                    com.jabook.app.jabook.compose.data.debug.AuthDebugInfo(
                        isAuthenticated = false,
                        lastAuthAttempt = System.currentTimeMillis(),
                        lastAuthError = "Failed to start: ${e.message}",
                        mirrorConnectivity = emptyMap(),
                        validationResults = null,
                    )
                _authDebugInfo.value = errorInfo
            }
        }

        private suspend fun checkAllMirrors(): Map<String, Boolean> =
            try {
                // Add overall timeout to prevent hanging (max 20 seconds for all mirrors)
                kotlinx.coroutines.withTimeout(20000L) {
                    // Get mirrors safely - use first() to wait for Flow to emit value
                    public val mirrors =
                        try {
                            mirrorManager.availableMirrors.first()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Re-throw cancellation to propagate timeout
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("DebugViewModel", "Failed to get available mirrors from Flow, using defaults", e)
                            com.jabook.app.jabook.compose.data.network.MirrorManager.DEFAULT_MIRRORS
                        }

                    if (mirrors.isEmpty()) {
                        android.util.Log.w("DebugViewModel", "No mirrors configured for health check")
                        emptyMap()
                    } else {
                        // Check mirrors in parallel for better performance
                        public val results =
                            coroutineScope {
                                mirrors
                                    .map { mirror ->
                                        async {
                                            try {
                                                public val isHealthy = mirrorManager.checkMirrorHealth(mirror)
                                                mirror to isHealthy
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                // Re-throw cancellation to propagate timeout
                                                throw e
                                            } catch (e: Exception) {
                                                // Individual mirror check failed - this is normal, not an error
                                                android.util.Log.i(
                                                    "DebugViewModel",
                                                    "Mirror $mirror unavailable (expected behavior): ${e.message}",
                                                )
                                                mirror to false
                                            }
                                        }
                                    }.awaitAll()
                                    .toMap()
                            }

                        // Log summary of mirror health check
                        public val availableCount = results.values.count { it }
                        public val totalCount = results.size
                        if (availableCount == 0) {
                            android.util.Log.w("DebugViewModel", "All mirrors unavailable ($totalCount checked)")
                        } else {
                            android.util.Log.i("DebugViewModel", "Mirror health check complete: $availableCount/$totalCount available")
                        }

                        results
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.i("DebugViewModel", "Mirror health check timed out after 20s (some mirrors slow to respond)")
                emptyMap()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation to allow proper cleanup
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DebugViewModel", "Unexpected error during mirror check", e)
                emptyMap()
            }

        private val _cacheStats = MutableStateFlow<com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache.CacheStatistics?>(null)
        public val cacheStats: StateFlow<com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache.CacheStatistics?> =
            _cacheStats
                .asStateFlow()

        public fun loadCacheStats() : Unit {
            try {
                viewModelScope.launch {
                    try {
                        public val stats = rutrackerRepository.getCacheStatistics()
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
            } catch (e: Exception) {
                // Handle case when viewModelScope.launch fails (e.g., viewModelScope not ready)
                android.util.Log.e("DebugViewModel", "Failed to launch loadCacheStats coroutine", e)
                _cacheStats.value = null
            }
        }

        public fun clearCache() : Unit {
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
public sealed class DebugUiState {
    data object Initial : DebugUiState()

    data object Loading : DebugUiState()

    data object Success : DebugUiState()

    public data class Error(
        public val message: String,
    ) : DebugUiState()
}
