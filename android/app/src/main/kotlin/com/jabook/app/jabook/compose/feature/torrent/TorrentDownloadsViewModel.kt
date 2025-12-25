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

package com.jabook.app.jabook.compose.feature.torrent

import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.data.torrent.TorrentState
import com.jabook.app.jabook.compose.navigation.DownloadsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for torrent downloads screen
 */
sealed interface TorrentDownloadsUiState {
    data object Loading : TorrentDownloadsUiState

    data class Success(
        val activeDownloads: List<TorrentDownload>,
        val pausedDownloads: List<TorrentDownload>,
        val completedDownloads: List<TorrentDownload>,
        val errorDownloads: List<TorrentDownload>,
    ) : TorrentDownloadsUiState

    data object Empty : TorrentDownloadsUiState

    data class Error(
        val message: String,
    ) : TorrentDownloadsUiState
}

/**
 * ViewModel for torrent downloads management
 */
@HiltViewModel
class TorrentDownloadsViewModel
    @Inject
    constructor(
        private val torrentManager: TorrentManager,
        private val repository: TorrentDownloadRepository,
        private val settingsRepository: SettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        init {
            // Check for initial magnet link
            try {
                val route = savedStateHandle.toRoute<DownloadsRoute>()
                route.magnetLink?.let { magnetLink ->
                    addTorrent(magnetLink)
                }
            } catch (e: Exception) {
                // Ignore if not navigated via route with args
            }
        }

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
                    val allDownloads =
                        (activeDownloads.values + persistedDownloads)
                            .distinctBy { it.hash }
                            .sortedByDescending { it.addedTime }

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
        fun pauseDownload(hash: String) {
            viewModelScope.launch {
                torrentManager.pauseTorrent(hash)
            }
        }

        /**
         * Resume download
         */
        fun resumeDownload(hash: String) {
            viewModelScope.launch {
                torrentManager.resumeTorrent(hash)
            }
        }

        /**
         * Stop and remove download
         */
        fun deleteDownload(
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
        fun selectDownload(download: TorrentDownload) {
            _selectedDownload.value = download
        }

        /**
         * Clear selection
         */
        fun clearSelection() {
            _selectedDownload.value = null
        }

        /**
         * Toggle show completed filter
         */
        fun toggleShowCompleted() {
            _showCompletedOnly.value = !_showCompletedOnly.value
        }

        /**
         * Delete all completed downloads
         */
        fun deleteAllCompleted() {
            viewModelScope.launch {
                repository.deleteAllCompleted()
            }
        }

        /**
         * Pause all active downloads
         */
        fun pauseAll() {
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
        fun resumeAll() {
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
        fun addTorrent(magnetLink: String) {
            viewModelScope.launch {
                try {
                    val prefs = settingsRepository.userPreferences.first()
                    val downloadPath =
                        prefs.downloadPath.ifEmpty {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        }

                    torrentManager.addTorrent(magnetLink, downloadPath)
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }
