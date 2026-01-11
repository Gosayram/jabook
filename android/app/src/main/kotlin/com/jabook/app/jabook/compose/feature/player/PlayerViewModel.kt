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

package com.jabook.app.jabook.compose.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
public class PlayerViewModel
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
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        // Get bookId from navigation arguments
        private val args = savedStateHandle.toRoute<PlayerRoute>()
        private val bookId = args.bookId

        // Track if book has been loaded into player
        private var isBookLoaded = false

        // Player Stats for Nerds
        public val playerStats: StateFlow<PlayerStats> = playerController.playerStats

        // Saved position from database (restored on init)
        private var savedPosition: Int = L
        private var savedChapterIndex: Int = 0

        // Chapter repeat mode state
        private val _chapterRepeatMode = MutableStateFlow(ChapterRepeatMode.OFF)
        public val chapterRepeatMode: StateFlow<ChapterRepeatMode> = _chapterRepeatMode.asStateFlow()

        // Track if we've already repeated once (for ONCE mode)
        private var hasRepeatedOnce = false

        // Dynamic Theme Colors
        private val _themeColors = MutableStateFlow<com.jabook.app.jabook.compose.core.theme.PlayerThemeColors?>(null)
        public val themeColors: StateFlow<com.jabook.app.jabook.compose.core.theme.PlayerThemeColors?> = _themeColors.asStateFlow()

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

        // Store lyrics in a separate flow to avoid re-parsing on every seeking
        private val lyricsState = MutableStateFlow<List<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>?>(null)

        /**
         * Combined UI state from book data, playback state, and settings.
         */
        public val uiState: StateFlow<PlayerUiState> =
            combine(
                getBookDetailsUseCase(bookId),
                getChaptersUseCase(bookId),
                playerController.isPlaying,
                playerController.currentPosition,
                playerController.currentChapterIndex,
                settingsRepository.userPreferences,
                userPreferencesRepository.userData.map { it.playbackSpeed },
            ) { args ->
                val book = args[0] as? Book

                @Suppress("UNCHECKED_CAST")
                val chapters = args[1] as List<Chapter>
                val playing = args[2] as Boolean
                val controllerPosition = args[3] as Long
                val controllerChapterIndex = args[4] as Int
                val preferences = args[5] as com.jabook.app.jabook.compose.data.preferences.UserPreferences
                val playbackSpeed = args[6] as Float

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
                        playbackSpeed = playbackSpeed,
                    )
                }
            }.combine(_themeColors) { state, themeColors ->
                if (state is PlayerUiState.Success) {
                    state.copy(themeColors = themeColors)
                } else {
                    state
                }
            }.combine(lyricsState) { state, lyrics ->
                if (state is PlayerUiState.Success) {
                    state.copy(lyrics = lyrics)
                } else {
                    state
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PlayerUiState.Loading,
            )

        // Load lyrics when chapter changes
        init {
            viewModelScope.launch {
                // Monitor chapter changes
                combine(
                    getChaptersUseCase(bookId),
                    playerController.currentChapterIndex,
                ) { chapters, index ->
                    chapters.getOrNull(index)
                }.collect { chapter ->
                    if (chapter?.fileUrl != null) {
                        loadLyrics(chapter.fileUrl!!)
                    } else {
                        lyricsState.value = null
                    }
                }
            }
        }

        private suspend fun loadLyrics(audioPath: String) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val audioFile = java.io.File(audioPath)
                    val lrcFile = java.io.File(audioFile.parent, "${audioFile.nameWithoutExtension}.lrc")

                    if (lrcFile.exists()) {
                        val content = lrcFile.readText()
                        val parsed =
                            com.jabook.app.jabook.compose.feature.player.lyrics.LrcParser
                                .parse(content)
                        if (parsed.isNotEmpty()) {
                            lyricsState.value = parsed
                            return@withContext
                        }
                    }
                    lyricsState.value = null
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Failed to load lyrics", e)
                    lyricsState.value = null
                }
            }
        }

        // Load artwork and extract colors when book changes
        init {
            viewModelScope.launch {
                getBookDetailsUseCase(bookId).collect { book ->
                    if (book?.coverUrl != null) {
                        extractColorsFromCover(book.coverUrl!!)
                    }
                }
            }
        }

        private suspend fun extractColorsFromCover(coverUrl: String) {
            try {
                val loader = SingletonImageLoader.get(context)
                val request =
                    coil3.request
                        .ImageRequest
                        .Builder(context)
                        .data(coverUrl)
                        .allowHardware(false) // Software bitmap required for Palette
                        .build()

                val result = loader.execute(request)
                if (result is coil3.request.SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    val colors =
                        com.jabook.app.jabook.compose.core.theme.DynamicThemeManager.extractColors(
                            bitmap,
                        )
                    _themeColors.value = colors
                }
            } catch (e: Exception) {
                // Ignore errors, keep default theme
                android.util.Log.e("PlayerViewModel", "Failed to extract dynamic colors", e)
            }
        }

        /**
         * Current playback speed from user preferences.
         */
        public val playbackSpeed: StateFlow<Float> =
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
        public val normalizeChapterTitles: StateFlow<Boolean> =
            userPreferencesRepository.userData
                .map { it.normalizeChapterTitles }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = false,
                )

        /**
         * Pitch correction state.
         */
        public val pitchCorrectionEnabled: StateFlow<Boolean> = playerController.pitchCorrectionEnabled

        /**
         * Current sleep timer state.
         */
        public val sleepTimerState: StateFlow<com.jabook.app.jabook.compose.domain.model.SleepTimerState> =
            sleepTimerRepository.timerState

        // Player control methods delegated to controller

        public fun play(...) {
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

        public fun pause(...) {
            playerController.pause()
        }

        public fun seekTo(...) {
            playerController.seekTo(positionMs)
        }

        public fun skipToNext(...) {
            playerController.skipToNext()
        }

        public fun skipToPrevious(...) {
            playerController.skipToPrevious()
        }

        public fun skipToChapter(...) {
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

        public fun seekForward(...) {
            val state = uiState.value
            if (state is PlayerUiState.Success && state.currentChapter != null) {
                val interval: Long = state.forwardInterval
                val newPosition =
                    (playerController.currentPosition.value + interval * 1000)
                        .coerceAtMost(state.currentChapter.duration.inWholeMilliseconds)
                seekTo(newPosition)
            }
        }

        public fun seekBackward(...) {
            val state = uiState.value
            if (state is PlayerUiState.Success) {
                val interval: Long = state.rewindInterval
                val newPosition = (playerController.currentPosition.value - interval * 1000).coerceAtLeast(0)
                seekTo(newPosition)
            }
        }

        public fun setPlaybackSpeed(...) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaybackSpeed(speed)
            }
            viewModelScope.launch {
                playerController.setPlaybackSpeed(speed)
            }
        }

        public fun setPitchCorrectionEnabled(...) {
            playerController.setPitchCorrectionEnabled(enabled)
        }

        public fun startSleepTimer(...) {
            sleepTimerRepository.startTimer(minutes)
        }

        public fun startSleepTimerEndOfChapter(...) {
            sleepTimerRepository.startTimerEndOfChapter()
        }

        public fun cancelSleepTimer(...) {
            sleepTimerRepository.cancelTimer()
        }

        public fun updateBookSeekSettings(
            rewindSeconds: Int?,
            forwardSeconds: Int?,
        ) {
            viewModelScope.launch {
                updateBookSettingsUseCase(bookId, rewindSeconds, forwardSeconds)
            }
        }

        public fun resetBookSeekSettings(...) {
            viewModelScope.launch {
                updateBookSettingsUseCase.resetForBook(bookId)
            }
        }

        /**
         * Initialize player with book data if needed.
         * Restores saved position from database if available.
         */
        public fun initializePlayer(...) {
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

        public fun reorderChapters(...) {
            viewModelScope.launch {
                booksRepository.updateChapterOrder(bookId, newOrderedIds)
            }
        }

        /**
         * Toggle chapter repeat mode: OFF -> ONCE -> INFINITE -> OFF
         */
        public fun toggleChapterRepeat(...) {
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
        public fun onChapterEnded(): Boolean =
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
        public fun onChapterChanged(...) {
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
public enum class ChapterRepeatMode {
    OFF,
    ONCE,
    INFINITE,
}

/**
 * UI state for the Player screen.
 */
public sealed interface PlayerUiState {
    /**
     * Loading state - fetching book data.
     */
    data object Loading : PlayerUiState

    /**
     * Success state with book and playback info.
     */
    public data class Success(
        public val book: Book,
        public val chapters: List<Chapter>,
        public val isPlaying: Boolean,
        public val currentPosition: Long, // milliseconds
        public val currentChapterIndex: Int,
        public val currentChapter: Chapter?,
        public val rewindInterval: Int,
        public val forwardInterval: Int,
        public val playbackSpeed: Float,
        public val themeColors: com.jabook.app.jabook.compose.core.theme.PlayerThemeColors? = null,
        public val lyrics: List<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>? = null,
    ) : PlayerUiState

    /**
     * Error state.
     */
    public data class Error(
        public val message: String,
    ) : PlayerUiState
}
