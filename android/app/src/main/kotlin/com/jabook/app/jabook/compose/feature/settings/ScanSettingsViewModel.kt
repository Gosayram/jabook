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

package com.jabook.app.jabook.compose.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.scanner.ScanPathValidator
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class ScanSettingsViewModel
    @Inject
    constructor(
        private val booksRepository: BooksRepository,
        private val loggerFactory: LoggerFactory,
    ) : ViewModel() {
        public val scanPaths: StateFlow<List<String>> =
            booksRepository
                .getScanPaths()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        public fun addScanPath(uriString: String) {
            val path = ScanPathValidator.normalize(FileUtils.resolvePathFromUri(uriString))
            // The scanner is File-based: a raw content:// URI can never be scanned.
            // Reject at the boundary instead of persisting a silently dead row.
            if (path.startsWith("content://")) {
                loggerFactory.get("ScanSettingsViewModel").w {
                    "Rejecting non-filesystem scan path: $uriString"
                }
                return
            }
            viewModelScope.launch {
                booksRepository.addScanPath(path)
                // Trigger rescan? The scanner runs on startup or manual refresh.
                booksRepository.refresh()
            }
        }

        public fun removeScanPath(path: String) {
            viewModelScope.launch {
                booksRepository.removeScanPath(path)
                booksRepository.refresh()
            }
        }
    }
