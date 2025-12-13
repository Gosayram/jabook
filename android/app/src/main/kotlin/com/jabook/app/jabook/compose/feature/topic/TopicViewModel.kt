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

package com.jabook.app.jabook.compose.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.navigation.TopicRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for Topic Screen.
 */
sealed interface TopicUiState {
    data object Loading : TopicUiState

    data class Success(
        val details: TopicDetails,
    ) : TopicUiState

    data class Error(
        val message: String,
    ) : TopicUiState
}

/**
 * ViewModel for Topic Screen.
 *
 * Loads and displays detailed information about a RuTracker topic.
 */
@HiltViewModel
class TopicViewModel
    @Inject
    constructor(
        private val rutrackerRepository: RutrackerRepository,
        private val authRepository: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val topicId: String = savedStateHandle.toRoute<TopicRoute>().topicId

        private val _uiState = MutableStateFlow<TopicUiState>(TopicUiState.Loading)
        val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

        val authStatus: StateFlow<AuthStatus> =
            authRepository.authStatus.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AuthStatus.Unauthenticated,
            )

        init {
            loadTopicDetails()
        }

        private fun loadTopicDetails() {
            viewModelScope.launch {
                _uiState.value = TopicUiState.Loading

                val result = rutrackerRepository.getTopicDetails(topicId)

                _uiState.value =
                    when (result) {
                        is com.jabook.app.jabook.compose.domain.model.Result.Success -> {
                            TopicUiState.Success(result.data)
                        }
                        is com.jabook.app.jabook.compose.domain.model.Result.Error -> {
                            TopicUiState.Error(result.message ?: "Unknown error")
                        }
                        is com.jabook.app.jabook.compose.domain.model.Result.Loading -> {
                            TopicUiState.Loading
                        }
                    }
            }
        }

        fun downloadTorrent() {
            viewModelScope.launch {
                val details = (_uiState.value as? TopicUiState.Success)?.details ?: return@launch

                // TODO: Trigger download via DownloadManager
                // For now, this is a placeholder
                // Will be implemented when integrating with download system
            }
        }

        fun retry() {
            loadTopicDetails()
        }
    }
