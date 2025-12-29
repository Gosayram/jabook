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
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        private val updateBookSettingsUseCase: com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase,
        private val booksRepository: com.jabook.app.jabook.compose.data.repository.BooksRepository,
        private val playbackPositionRepository: PlaybackPositionRepository,
    ) : ViewModel() {
        // Get bookId from navigation arguments
        private val args = savedStateHandle.toRoute<PlayerRoute>()
        private val bookId = args.bookId

        // Track if book has been loaded into player
        private var isBookLoaded = false

        // Saved position from database (restored on init)
        private var savedPosition: Long = 0L
        private var savedChapterIndex: Int = 0

        // Chapter repeat mode state
        private val _chapterRepeatMode = MutableStateFlow(ChapterRepeatMode.OFF)
        val chapterRepeatMode: StateFlow<ChapterRepeatMode> = _chapterRepeatMode.asStateFlow()

        // Track if we've already repeated once (for ONCE mode)
        private var hasRepeatedOnce = false

        init {
            // CRITICAL: Restore saved position from database on init
            // This ensures position is restored in all scenarios:
            // - User paused and closed app
            // - Device battery died
            // - Phone call interrupted playback
            // - Other system events
            viewModelScope.launch {
                try {
                    val positionResult = playbackPositionRepository.getPosition(bookId).first()
                    when (positionResult) {
                        is com.jabook.app.jabook.audio.core.result.Result.Success -> {
                            positionResult.data?.let { entity ->
                                savedPosition = entity.position
                                savedChapterIndex = entity.trackIndex
                                android.util.Log.d(
                                    "PlayerViewModel",
                                    "Restored position from database: chapter=$savedChapterIndex, position=${savedPosition}ms",
                                )
                            }
                        }
                        is com.jabook.app.jabook.audio.core.result.Result.Error -> {
                            android.util.Log.w("PlayerViewModel", "Failed to restore position: ${positionResult.exception.message}")
                        }
                        else -> {
                            // Loading state, will be updated when ready
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerViewModel", "Error restoring position from database", e)
                }
            }
        }

        /**
         * Combined UI state from book data, playback state, and settings.
         */
        val uiState: StateFlow<PlayerUiState> =
            combine(
                getBookDetailsUseCase(bookId),
                getChaptersUseCase(bookId),
                playerController.isPlaying,
                playerController.currentPosition,
                playerController.currentChapterIndex,
                settingsRepository.userPreferences,
            ) { args ->
                val book = args[0] as? Book

                @Suppress("UNCHECKED_CAST")
                val chapters = args[1] as List<Chapter>
                val playing = args[2] as Boolean
                val controllerPosition = args[3] as Long
                val controllerChapterIndex = args[4] as Int
                val preferences = args[5] as com.jabook.app.jabook.compose.data.preferences.UserPreferences

                if (book == null) {
                    PlayerUiState.Error("Book not found")
                } else {
                    // Calculate effective seek intervals
                    // Priority: Book Override -> Global Setting -> Hardcoded Default
                    val rewindInterval =
                        book.rewindDuration
                            ?: if (preferences.rewindDurationSeconds > 0) preferences.rewindDurationSeconds.toInt() else 10
                    val forwardInterval =
                        book.forwardDuration
                            ?: if (preferences.forwardDurationSeconds > 0) preferences.forwardDurationSeconds.toInt() else 30

                    // Use saved position from database if player hasn't loaded yet
                    // This ensures position is restored even if player hasn't started
                    val position = if (controllerPosition > 0 || isBookLoaded) controllerPosition else savedPosition
                    val chapterIndex =
                        if (controllerChapterIndex > 0 ||
                            isBookLoaded
                        ) {
                            controllerChapterIndex
                        } else {
                            savedChapterIndex.coerceIn(0, chapters.size - 1)
                        }

                    PlayerUiState.Success(
                        book = book,
                        chapters = chapters,
                        isPlaying = playing,
                        currentPosition = position,
                        currentChapterIndex = chapterIndex,
                        currentChapter = chapters.getOrNull(chapterIndex),
                        rewindInterval = rewindInterval,
                        forwardInterval = forwardInterval,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly, // Start immediately to avoid race conditions
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
         * Chapter title normalization preference.
         */
        val normalizeChapterTitles: StateFlow<Boolean> =
            userPreferencesRepository.userData
                .map { it.normalizeChapterTitles }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Current sleep timer state.
         */
        val sleepTimerState: StateFlow<com.jabook.app.jabook.compose.domain.model.SleepTimerState> =
            sleepTimerRepository.timerState

        // Player control methods delegated to controller

        fun play() {
            val state = uiState.value
            if (state is PlayerUiState.Success) {
                // Ensure book is loaded before playing
                if (!isBookLoaded) {
                    val filePaths = state.chapters.mapNotNull { it.fileUrl }
                    if (filePaths.isNotEmpty()) {
                        playerController.loadBook(
                            filePaths = filePaths,
                            initialChapterIndex = state.currentChapterIndex,
                            initialPosition = state.currentPosition,
                            autoPlay = true, // Auto-play after loading
                            metadata =
                                mapOf(
                                    "title" to state.book.title,
                                    "author" to state.book.author,
                                    "bookTitle" to state.book.title, // For fallback
                                    "artist" to state.book.author, // For fallback
                                ),
                            bookId = bookId,
                        )
                        isBookLoaded = true
                    }
                } else {
                    playerController.play()
                }
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
            // Reset repeat flag when manually changing chapters
            onChapterChanged()
            // Always start playback when user selects a chapter from the chapter selector
            // Use coroutine to ensure chapter switch completes before starting playback
            viewModelScope.launch {
                kotlinx.coroutines.delay(100) // Small delay to ensure chapter switch completes
                playerController.play()
            }
        }

        fun seekForward() {
            val state = uiState.value
            if (state is PlayerUiState.Success && state.currentChapter != null) {
                val interval = state.forwardInterval
                val newPosition =
                    (playerController.currentPosition.value + interval * 1000)
                        .coerceAtMost(state.currentChapter.duration.inWholeMilliseconds)
                seekTo(newPosition)
            }
        }

        fun seekBackward() {
            val state = uiState.value
            if (state is PlayerUiState.Success) {
                val interval = state.rewindInterval
                val newPosition = (playerController.currentPosition.value - interval * 1000).coerceAtLeast(0)
                seekTo(newPosition)
            }
        }

        fun setPlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaybackSpeed(speed)
            }
        }

        fun startSleepTimer(minutes: Int) {
            sleepTimerRepository.startTimer(minutes)
        }

        fun cancelSleepTimer() {
            sleepTimerRepository.cancelTimer()
        }

        fun updateBookSeekSettings(
            rewindSeconds: Int?,
            forwardSeconds: Int?,
        ) {
            viewModelScope.launch {
                updateBookSettingsUseCase(bookId, rewindSeconds, forwardSeconds)
            }
        }

        fun resetBookSeekSettings() {
            viewModelScope.launch {
                updateBookSettingsUseCase.resetForBook(bookId)
            }
        }

        /**
         * Initialize player with book data if needed.
         * Restores saved position from database if available.
         */
        fun initializePlayer() {
            val state = uiState.value
            if (state is PlayerUiState.Success && !isBookLoaded) {
                val filePaths = state.chapters.mapNotNull { it.fileUrl }
                if (filePaths.isNotEmpty()) {
                    // Use saved position from database if available, otherwise use current position
                    val initialChapterIndex = if (savedChapterIndex > 0) savedChapterIndex else state.currentChapterIndex
                    val initialPosition = if (savedPosition > 0) savedPosition else state.currentPosition

                    android.util.Log.d(
                        "PlayerViewModel",
                        "Initializing player: chapter=$initialChapterIndex, position=${initialPosition}ms",
                    )

                    // Set callback for chapter end handling (repeat logic)
                    playerController.setOnChapterEndedCallback {
                        onChapterEnded()
                    }

                    playerController.loadBook(
                        filePaths = filePaths,
                        initialChapterIndex = initialChapterIndex,
                        initialPosition = initialPosition,
                        autoPlay = false, // Don't auto-play on init
                        metadata =
                            mapOf(
                                "title" to state.book.title,
                                "author" to state.book.author,
                                "bookTitle" to state.book.title, // For fallback
                                "artist" to state.book.author, // For fallback
                            ),
                        bookId = bookId,
                    )
                    isBookLoaded = true
                }
            }
        }

        fun reorderChapters(newOrderedIds: List<String>) {
            viewModelScope.launch {
                booksRepository.updateChapterOrder(bookId, newOrderedIds)
            }
        }

        /**
         * Toggle chapter repeat mode: OFF -> ONCE -> INFINITE -> OFF
         */
        fun toggleChapterRepeat() {
            _chapterRepeatMode.value =
                when (_chapterRepeatMode.value) {
                    ChapterRepeatMode.OFF -> ChapterRepeatMode.ONCE
                    ChapterRepeatMode.ONCE -> ChapterRepeatMode.INFINITE
                    ChapterRepeatMode.INFINITE -> ChapterRepeatMode.OFF
                }
            // Reset repeat flag when changing mode
            hasRepeatedOnce = false
        }

        /**
         * Handle chapter end - check if we need to repeat.
         * Called by AudioPlayerController when chapter ends.
         *
         * @return true if chapter should be repeated, false to continue to next
         */
        fun onChapterEnded(): Boolean =
            when (_chapterRepeatMode.value) {
                ChapterRepeatMode.OFF -> {
                    // No repeat, continue to next chapter
                    false
                }
                ChapterRepeatMode.ONCE -> {
                    // Repeat once if not already repeated
                    if (!hasRepeatedOnce) {
                        hasRepeatedOnce = true
                        true // Need to repeat
                    } else {
                        // Already repeated once, continue to next
                        hasRepeatedOnce = false
                        false
                    }
                }
                ChapterRepeatMode.INFINITE -> {
                    // Always repeat
                    true
                }
            }

        /**
         * Reset repeat flag when chapter changes manually.
         */
        fun onChapterChanged() {
            hasRepeatedOnce = false
        }
    }

/**
 * Chapter repeat mode for player.
 *
 * - OFF: No repeat, play next chapter when current ends
 * - ONCE: Repeat current chapter once, then play next
 * - INFINITE: Repeat current chapter infinitely
 */
enum class ChapterRepeatMode {
    OFF,
    ONCE,
    INFINITE,
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
        val rewindInterval: Int,
        val forwardInterval: Int,
    ) : PlayerUiState

    /**
     * Error state.
     */
    data class Error(
        val message: String,
    ) : PlayerUiState
}
