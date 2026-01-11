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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.compose.navigation.DownloadsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
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

/**
 * UI state for torrent downloads screen
 */
public sealed interface TorrentDownloadsUiState {
    data object Loading : TorrentDownloadsUiState

    public data class Success(
        val activeDownloads: List<TorrentDownload>,
        val pausedDownloads: List<TorrentDownload>,
        val completedDownloads: List<TorrentDownload>,
        val errorDownloads: List<TorrentDownload>,
    ) : TorrentDownloadsUiState

    data object Empty : TorrentDownloadsUiState

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
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // Init block moved below to use new prepareAddTorrent logic

        private val _snackbarEvent = Channel<String>()
        val snackbarEvent = _snackbarEvent.receiveAsFlow()

        // Selected download for details view
        private val _selectedDownload = MutableStateFlow<TorrentDownload?>(null)
        val selectedDownload: StateFlow<TorrentDownload?> = _selectedDownload.asStateFlow()

        // Filter state
        private val _showCompletedOnly = MutableStateFlow(false)
        val showCompletedOnly: StateFlow<Boolean> = _showCompletedOnly.asStateFlow()

        // UI state combining downloads from manager and repository
        val uiState: StateFlow<TorrentDownloadsUiState> =
            combine(
                torrentManager.downloadsFlow,
                repository.getAllFlow(),
                _showCompletedOnly,
            ) { activeDownloads, persistedDownloads, showCompletedOnly ->
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

                    if (allDownloads.isEmpty()) {
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

                        TorrentDownloadsUiState.Success(
                            activeDownloads = active,
                            pausedDownloads = paused,
                            completedDownloads = completed,
                            errorDownloads = errors,
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TorrentDownloadsViewModel", "Error processing downloads", e)
                    TorrentDownloadsUiState.Error(e.message ?: "Unknown error")
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = TorrentDownloadsUiState.Loading,
            )

        /**
         * Pause download
         */
        public fun pauseDownload() {
            viewModelScope.launch {
                torrentManager.pauseTorrent(hash)
            }
        }

        /**
         * Resume download
         */
        public fun resumeDownload() {
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
        public fun selectDownload() {
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
        val pendingMagnetLink: StateFlow<String?> = _pendingMagnetLink.asStateFlow()

        private val _pendingDownloadPath = MutableStateFlow("")
        val pendingDownloadPath: StateFlow<String> = _pendingDownloadPath.asStateFlow()

        init {
            // Check for initial magnet link
            try {
                val route = savedStateHandle.toRoute<DownloadsRoute>()
                route.magnetLink?.let { magnetLink ->
                    prepareAddTorrent(magnetLink)
                }
            } catch (e: Exception) {
                // Ignore if not navigated via route with args
            }
        }

        public fun prepareAddTorrent() {
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

        public fun updatePendingPath() {
            _pendingDownloadPath.value = path
        }

        public fun updatePendingPathFromUri() {
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
                    torrentManager.addTorrent(magnetLink, path)
                    _pendingMagnetLink.value = null
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

            if (prefs.wifiOnlyDownload && networkType != com.jabook.app.jabook.compose.data.network.NetworkType.WIFI) {
                _snackbarEvent.send("Download queued: Waiting for WiFi connection")
            }
        }
    }
