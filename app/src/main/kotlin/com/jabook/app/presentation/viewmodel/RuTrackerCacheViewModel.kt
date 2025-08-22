package com.jabook.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.cache.CacheKey
import com.jabook.app.core.cache.CacheStatistics
import com.jabook.app.core.cache.RuTrackerCacheManager
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for cache management
 */
data class CacheManagementUiState(
    val isLoading: Boolean = false,
    val statistics: CacheStatistics = CacheStatistics(0, 0, 0, 0, 0, 0, 0, 0),
    val hitRate: Float = 0f,
    val efficiencyMetrics: Map<String, Float> = emptyMap(),
    val cacheKeys: List<String> = emptyList(),
    val selectedNamespace: String = "rutracker",
    val availableNamespaces: List<String> = listOf("rutracker", "search", "categories", "torrents"),
    val isRefreshing: Boolean = false,
    val userMessage: String? = null,
    val cacheSize: Long = 0,
    val config: com.jabook.app.core.cache.CacheConfig =
        com.jabook.app.core.cache
            .CacheConfig(),
)

/**
 * ViewModel for managing RuTracker cache
 */
@HiltViewModel
class RuTrackerCacheViewModel
    @Inject
    constructor(
        private val cacheManager: RuTrackerCacheManager,
        private val debugLogger: IDebugLogger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CacheManagementUiState())
        val uiState: StateFlow<CacheManagementUiState> = _uiState.asStateFlow()

        init {
            observeCacheStatistics()
            observeHitRate()
            observeEfficiencyMetrics()
            loadCacheSize()
        }

        /**
         * Observe cache statistics
         */
        private fun observeCacheStatistics() {
            cacheManager.statistics
                .onEach { stats ->
                    _uiState.update { current ->
                        current.copy(
                            statistics = stats,
                            isRefreshing = false,
                        )
                    }
                }.launchIn(viewModelScope)
        }

        /**
         * Observe cache hit rate
         */
        private fun observeHitRate() {
            cacheManager
                .getHitRate()
                .onEach { hitRate ->
                    _uiState.update { current ->
                        current.copy(
                            hitRate = hitRate,
                        )
                    }
                }.launchIn(viewModelScope)
        }

        /**
         * Observe cache efficiency metrics
         */
        private fun observeEfficiencyMetrics() {
            cacheManager
                .getEfficiencyMetrics()
                .onEach { metrics ->
                    _uiState.update { current ->
                        current.copy(
                            efficiencyMetrics = metrics,
                        )
                    }
                }.launchIn(viewModelScope)
        }

        /**
         * Load cache size
         */
        private fun loadCacheSize() {
            viewModelScope.launch {
                try {
                    val size = cacheManager.getSize()
                    _uiState.update { it.copy(cacheSize = size) }
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerCacheViewModel: Error loading cache size", e)
                }
            }
        }

        /**
         * Load cache keys for selected namespace
         */
        fun loadCacheKeys() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                try {
                    val namespace = _uiState.value.selectedNamespace
                    val keys = cacheManager.getKeys(namespace)

                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            cacheKeys = keys,
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            userMessage = "Ошибка при загрузке ключей кэша",
                        )
                    }

                    debugLogger.logError("RuTrackerCacheViewModel: Error loading cache keys", e)
                }
            }
        }

        /**
         * Clear all cache
         */
        fun clearAllCache() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                try {
                    val result = cacheManager.clear()
                    if (result.isSuccess) {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                userMessage = "Кэш полностью очищен",
                                cacheKeys = emptyList(),
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                userMessage = "Ошибка при очистке кэша: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }

                    // Clear user message after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(userMessage = null) }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            userMessage = "Ошибка при очистке кэша",
                        )
                    }

                    debugLogger.logError("RuTrackerCacheViewModel: Error clearing cache", e)
                }
            }
        }

        /**
         * Clear cache by namespace
         */
        fun clearNamespaceCache() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                try {
                    val namespace = _uiState.value.selectedNamespace
                    val result = cacheManager.clearNamespace(namespace)

                    if (result.isSuccess) {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                userMessage = "Кэш для пространства '$namespace' очищен",
                                cacheKeys = emptyList(),
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                userMessage = "Ошибка при очистке кэша: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }

                    // Clear user message after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(userMessage = null) }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            userMessage = "Ошибка при очистке кэша",
                        )
                    }

                    debugLogger.logError("RuTrackerCacheViewModel: Error clearing namespace cache", e)
                }
            }
        }

        /**
         * Force cleanup of expired entries
         */
        fun forceCleanup() {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true) }

                try {
                    val result = cacheManager.forceCleanup()
                    if (result.isSuccess) {
                        val cleanedCount = result.getOrNull() ?: 0
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                userMessage = "Очищено $cleanedCount устаревших записей",
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                userMessage = "Ошибка при очистке: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }

                    // Clear user message after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(userMessage = null) }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            userMessage = "Ошибка при очистке",
                        )
                    }

                    debugLogger.logError("RuTrackerCacheViewModel: Error forcing cleanup", e)
                }
            }
        }

        /**
         * Remove specific cache entry
         */
        fun removeCacheEntry(key: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }

                try {
                    val namespace = _uiState.value.selectedNamespace
                    val cacheKey = CacheKey(namespace, key)
                    val result = cacheManager.remove(cacheKey)

                    if (result.isSuccess) {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                userMessage = "Запись кэша удалена",
                                cacheKeys = current.cacheKeys.filter { it != key },
                            )
                        }
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                userMessage = "Ошибка при удалении записи: ${result.exceptionOrNull()?.message}",
                            )
                        }
                    }

                    // Clear user message after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(userMessage = null) }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            userMessage = "Ошибка при удалении записи",
                        )
                    }

                    debugLogger.logError("RuTrackerCacheViewModel: Error removing cache entry", e)
                }
            }
        }

        /**
         * Change selected namespace
         */
        fun selectNamespace(namespace: String) {
            _uiState.update { current ->
                current.copy(
                    selectedNamespace = namespace,
                    cacheKeys = emptyList(),
                )
            }

            // Load keys for new namespace
            loadCacheKeys()
        }

        /**
         * Refresh cache data
         */
        fun refreshCacheData() {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true) }

                try {
                    loadCacheSize()
                    loadCacheKeys()

                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            userMessage = "Данные кэша обновлены",
                        )
                    }

                    // Clear user message after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(userMessage = null) }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            userMessage = "Ошибка при обновлении данных",
                        )
                    }

                    debugLogger.logError("RuTrackerCacheViewModel: Error refreshing cache data", e)
                }
            }
        }

        /**
         * Get formatted cache size
         */
        fun getFormattedCacheSize(): String {
            val size = _uiState.value.cacheSize
            return formatBytes(size)
        }

        /**
         * Get formatted memory size
         */
        fun getFormattedMemorySize(): String {
            val size = _uiState.value.statistics.memorySize
            return formatBytes(size)
        }

        /**
         * Get formatted disk size
         */
        fun getFormattedDiskSize(): String {
            val size = _uiState.value.statistics.diskSize
            return formatBytes(size)
        }

        /**
         * Format bytes to human readable format
         */
        private fun formatBytes(bytes: Long): String =
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }

        /**
         * Get hit rate percentage
         */
        fun getHitRatePercentage(): String {
            val hitRate = _uiState.value.hitRate
            return "${(hitRate * 100).toInt()}%"
        }

        /**
         * Get efficiency metric value
         */
        fun getEfficiencyMetricValue(metric: String): String {
            val value = _uiState.value.efficiencyMetrics[metric] ?: 0f
            return when (metric) {
                "hitRate", "memoryUtilization", "diskUtilization", "evictionRate" ->
                    "${(value * 100).toInt()}%"
                else ->
                    value.toString()
            }
        }

        /**
         * Clear user message
         */
        fun clearUserMessage() {
            _uiState.update { it.copy(userMessage = null) }
        }

        /**
         * Get cache configuration summary
         */
        fun getConfigSummary(): Map<String, String> {
            val config = _uiState.value.config
            return mapOf(
                "memoryMaxSize" to "${config.memoryMaxSize} записей",
                "diskMaxSize" to formatBytes(config.diskMaxSize),
                "defaultTTL" to "${config.defaultTTL / 1000} сек",
                "cleanupInterval" to "${config.cleanupInterval / 1000} сек",
                "compressionEnabled" to if (config.compressionEnabled) "Включено" else "Отключено",
                "encryptionEnabled" to if (config.encryptionEnabled) "Включено" else "Отключено",
            )
        }

        /**
         * Check if cache entry exists
         */
        suspend fun containsCacheEntry(key: String): Boolean =
            try {
                val namespace = _uiState.value.selectedNamespace
                val cacheKey = CacheKey(namespace, key)
                cacheManager.contains(cacheKey)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerCacheViewModel: Error checking cache entry", e)
                false
            }

        /**
         * Get cache entry as JSON string for debugging
         */
        suspend fun getCacheEntryDebug(key: String): String? =
            try {
                val namespace = _uiState.value.selectedNamespace
                val cacheKey = CacheKey(namespace, key)

                cacheManager.get(cacheKey) { json -> json }.getOrNull()
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerCacheViewModel: Error getting cache entry for debug", e)
                null
            }

        /**
         * Put test data in cache for debugging
         */
        suspend fun putTestData(
            key: String,
            data: String,
            ttl: Long = 60000,
        ): Boolean =
            try {
                val namespace = _uiState.value.selectedNamespace
                val cacheKey = CacheKey(namespace, key)
                val result = cacheManager.put(cacheKey, data, ttl) { it }

                if (result.isSuccess) {
                    loadCacheKeys()
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerCacheViewModel: Error putting test data", e)
                false
            }
    }
