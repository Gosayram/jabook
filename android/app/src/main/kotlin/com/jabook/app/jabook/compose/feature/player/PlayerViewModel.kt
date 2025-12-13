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

package com.jabook.app.jabook.compose.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Player screen.
 *
 * Uses domain layer use cases to manage player state following
 * Clean Architecture principles.
 *
 * Manages player state and integrates with AudioPlayerService.
 * For MVP, we show the UI structure without full AudioPlayerService integration.
 *
 * @param savedStateHandle Navigation arguments containing bookId
 * @param getBookDetailsUseCase Use case for retrieving book details
 * @param getChaptersUseCase Use case for retrieving book chapters
 * @param playerController Controller for audio playback
 */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getBookDetailsUseCase: GetBookDetailsUseCase,
        private val getChaptersUseCase: GetChaptersUseCase,
        private val playerController: com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController,
        private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository,
        private val userPreferencesRepository: com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository,
        private val sleepTimerRepository: com.jabook.app.jabook.compose.data.repository.SleepTimerRepository,
    ) : ViewModel() {
        // Get bookId from navigation arguments
        private val args = savedStateHandle.toRoute<PlayerRoute>()
        private val bookId = args.bookId

        /**
         * Combined UI state from book data and playback state.
         */
        val uiState: StateFlow<PlayerUiState> =
            combine(
                getBookDetailsUseCase(bookId),
                getChaptersUseCase(bookId),
                playerController.isPlaying,
                playerController.currentPosition,
                playerController.currentChapterIndex,
            ) { book, chapters, playing, position, chapterIndex ->
                if (book == null) {
                    PlayerUiState.Error("Book not found")
                } else {
                    PlayerUiState.Success(
                        book = book,
                        chapters = chapters,
                        isPlaying = playing,
                        currentPosition = position,
                        currentChapterIndex = chapterIndex,
                        currentChapter = chapters.getOrNull(chapterIndex),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PlayerUiState.Loading,
            )

        /**
         * Current playback speed from user preferences.
         */
        val playbackSpeed: StateFlow<Float> =
            userPreferencesRepository.userData
                .map { it.playbackSpeed }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = 1.0f,
                )

        /**
         * Current sleep timer state.
         */
        val sleepTimerState: StateFlow<com.jabook.app.jabook.compose.domain.model.SleepTimerState> =
            sleepTimerRepository.timerState

        // Player control methods delegated to controller

        fun play() {
            // If we have book data but not playing, we might need to load the book first
            // simplified for MVP: assume book is loaded if we are here or just call play
            val state = uiState.value
            if (state is PlayerUiState.Success) {
                // Check if we need to load variables (omitted for strict MVP, assuming existing playlist)
                // In full implementation: if (currentMediaId != bookId) playerController.loadBook(...)
                playerController.play()
            }
        }

        fun pause() {
            playerController.pause()
        }

        fun seekTo(positionMs: Long) {
            playerController.seekTo(positionMs)
        }

        fun skipToNext() {
            playerController.skipToNext()
        }

        fun skipToPrevious() {
            playerController.skipToPrevious()
        }

        fun skipToChapter(chapterIndex: Int) {
            playerController.skipToChapter(chapterIndex)
        }

        fun seekForward(seconds: Int = 30) {
            val state = uiState.value
            if (state is PlayerUiState.Success && state.currentChapter != null) {
                val newPosition =
                    (playerController.currentPosition.value + seconds * 1000)
                        .coerceAtMost(state.currentChapter.duration.inWholeMilliseconds)
                seekTo(newPosition)
            }
        }

        fun seekBackward(seconds: Int = 10) {
            val newPosition = (playerController.currentPosition.value - seconds * 1000).coerceAtLeast(0)
            seekTo(newPosition)
        }

        fun setPlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                settingsRepository.updatePlaybackSpeed(speed)
            }
        }

        fun startSleepTimer(minutes: Int) {
            sleepTimerRepository.startTimer(minutes)
        }

        fun cancelSleepTimer() {
            sleepTimerRepository.cancelTimer()
        }

        /**
         * Initialize player with book data if needed
         */
        fun initializePlayer() {
            val state = uiState.value
            if (state is PlayerUiState.Success) {
                // Logic to load book if not already loaded would go here
                // For MVP, we rely on manual "Load" or assume it's loaded via clicking "Play"
                // Or we can auto-load here:
                // playerController.loadBook(state.book.chapters.map { it.fileUrl ?: "" })
            }
        }
    }

/**
 * UI state for the Player screen.
 */
sealed interface PlayerUiState {
    /**
     * Loading state - fetching book data.
     */
    data object Loading : PlayerUiState

    /**
     * Success state with book and playback info.
     */
    data class Success(
        val book: Book,
        val chapters: List<Chapter>,
        val isPlaying: Boolean,
        val currentPosition: Long, // milliseconds
        val currentChapterIndex: Int,
        val currentChapter: Chapter?,
    ) : PlayerUiState

    /**
     * Error state.
     */
    data class Error(
        val message: String,
    ) : PlayerUiState
}
