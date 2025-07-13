package com.jabook.app.features.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentStatus
import com.jabook.app.core.domain.repository.TorrentRepository
import com.jabook.app.core.torrent.TorrentManager
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val torrentRepository: TorrentRepository,
    private val torrentManager: TorrentManager,
    private val debugLogger: IDebugLogger,
) : ViewModel() {

    private val _activeDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadProgress>> = _activeDownloads.asStateFlow()

    private val _completedDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
    val completedDownloads: StateFlow<List<DownloadProgress>> = _completedDownloads.asStateFlow()

    private val _failedDownloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
    val failedDownloads: StateFlow<List<DownloadProgress>> = _failedDownloads.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(DownloadsTab.Active)
    val selectedTab: StateFlow<DownloadsTab> = _selectedTab.asStateFlow()

    val uiState: StateFlow<DownloadsUiState> =
        combine(activeDownloads, completedDownloads, failedDownloads, isLoading, errorMessage, selectedTab) { states ->
            DownloadsUiState(
                activeDownloads = states[0] as List<DownloadProgress>,
                completedDownloads = states[1] as List<DownloadProgress>,
                failedDownloads = states[2] as List<DownloadProgress>,
                isLoading = states[3] as Boolean,
                errorMessage = states[4] as String?,
                selectedTab = states[5] as DownloadsTab,
            )
        }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = DownloadsUiState())

    init {
        loadDownloads()
        observeDownloadUpdates()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                debugLogger.logInfo("Loading downloads")

                torrentRepository
                    .getActiveDownloads()
                    .catch { error ->
                        debugLogger.logError("Failed to load downloads", error)
                        _errorMessage.value = "Failed to load downloads: ${error.message}"
                    }
                    .collect { downloads ->
                        categorizeDownloads(downloads)
                        _isLoading.value = false
                        debugLogger.logInfo("Loaded ${downloads.size} downloads")
                    }
            } catch (e: Exception) {
                debugLogger.logError("Error loading downloads", e)
                _errorMessage.value = "Error loading downloads: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun observeDownloadUpdates() {
        viewModelScope.launch {
            try {
                torrentManager.downloadStates
                    .catch { error -> debugLogger.logError("Failed to observe download updates", error) }
                    .collect { downloadStates ->
                        val downloads = downloadStates.values.toList()
                        categorizeDownloads(downloads)
                        debugLogger.logInfo("Download states updated: ${downloads.size} downloads")
                    }
            } catch (e: Exception) {
                debugLogger.logError("Error observing download updates", e)
            }
        }
    }

    private fun categorizeDownloads(downloads: List<DownloadProgress>) {
        val active = mutableListOf<DownloadProgress>()
        val completed = mutableListOf<DownloadProgress>()
        val failed = mutableListOf<DownloadProgress>()

        downloads.forEach { download ->
            when (download.status) {
                TorrentStatus.DOWNLOADING,
                TorrentStatus.SEEDING,
                TorrentStatus.PAUSED,
                TorrentStatus.PENDING -> {
                    active.add(download)
                }
                TorrentStatus.COMPLETED -> {
                    completed.add(download)
                }
                TorrentStatus.ERROR,
                TorrentStatus.STOPPED -> {
                    failed.add(download)
                }
                TorrentStatus.IDLE -> {
                    // Don't categorize idle downloads
                }
            }
        }

        _activeDownloads.value = active.sortedByDescending { it.progress }
        _completedDownloads.value = completed.sortedByDescending { it.progress }
        _failedDownloads.value = failed
    }

    fun selectTab(tab: DownloadsTab) {
        _selectedTab.value = tab
        debugLogger.logInfo("Selected downloads tab: ${tab.name}")
    }

    fun pauseDownload(torrentId: String) {
        viewModelScope.launch {
            try {
                debugLogger.logInfo("Pausing download: $torrentId")
                torrentRepository.pauseTorrent(torrentId)
            } catch (e: Exception) {
                debugLogger.logError("Failed to pause download", e)
                _errorMessage.value = "Failed to pause download: ${e.message}"
            }
        }
    }

    fun resumeDownload(torrentId: String) {
        viewModelScope.launch {
            try {
                debugLogger.logInfo("Resuming download: $torrentId")
                torrentRepository.resumeTorrent(torrentId)
            } catch (e: Exception) {
                debugLogger.logError("Failed to resume download", e)
                _errorMessage.value = "Failed to resume download: ${e.message}"
            }
        }
    }

    fun cancelDownload(torrentId: String) {
        viewModelScope.launch {
            try {
                debugLogger.logInfo("Cancelling download: $torrentId")
                torrentRepository.stopTorrent(torrentId)
            } catch (e: Exception) {
                debugLogger.logError("Failed to cancel download", e)
                _errorMessage.value = "Failed to cancel download: ${e.message}"
            }
        }
    }

    fun retryDownload(download: DownloadProgress) {
        viewModelScope.launch {
            try {
                debugLogger.logInfo("Retrying download: ${download.torrentId}")

                // For now, we'll just try to resume it
                // In a real implementation, we might need to restart from the beginning
                torrentRepository.resumeTorrent(download.torrentId)
            } catch (e: Exception) {
                debugLogger.logError("Failed to retry download", e)
                _errorMessage.value = "Failed to retry download: ${e.message}"
            }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            try {
                debugLogger.logInfo("Clearing completed downloads")
                _completedDownloads.value.forEach { download -> torrentRepository.stopTorrent(download.torrentId) }
            } catch (e: Exception) {
                debugLogger.logError("Failed to clear completed downloads", e)
                _errorMessage.value = "Failed to clear completed: ${e.message}"
            }
        }
    }

    fun clearFailed() {
        viewModelScope.launch {
            try {
                debugLogger.logInfo("Clearing failed downloads")
                _failedDownloads.value.forEach { download -> torrentRepository.removeTorrent(download.torrentId, deleteFiles = false) }
            } catch (e: Exception) {
                debugLogger.logError("Failed to clear failed downloads", e)
                _errorMessage.value = "Failed to clear failed: ${e.message}"
            }
        }
    }

    fun refreshDownloads() {
        loadDownloads()
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}

enum class DownloadsTab {
    Active,
    Completed,
    Failed,
}

data class DownloadsUiState(
    val activeDownloads: List<DownloadProgress> = emptyList(),
    val completedDownloads: List<DownloadProgress> = emptyList(),
    val failedDownloads: List<DownloadProgress> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedTab: DownloadsTab = DownloadsTab.Active,
)
