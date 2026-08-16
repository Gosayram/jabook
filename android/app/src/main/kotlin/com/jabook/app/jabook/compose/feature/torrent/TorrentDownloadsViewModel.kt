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

package com.jabook.app.jabook.compose.feature.torrent

import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.TorrentDownloadNetworkPolicy
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.repository.DownloadHistoryRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem
import com.jabook.app.jabook.compose.navigation.DownloadsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * UI state for torrent downloads screen
 */
public sealed interface TorrentDownloadsUiState {
    @Immutable
    public data object Loading : TorrentDownloadsUiState

    @Immutable
    public data class Success(
        val activeDownloads: ImmutableList<TorrentDownload>,
        val pausedDownloads: ImmutableList<TorrentDownload>,
        val completedDownloads: ImmutableList<TorrentDownload>,
        val errorDownloads: ImmutableList<TorrentDownload>,
        val historyItems: ImmutableList<DownloadHistoryItem>,
        val downloadingCount: Int,
        val totalDownloadSpeed: Long,
        val queuedCount: Int,
        val audiobookStorageUsed: Long,
        val totalStorageUsed: Long,
        val availableStorage: Long,
    ) : TorrentDownloadsUiState

    @Immutable
    public data object Empty : TorrentDownloadsUiState

    @Immutable
    public data class Error(
        val message: String,
    ) : TorrentDownloadsUiState
}

/**
 * ViewModel for torrent downloads management
 */
@HiltViewModel
public class TorrentDownloadsViewModel
    @Inject
    constructor(
        private val torrentManager: TorrentManager,
        private val repository: TorrentDownloadRepository,
        private val settingsRepository: SettingsRepository,
        private val networkMonitor: NetworkMonitor,
        private val downloadHistoryRepository: DownloadHistoryRepository,
        private val loggerFactory: LoggerFactory,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val logger = loggerFactory.get("TorrentDownloadsViewModel")
        // Init block moved below to use new prepareAddTorrent logic

        private val _snackbarEvent = Channel<String>()
        public val snackbarEvent: Flow<String> = _snackbarEvent.receiveAsFlow()

        // Selected download for details view
        private val _selectedDownload = MutableStateFlow<TorrentDownload?>(null)
        public val selectedDownload: StateFlow<TorrentDownload?> = _selectedDownload.asStateFlow()

        // Filter state
        private val _showCompletedOnly = MutableStateFlow(false)
        public val showCompletedOnly: StateFlow<Boolean> = _showCompletedOnly.asStateFlow()

        // Retry trigger — toggled by retryLoad() to re-evaluate the combine
        private val retryTrigger = MutableStateFlow(0L)

        // UI state combining downloads from manager and repository
        public val uiState: StateFlow<TorrentDownloadsUiState> =
            combine(
                torrentManager.downloadsFlow,
                repository.getAllFlow(),
                _showCompletedOnly,
                downloadHistoryRepository.getHistoryWithFilter(),
                retryTrigger,
            ) { activeDownloads, persistedDownloads, showCompletedOnly, historyItems, _ ->
                try {
                    // Merge active downloads with persisted ones
                    // Active downloads take precedence (they have real-time data)
                    val activeMap = activeDownloads.values.associateBy { it.hash }
                    val persistedMap = persistedDownloads.associateBy { it.hash }

                    // Combine: active downloads override persisted ones with same hash
                    val allDownloads =
                        (persistedMap + activeMap)
                            .values
                            .filter { download ->
                                // Validate download data
                                download.hash.isNotBlank() && download.name.isNotBlank()
                            }.sortedByDescending { it.addedTime }

                    if (allDownloads.isEmpty() && historyItems.isEmpty()) {
                        TorrentDownloadsUiState.Empty
                    } else {
                        // Group by state
                        val active =
                            allDownloads.filter { download ->
                                download.state in
                                    listOf(
                                        TorrentState.DOWNLOADING,
                                        TorrentState.CHECKING,
                                        TorrentState.DOWNLOADING_METADATA,
                                        TorrentState.SEEDING,
                                        TorrentState.STREAMING,
                                    )
                            }

                        val paused = allDownloads.filter { it.state == TorrentState.PAUSED }
                        val completed = allDownloads.filter { it.state == TorrentState.COMPLETED }
                        val errors = allDownloads.filter { it.state == TorrentState.ERROR }
                        val queued = allDownloads.filter { it.state == TorrentState.QUEUED }
                        val downloading = allDownloads.filter { it.state == TorrentState.DOWNLOADING }

                        // Calculate stats
                        val totalDownloadSpeed = downloading.sumOf { it.downloadSpeed.toLong() }

                        // Calculate storage stats
                        val storageStats = calculateStorageStats(allDownloads)

                        TorrentDownloadsUiState.Success(
                            activeDownloads = active.toImmutableList(),
                            pausedDownloads = paused.toImmutableList(),
                            completedDownloads = completed.toImmutableList(),
                            errorDownloads = errors.toImmutableList(),
                            historyItems = historyItems.take(50).toImmutableList(),
                            downloadingCount = downloading.size,
                            totalDownloadSpeed = totalDownloadSpeed,
                            queuedCount = queued.size,
                            audiobookStorageUsed = storageStats.audiobookStorageUsed,
                            totalStorageUsed = storageStats.totalStorageUsed,
                            availableStorage = storageStats.availableStorage,
                        )
                    }
                } catch (e: Exception) {
                    logger.e({ "Error processing downloads" }, e)
                    TorrentDownloadsUiState.Error(e.message ?: "Unknown error")
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TorrentDownloadsUiState.Loading,
            )

        /**
         * Retry loading after an error state.
         */
        public fun retryLoad() {
            retryTrigger.value++
        }

        /**
         * Pause download
         */
        public fun pauseDownload(hash: String) {
            viewModelScope.launch {
                torrentManager.pauseTorrent(hash)
            }
        }

        /**
         * Resume download
         */
        public fun resumeDownload(hash: String) {
            viewModelScope.launch {
                checkNetworkAndWarn()
                torrentManager.resumeTorrent(hash)
            }
        }

        /**
         * Stop and remove download
         */
        public fun deleteDownload(
            hash: String,
            deleteFiles: Boolean = false,
        ) {
            viewModelScope.launch {
                torrentManager.removeTorrent(hash, deleteFiles)
                repository.delete(hash)
            }
        }

        /**
         * Select download for details view
         */
        public fun selectDownload(download: TorrentDownload) {
            _selectedDownload.value = download
        }

        /**
         * Clear selection
         */
        public fun clearSelection() {
            _selectedDownload.value = null
        }

        /**
         * Toggle show completed filter
         */
        public fun toggleShowCompleted() {
            _showCompletedOnly.value = !_showCompletedOnly.value
        }

        /**
         * Delete all completed downloads
         */
        public fun deleteAllCompleted() {
            viewModelScope.launch {
                val state = uiState.value
                if (state is TorrentDownloadsUiState.Success) {
                    state.completedDownloads.forEach { download ->
                        torrentManager.removeTorrent(download.hash, deleteFiles = false)
                    }
                }
            }
        }

        /**
         * Pause all active downloads
         */
        public fun pauseAll() {
            viewModelScope.launch {
                val state = uiState.value
                if (state is TorrentDownloadsUiState.Success) {
                    state.activeDownloads.forEach { download ->
                        torrentManager.pauseTorrent(download.hash)
                    }
                }
            }
        }

        /**
         * Resume all paused downloads
         */
        public fun resumeAll() {
            viewModelScope.launch {
                val state = uiState.value
                if (state is TorrentDownloadsUiState.Success) {
                    state.pausedDownloads.forEach { download ->
                        torrentManager.resumeTorrent(download.hash)
                    }
                }
            }
        }

        /**
         * Add torrent from magnet link
         */
        // Pending torrent state for dialog
        private val _pendingMagnetLink = MutableStateFlow<String?>(null)
        public val pendingMagnetLink: StateFlow<String?> = _pendingMagnetLink.asStateFlow()

        private val _pendingDownloadPath = MutableStateFlow("")
        public val pendingDownloadPath: StateFlow<String> = _pendingDownloadPath.asStateFlow()

        init {
            // Check for initial magnet link
            try {
                val route = savedStateHandle.toRoute<DownloadsRoute>()
                route.magnetLink?.let { magnetLink ->
                    prepareAddTorrent(magnetLink)
                }
            } catch (_: IllegalArgumentException) {
                // Ignore if route args are absent or malformed
            } catch (_: IllegalStateException) {
                // Ignore if not navigated via route with args
            }
        }

        public fun prepareAddTorrent(magnetLink: String) {
            viewModelScope.launch {
                val prefs = settingsRepository.userPreferences.first()
                val defaultPath =
                    prefs.downloadPath.ifEmpty {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                    }
                _pendingDownloadPath.value = defaultPath
                _pendingMagnetLink.value = magnetLink
            }
        }

        public fun updatePendingPath(path: String) {
            _pendingDownloadPath.value = path
        }

        public fun updatePendingPathFromUri(uriString: String) {
            val path =
                com.jabook.app.jabook.util.FileUtils
                    .resolvePathFromUri(uriString)
            _pendingDownloadPath.value = path
        }

        public fun confirmAddTorrent() {
            viewModelScope.launch {
                val magnetLink = _pendingMagnetLink.value ?: return@launch
                val path = _pendingDownloadPath.value

                try {
                    checkNetworkAndWarn()
                    torrentManager
                        .addTorrent(magnetLink, path)
                        .onSuccess { _pendingMagnetLink.value = null }
                        .onFailure { _snackbarEvent.send("Failed to add torrent: ${it.message}") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _snackbarEvent.send("Failed to add torrent: ${e.message}")
                }
            }
        }

        public fun cancelAddTorrent() {
            _pendingMagnetLink.value = null
        }

        private suspend fun checkNetworkAndWarn() {
            val prefs = settingsRepository.userPreferences.first()
            val networkType = networkMonitor.networkType.first()

            if (
                TorrentDownloadNetworkPolicy.shouldPauseForNetwork(
                    wifiOnlyEnabled = prefs.wifiOnlyDownload,
                    networkType = networkType,
                )
            ) {
                _snackbarEvent.send("Download queued: Waiting for WiFi connection")
            }
        }

        private data class StorageStats(
            val audiobookStorageUsed: Long,
            val totalStorageUsed: Long,
            val availableStorage: Long,
        )

        private fun calculateStorageStats(downloads: List<TorrentDownload>): StorageStats {
            // Get downloads directory stats
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val stat = StatFs(downloadsDir.absolutePath)
            val availableStorage = stat.availableBlocksLong * stat.blockSizeLong

            // Calculate total storage used by downloads
            var totalStorageUsed = 0L
            var audiobookStorageUsed = 0L

            for (download in downloads) {
                if (download.totalSize > 0) {
                    totalStorageUsed += download.totalSize
                }
                // Completed downloads are audiobooks
                if (download.state == TorrentState.COMPLETED && download.totalSize > 0) {
                    audiobookStorageUsed += download.totalSize
                }
            }

            return StorageStats(
                audiobookStorageUsed = audiobookStorageUsed,
                totalStorageUsed = totalStorageUsed,
                availableStorage = availableStorage,
            )
        }
    }
