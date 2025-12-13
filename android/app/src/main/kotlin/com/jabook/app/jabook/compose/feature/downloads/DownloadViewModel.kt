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
import com.jabook.app.jabook.compose.domain.model.DownloadInfo
import com.jabook.app.jabook.compose.domain.usecase.download.CancelDownloadUseCase
import com.jabook.app.jabook.compose.domain.usecase.download.GetActiveDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Downloads screen.
 *
 * Manages active downloads state and provides actions for
 * canceling downloads.
 */
@HiltViewModel
class DownloadViewModel
    @Inject
    constructor(
        getActiveDownloadsUseCase: GetActiveDownloadsUseCase,
        private val cancelDownloadUseCase: CancelDownloadUseCase,
    ) : ViewModel() {
        /**
         * Active downloads state.
         */
        val activeDownloads: StateFlow<List<DownloadInfo>> =
            getActiveDownloadsUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

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
    }
