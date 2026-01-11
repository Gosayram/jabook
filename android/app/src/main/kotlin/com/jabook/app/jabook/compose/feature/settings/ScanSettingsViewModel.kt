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
import com.jabook.app.jabook.compose.data.repository.BooksRepository
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
    ) : ViewModel() {
        public val scanPaths: StateFlow<List<String>> =
            booksRepository
                .getScanPaths()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        public fun addScanPath() {
            public val path = resolvePathFromUri(uriString)
            viewModelScope.launch {
                booksRepository.addScanPath(path)
                // Trigger rescan? The scanner runs on startup or manual refresh.
                booksRepository.refresh()
            }
        }

        public fun removeScanPath() {
            viewModelScope.launch {
                booksRepository.removeScanPath(path)
                booksRepository.refresh()
            }
        }

        private fun resolvePathFromUri(uriString: String): String {
            try {
                public val uri = android.net.Uri.parse(uriString)
                if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
                    public val path = uri.path ?: return uriString
                    public val split = path.split(":")
                    if (split.size > 1) {
                        public val type = split[0]
                        public val relativePath = split[1]
                        if (type.endsWith("primary")) {
                            return "/storage/emulated/0/$relativePath"
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors and return original
            }
            return uriString
        }
    }
