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

package com.jabook.app.jabook.compose.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.repository.DownloadRepository
import com.jabook.app.jabook.compose.domain.model.DownloadFilter
import com.jabook.app.jabook.compose.domain.model.DownloadInfo
import com.jabook.app.jabook.compose.domain.model.DownloadPriority
import com.jabook.app.jabook.compose.domain.usecase.download.CancelDownloadUseCase
import com.jabook.app.jabook.compose.domain.usecase.download.GetActiveDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Downloads screen.
 *
 * Manages active downloads state, filtering, and provides actions for
 * download management (cancel, priority, pause/resume).
 */
@HiltViewModel
class DownloadViewModel
    @Inject
    constructor(
        getActiveDownloadsUseCase: GetActiveDownloadsUseCase,
        private val cancelDownloadUseCase: CancelDownloadUseCase,
        private val downloadRepository: DownloadRepository,
    ) : ViewModel() {
        /**
         * Current filter selection.
         */
        private val _currentFilter = MutableStateFlow(DownloadFilter.ALL)
        val currentFilter: StateFlow<DownloadFilter> = _currentFilter.asStateFlow()

        /**
         * Active downloads state.
         */
        private val allDownloads: StateFlow<List<DownloadInfo>> =
            getActiveDownloadsUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Filtered downloads based on current filter.
         */
        val filteredDownloads: StateFlow<List<DownloadInfo>> =
            combine(allDownloads, _currentFilter) { downloads, filter ->
                when (filter) {
                    DownloadFilter.ALL -> downloads
                    DownloadFilter.ACTIVE ->
                        downloads.filter { download ->
                            download.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Downloading ||
                                download.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Idle
                        }
                    DownloadFilter.PAUSED ->
                        downloads.filter { download ->
                            download.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Paused
                        }
                    DownloadFilter.COMPLETED ->
                        downloads.filter { download ->
                            download.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Completed
                        }
                    DownloadFilter.FAILED ->
                        downloads.filter { download ->
                            download.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Failed
                        }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /**
         * Set download filter.
         */
        fun setFilter(filter: DownloadFilter) {
            _currentFilter.value = filter
        }

        /**
         * Cancel a download.
         *
         * @param bookId ID of the book to cancel downloading
         */
        fun cancelDownload(bookId: String) {
            viewModelScope.launch {
                cancelDownloadUseCase(bookId)
            }
        }

        /**
         * Update download priority.
         *
         * @param bookId ID of the book
         * @param priority New priority level
         */
        fun updatePriority(
            bookId: String,
            priority: DownloadPriority,
        ) {
            viewModelScope.launch {
                downloadRepository.updatePriority(bookId, priority)
            }
        }

        /**
         * Pause a download.
         *
         * @param bookId ID of the book to pause
         */
        fun pauseDownload(bookId: String) {
            viewModelScope.launch {
                downloadRepository.pauseDownload(bookId)
            }
        }

        /**
         * Resume a paused download.
         *
         * @param bookId ID of the book to resume
         */
        fun resumeDownload(bookId: String) {
            viewModelScope.launch {
                downloadRepository.resumeDownload(bookId)
            }
        }

        /**
         * Pause all active downloads.
         */
        fun pauseAll() {
            viewModelScope.launch {
                allDownloads.value
                    .filter { it.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Downloading }
                    .forEach { downloadRepository.pauseDownload(it.bookId) }
            }
        }

        /**
         * Resume all paused downloads.
         */
        fun resumeAll() {
            viewModelScope.launch {
                allDownloads.value
                    .filter { it.state is com.jabook.app.jabook.compose.domain.model.DownloadState.Paused }
                    .forEach { downloadRepository.resumeDownload(it.bookId) }
            }
        }
    }
