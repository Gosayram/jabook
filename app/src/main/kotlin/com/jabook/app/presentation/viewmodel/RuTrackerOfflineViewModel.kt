package com.jabook.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.network.models.RuTrackerCategory
import com.jabook.app.core.network.models.RuTrackerSearchResult
import com.jabook.app.core.network.models.RuTrackerTorrentDetails
import com.jabook.app.core.offline.RuTrackerOfflineManager
import com.jabook.app.core.offline.RuTrackerOfflineManager.OfflineDataStatistics
import com.jabook.app.core.offline.RuTrackerOfflineManager.OfflineSearchAnalytics
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing RuTracker offline functionality
 */
@HiltViewModel
class RuTrackerOfflineViewModel
    @Inject
    constructor(
        private val offlineManager: RuTrackerOfflineManager,
        private val debugLogger: IDebugLogger,
    ) : ViewModel() {
        // UI state
        private val _uiState = MutableStateFlow(RuTrackerOfflineUiState())
        val uiState: StateFlow<RuTrackerOfflineUiState> = _uiState.asStateFlow()

        // Offline mode state
        val isOfflineMode = offlineManager.isOfflineMode

        // Offline data status
        val offlineDataStatus = offlineManager.offlineDataStatus

        // Offline search analytics
        val offlineSearchAnalytics = offlineManager.offlineSearchAnalytics

        // Search results
        private val _searchResults = MutableStateFlow<List<RuTrackerSearchResult>>(emptyList())
        val searchResults: StateFlow<List<RuTrackerSearchResult>> = _searchResults.asStateFlow()

        // Categories
        private val _categories = MutableStateFlow<List<RuTrackerCategory>>(emptyList())
        val categories: StateFlow<List<RuTrackerCategory>> = _categories.asStateFlow()

        // Torrent details
        private val _torrentDetails = MutableStateFlow<RuTrackerTorrentDetails?>(null)
        val torrentDetails: StateFlow<RuTrackerTorrentDetails?> = _torrentDetails.asStateFlow()

        // Loading states
        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        // Error messages
        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        init {
            // Load offline data on initialization
            loadOfflineData()

            // Observe offline mode changes
            viewModelScope.launch {
                isOfflineMode.collect { isOffline ->
                    _uiState.update { it.copy(isOfflineMode = isOffline) }
                    debugLogger.logInfo("RuTrackerOfflineViewModel: Offline mode changed to $isOffline")
                }
            }

            // Observe offline data status changes
            viewModelScope.launch {
                offlineDataStatus.collect { status ->
                    _uiState.update { it.copy(offlineDataStatus = status) }
                }
            }

            // Observe offline search analytics
            viewModelScope.launch {
                offlineSearchAnalytics.collect { analytics ->
                    _uiState.update { it.copy(searchAnalytics = analytics) }
                }
            }
        }

        /**
         * Toggle offline mode
         */
        fun toggleOfflineMode() {
            val currentMode = isOfflineMode.value
            offlineManager.setOfflineMode(!currentMode)

            if (!currentMode) {
                // When enabling offline mode, refresh data
                loadOfflineData()
            }
        }

        /**
         * Load offline data
         */
        fun loadOfflineData() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null

                try {
                    val result = offlineManager.loadOfflineData()
                    if (result.isFailure) {
                        _errorMessage.value = "Failed to load offline data: ${result.exceptionOrNull()?.message}"
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error loading offline data: ${e.message}"
                    debugLogger.logError("RuTrackerOfflineViewModel: Error loading offline data", e)
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Clear all offline data
         */
        fun clearOfflineData() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null

                try {
                    val result = offlineManager.clearOfflineData()
                    if (result.isFailure) {
                        _errorMessage.value = "Failed to clear offline data: ${result.exceptionOrNull()?.message}"
                    } else {
                        // Clear local state
                        _searchResults.value = emptyList()
                        _categories.value = emptyList()
                        _torrentDetails.value = null
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error clearing offline data: ${e.message}"
                    debugLogger.logError("RuTrackerOfflineViewModel: Error clearing offline data", e)
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Search offline content
         */
        fun searchOffline(
            query: String,
            categoryId: Int? = null,
        ) {
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return
            }

            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null

                try {
                    val result = offlineManager.searchOffline(query, categoryId)
                    if (result.isSuccess) {
                        _searchResults.value = result.getOrNull() ?: emptyList()
                    } else {
                        _errorMessage.value = "Search failed: ${result.exceptionOrNull()?.message}"
                        _searchResults.value = emptyList()
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error searching offline: ${e.message}"
                    debugLogger.logError("RuTrackerOfflineViewModel: Error searching offline", e)
                    _searchResults.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Get categories offline
         */
        fun getCategoriesOffline() {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null

                try {
                    val result = offlineManager.getCategoriesOffline()
                    if (result.isSuccess) {
                        _categories.value = result.getOrNull() ?: emptyList()
                    } else {
                        _errorMessage.value = "Failed to get categories: ${result.exceptionOrNull()?.message}"
                        _categories.value = emptyList()
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error getting categories: ${e.message}"
                    debugLogger.logError("RuTrackerOfflineViewModel: Error getting categories", e)
                    _categories.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Get torrent details offline
         */
        fun getTorrentDetailsOffline(topicId: String) {
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null

                try {
                    val result = offlineManager.getTorrentDetailsOffline(topicId)
                    if (result.isSuccess) {
                        _torrentDetails.value = result.getOrNull()
                    } else {
                        _errorMessage.value = "Failed to get torrent details: ${result.exceptionOrNull()?.message}"
                        _torrentDetails.value = null
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Error getting torrent details: ${e.message}"
                    debugLogger.logError("RuTrackerOfflineViewModel: Error getting torrent details", e)
                    _torrentDetails.value = null
                } finally {
                    _isLoading.value = false
                }
            }
        }

        /**
         * Get offline data statistics
         */
        fun getOfflineDataStatistics(): OfflineDataStatistics = offlineManager.getOfflineDataStatistics()

        /**
         * Clear error message
         */
        fun clearErrorMessage() {
            _errorMessage.value = null
        }

        /**
         * Clear search results
         */
        fun clearSearchResults() {
            _searchResults.value = emptyList()
        }

        /**
         * Clear torrent details
         */
        fun clearTorrentDetails() {
            _torrentDetails.value = null
        }

        /**
         * Refresh offline data
         */
        fun refreshOfflineData() {
            clearOfflineData()
            loadOfflineData()
        }

        /**
         * Check if offline mode is available
         */
        fun isOfflineModeAvailable(): Boolean {
            val status = offlineDataStatus.value
            return status.hasSearchResults || status.hasCategories || status.hasDetails
        }

        /**
         * Get offline data summary
         */
        fun getOfflineDataSummary(): String {
            val stats = getOfflineDataStatistics()
            val status = offlineDataStatus.value

            return buildString {
                append("Offline Data Summary:\n")
                append("• Search Results: ${stats.searchResultsCount}\n")
                append("• Categories: ${stats.categoriesCount}\n")
                append("• Torrent Details: ${stats.detailsCount}\n")
                append("• Search Index: ${stats.searchIndexSize}\n")
                append("• Cache Size: ${formatFileSize(stats.totalCacheSize)}\n")
                append("• Last Updated: ${formatTimestamp(stats.lastUpdated)}\n")
                append("• Offline Mode: ${if (isOfflineMode.value) "Enabled" else "Disabled"}\n")
                append("• Data Available: ${if (isOfflineModeAvailable()) "Yes" else "No"}")
            }
        }

        /**
         * Format file size for display
         */
        private fun formatFileSize(bytes: Long): String =
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }

        /**
         * Format timestamp for display
         */
        private fun formatTimestamp(timestamp: Long): String =
            if (timestamp > 0) {
                val now = System.currentTimeMillis()
                val diff = now - timestamp

                when {
                    diff < 60 * 1000 -> "Just now"
                    diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
                    diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
                    else -> "${diff / (24 * 60 * 60 * 1000)} days ago"
                }
            } else {
                "Never"
            }

        /**
         * UI state data class
         */
        data class RuTrackerOfflineUiState(
            val isOfflineMode: Boolean = false,
            val offlineDataStatus: RuTrackerOfflineManager.OfflineDataStatus = RuTrackerOfflineManager.OfflineDataStatus(),
            val searchAnalytics: OfflineSearchAnalytics = OfflineSearchAnalytics(),
            val isLoading: Boolean = false,
            val errorMessage: String? = null,
        )
    }
