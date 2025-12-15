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

package com.jabook.app.jabook.compose.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.debug.DebugLogService
import com.jabook.app.jabook.compose.data.network.MirrorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Debug screen.
 * Manages log collection, mirror testing, and cache statistics.
 */
@HiltViewModel
class DebugViewModel
    @Inject
    constructor(
        private val debugLogService: DebugLogService,
        private val mirrorManager: MirrorManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<DebugUiState>(DebugUiState.Initial)
        val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

        private val _logs = MutableStateFlow<String>("")
        val logs: StateFlow<String> = _logs.asStateFlow()

        init {
            loadLogs()
        }

        fun loadLogs() {
            viewModelScope.launch {
                try {
                    _uiState.value = DebugUiState.Loading
                    val logContent = debugLogService.collectLogs()
                    _logs.value = logContent
                    _uiState.value = DebugUiState.Success
                } catch (e: Exception) {
                    _uiState.value = DebugUiState.Error(e.message ?: "Failed to load logs")
                }
            }
        }

        fun shareLogs() {
            viewModelScope.launch {
                try {
                    _uiState.value = DebugUiState.Loading
                    debugLogService.shareLogs()
                    _uiState.value = DebugUiState.Success
                } catch (e: Exception) {
                    _uiState.value = DebugUiState.Error(e.message ?: "Failed to share logs")
                }
            }
        }

        fun clearOldLogFiles() {
            viewModelScope.launch {
                debugLogService.clearOldLogFiles()
            }
        }

        fun testAllMirrors() {
            viewModelScope.launch {
                _uiState.value = DebugUiState.Loading
                // TODO: Implement mirror testing
                _uiState.value = DebugUiState.Success
            }
        }

        fun refreshDebugData() {
            loadLogs()
        }
    }

/**
 * UI state for Debug screen.
 */
sealed class DebugUiState {
    data object Initial : DebugUiState()

    data object Loading : DebugUiState()

    data object Success : DebugUiState()

    data class Error(
        val message: String,
    ) : DebugUiState()
}
