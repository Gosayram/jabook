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
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.debug.DebugAudioFocusSimulator
import com.jabook.app.jabook.compose.data.debug.DebugLogService
import com.jabook.app.jabook.compose.data.debug.DebugNetworkOverrideMode
import com.jabook.app.jabook.compose.data.debug.DebugRuntimeOverrides
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
        private val debugRuntimeOverrides: DebugRuntimeOverrides,
        private val debugAudioFocusSimulator: DebugAudioFocusSimulator,
        private val networkMonitor: NetworkMonitor,
        private val database: JabookDatabase,
        private val loggerFactory: LoggerFactory,
    ) : ViewModel() {
        private val logger = loggerFactory.get("DebugViewModel")
        private val _uiState = MutableStateFlow<DebugUiState>(DebugUiState.Initial)
        public val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

        private val _logs = MutableStateFlow<String>("")
        public val logs: StateFlow<String> = _logs.asStateFlow()

        private val _authDebugInfo = MutableStateFlow<com.jabook.app.jabook.compose.data.debug.AuthDebugInfo?>(null)
        public val authDebugInfo: StateFlow<com.jabook.app.jabook.compose.data.debug.AuthDebugInfo?> =
            _authDebugInfo
                .asStateFlow()
        private val _networkOverrideMode = MutableStateFlow(debugRuntimeOverrides.networkOverrideMode.value)
        public val networkOverrideMode: StateFlow<DebugNetworkOverrideMode> = _networkOverrideMode.asStateFlow()

        private val _effectiveNetworkType = MutableStateFlow(NetworkType.UNKNOWN)
        public val effectiveNetworkType: StateFlow<NetworkType> = _effectiveNetworkType.asStateFlow()

        private val _forceLowStorage = MutableStateFlow(debugRuntimeOverrides.forceLowStorage.value)
        public val forceLowStorage: StateFlow<Boolean> = _forceLowStorage.asStateFlow()

        private val _dbInspectorSnapshot = MutableStateFlow(DebugDbInspectorSnapshot())
        public val dbInspectorSnapshot: StateFlow<DebugDbInspectorSnapshot> = _dbInspectorSnapshot.asStateFlow()

        private val _recentSearchPreview = MutableStateFlow<List<String>>(emptyList())
        public val recentSearchPreview: StateFlow<List<String>> = _recentSearchPreview.asStateFlow()

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
                        observeDebugRuntimeState()
                        observeRecentSearchHistory()
                        refreshDbInspector()
                    } catch (e: Exception) {
                        logger.e({ "Failed to initialize debug data" }, e)
                        _uiState.value = DebugUiState.Error("Initialization failed: ${e.message ?: "Unknown error"}")
                    }
                }
            } catch (e: Exception) {
                // Handle case when initialization fails
                logger.e({ "Failed to post initialization" }, e)
                _uiState.value = DebugUiState.Error("Initialization failed: ${e.message ?: "Unknown error"}")
            }
        }

        public fun loadLogs() {
            try {
                viewModelScope.launch {
                    try {
                        // Standard logging
                        val operationId = "loadLogs_${System.currentTimeMillis()}"
                        try {
                            _uiState.value = DebugUiState.Loading
                            val logContent = debugLogService.collectLogs()
                            _logs.value = logContent
                            _uiState.value = DebugUiState.Success
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to load logs (Op: $operationId)" }
                            _uiState.value = DebugUiState.Error(e.message ?: "Failed to load logs")
                        }
                    } catch (e: Exception) {
                        // Handle case when logger.withOperation itself throws an exception
                        logger.e({ "Failed to initialize log loading operation" }, e)
                        _uiState.value = DebugUiState.Error("Failed to initialize log loading: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // Handle case when viewModelScope.launch fails (e.g., viewModelScope not ready)
                logger.e({ "Failed to launch loadLogs coroutine" }, e)
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

        public fun clearOldLogFiles() {
            viewModelScope.launch {
                try {
                    debugLogService.clearOldLogFiles()
                } catch (e: Exception) {
                    logger.e({ "Failed to clear old log files" }, e)
                }
            }
        }

        public fun testAllMirrors() {
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

        public fun refreshDebugData() {
            loadLogs()
            refreshAuthDebugInfo()
            loadCacheStats()
            refreshDbInspector()
        }

        public fun setNetworkOverrideMode(mode: DebugNetworkOverrideMode) {
            debugRuntimeOverrides.setNetworkOverrideMode(mode)
        }

        public fun setForceLowStorage(enabled: Boolean) {
            debugRuntimeOverrides.setForceLowStorage(enabled)
        }

        public fun simulateAudioFocusDuck() {
            debugAudioFocusSimulator.simulateDuck()
        }

        public fun simulateAudioFocusLossTransient() {
            debugAudioFocusSimulator.simulateLossTransient()
        }

        public fun simulateAudioFocusGain() {
            debugAudioFocusSimulator.simulateGain()
        }

        public fun refreshAuthDebugInfo() {
            try {
                viewModelScope.launch {
                    try {
                        // Standard logging instead of StructuredLogger
                        val operationId = "refreshAuthDebugInfo_${System.currentTimeMillis()}"
                        try {
                            // Get fresh auth status by validating (with timeout protection)
                            val isAuthenticated =
                                try {
                                    kotlinx.coroutines.withTimeout(20000L) {
                                        authService.validateAuth(operationId)
                                    }
                                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                    logger.w { "Auth validation timed out" }
                                    false
                                }

                            // Check mirrors (already has timeout protection)
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
                                            indexPageCheck =
                                                connectivity.isNotEmpty() &&
                                                    connectivity.values.any {
                                                        it
                                                    },
                                            lastValidation = System.currentTimeMillis(),
                                        ),
                                )
                            _authDebugInfo.value = info
                            logger.i { "Auth debug info refreshed (Op: $operationId)" }
                        } catch (e: Exception) {
                            // Handle errors gracefully - update authDebugInfo with error state
                            // Use WARNING for individual failures, not ERROR
                            logger.w { "Auth debug info refresh incomplete: ${e.message} (Op: $operationId)" }
                            logger.e(e) { "Auth debug info refresh incomplete" }
                            val errorInfo =
                                com.jabook.app.jabook.compose.data.debug.AuthDebugInfo(
                                    isAuthenticated = false,
                                    lastAuthAttempt = System.currentTimeMillis(),
                                    lastAuthError = e.message ?: "Unknown error",
                                    mirrorConnectivity = emptyMap(),
                                    validationResults = null,
                                )
                            _authDebugInfo.value = errorInfo
                        }
                    } catch (e: Exception) {
                        // Handle case when logger.withOperation itself throws an exception
                        logger.e({ "Failed to initialize auth debug info operation" }, e)
                        val errorInfo =
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
                logger.e({ "Failed to launch refreshAuthDebugInfo coroutine" }, e)
                val errorInfo =
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
                    val mirrors =
                        try {
                            mirrorManager.availableMirrors.first()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Re-throw cancellation to propagate timeout
                            throw e
                        } catch (e: Exception) {
                            logger.e({ "Failed to get available mirrors from Flow, using defaults" }, e)
                            com.jabook.app.jabook.compose.data.network.MirrorManager.DEFAULT_MIRRORS
                        }

                    if (mirrors.isEmpty()) {
                        logger.w { "No mirrors configured for health check" }
                        emptyMap()
                    } else {
                        // Check mirrors in parallel for better performance
                        val results =
                            supervisorScope {
                                mirrors
                                    .map { mirror ->
                                        async {
                                            try {
                                                val isHealthy = mirrorManager.checkMirrorHealth(mirror)
                                                mirror to isHealthy
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                // Re-throw cancellation to propagate timeout
                                                throw e
                                            } catch (e: Exception) {
                                                // Individual mirror check failed - this is normal, not an error
                                                logger.i {
                                                    "Mirror $mirror unavailable (expected behavior): ${e.message}"
                                                }
                                                mirror to false
                                            }
                                        }
                                    }.awaitAll()
                                    .toMap()
                            }

                        // Log summary of mirror health check
                        val availableCount = results.values.count { it }
                        val totalCount = results.size
                        if (availableCount == 0) {
                            logger.w { "All mirrors unavailable ($totalCount checked)" }
                        } else {
                            logger.i { "Mirror health check complete: $availableCount/$totalCount available" }
                        }

                        results
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logger.i { "Mirror health check timed out after 20s (some mirrors slow to respond)" }
                emptyMap()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Re-throw cancellation to allow proper cleanup
                throw e
            } catch (e: Exception) {
                logger.e({ "Unexpected error during mirror check" }, e)
                emptyMap()
            }

        private val _cacheStats =
            MutableStateFlow<com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache.CacheStatistics?>(null)
        public val cacheStats: StateFlow<com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache.CacheStatistics?> =
            _cacheStats
                .asStateFlow()

        public fun loadCacheStats() {
            try {
                viewModelScope.launch {
                    try {
                        val stats = rutrackerRepository.getCacheStatistics()
                        _cacheStats.value = stats
                    } catch (e: NullPointerException) {
                        logger.e(
                            {
                                "NullPointerException while loading cache stats - repository or cache may not be initialized"
                            },
                            e,
                        )
                        _cacheStats.value = null
                    } catch (e: Exception) {
                        logger.e({ "Failed to load cache stats" }, e)
                        _cacheStats.value = null
                    }
                }
            } catch (e: Exception) {
                // Handle case when viewModelScope.launch fails (e.g., viewModelScope not ready)
                logger.e({ "Failed to launch loadCacheStats coroutine" }, e)
                _cacheStats.value = null
            }
        }

        public fun clearCache() {
            viewModelScope.launch {
                try {
                    rutrackerRepository.clearSearchCache()
                    loadCacheStats()
                } catch (e: Exception) {
                    logger.e({ "Failed to clear cache" }, e)
                }
            }
        }

        public fun refreshDbInspector() {
            viewModelScope.launch {
                try {
                    val booksCount = database.booksDao().getBookCount()
                    val favoritesCount = database.favoriteDao().getFavoritesCount()
                    val indexedTopicsCount = database.offlineSearchDao().getTopicCount()
                    val downloadHistoryCount = database.downloadHistoryDao().getCount()
                    _dbInspectorSnapshot.value =
                        DebugDbInspectorSnapshot(
                            booksCount = booksCount,
                            favoritesCount = favoritesCount,
                            indexedTopicsCount = indexedTopicsCount,
                            downloadHistoryCount = downloadHistoryCount,
                        )
                } catch (e: Exception) {
                    logger.e({ "Failed to refresh DB inspector snapshot" }, e)
                }
            }
        }

        private fun observeDebugRuntimeState() {
            viewModelScope.launch {
                debugRuntimeOverrides.networkOverrideMode.collect { mode ->
                    _networkOverrideMode.value = mode
                }
            }
            viewModelScope.launch {
                debugRuntimeOverrides.forceLowStorage.collect { forced ->
                    _forceLowStorage.value = forced
                }
            }
            viewModelScope.launch {
                networkMonitor.networkType.collect { type ->
                    _effectiveNetworkType.value = type
                }
            }
        }

        private fun observeRecentSearchHistory() {
            viewModelScope.launch {
                database
                    .searchHistoryDao()
                    .getRecentSearches(limit = 20)
                    .map { history -> history.map { it.query } }
                    .collect { queries ->
                        _recentSearchPreview.value = queries
                    }
            }
        }
    }

/**
 * UI state for Debug screen.
 */
public sealed class DebugUiState {
    public data object Initial : DebugUiState()

    public data object Loading : DebugUiState()

    public data object Success : DebugUiState()

    public data class Error(
        val message: String,
    ) : DebugUiState()
}

public data class DebugDbInspectorSnapshot(
    val booksCount: Int = 0,
    val favoritesCount: Int = 0,
    val indexedTopicsCount: Int = 0,
    val downloadHistoryCount: Int = 0,
)
