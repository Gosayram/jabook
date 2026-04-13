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
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.model.toTypedResult
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import com.jabook.app.jabook.compose.domain.model.Result as TypedResult

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
        private val savedStateHandle: SavedStateHandle,
        private val getBookDetailsUseCase: GetBookDetailsUseCase,
        private val getChaptersUseCase: GetChaptersUseCase,
        private val playerController: com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController,
        private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository,
        private val userPreferencesRepository: com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository,
        private val sleepTimerRepository: com.jabook.app.jabook.compose.data.repository.SleepTimerRepository,
        private val updateBookSettingsUseCase: com.jabook.app.jabook.compose.domain.usecase.library.UpdateBookSettingsUseCase,
        private val booksRepository: com.jabook.app.jabook.compose.data.repository.BooksRepository,
        private val playbackPositionRepository: PlaybackPositionRepository,
        private val lyricsRepository: com.jabook.app.jabook.data.lyrics.LyricsRepository,
        private val audioVisualizerStateBridge: com.jabook.app.jabook.audio.AudioVisualizerStateBridge,
        private val loggerFactory: LoggerFactory,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val logger = loggerFactory.get("PlayerViewModel")

        // Get bookId from navigation arguments
        private val args = savedStateHandle.toRoute<PlayerRoute>()
        private val bookId = args.bookId

        private val _effects = MutableSharedFlow<PlayerEffect>(extraBufferCapacity = 8)
        public val effects: SharedFlow<PlayerEffect> = _effects.asSharedFlow()

        // Player Stats for Nerds
        public val playerStats: StateFlow<PlayerStats> = playerController.playerStats
        public val visualizerWaveformData: StateFlow<FloatArray> = audioVisualizerStateBridge.waveformData

        // Saved position from database (restored on init)
        private var savedPosition: Long = 0L
        private var savedChapterIndex: Int = 0
        private var savedPlaybackSpeed: Float = 1.0f
        private var savedSleepTimerMode: String = PlayerStateSnapshotPolicy.MODE_IDLE
        private var lastPersistedPlayerSnapshot: PlayerStateSnapshot? = null
        private var hasRestoredSnapshot: Boolean = false

        // Chapter repeat mode state
        private val chapterRepeatModeState = MutableStateFlow(ChapterRepeatMode.OFF)

        // Track if we've already repeated once (for ONCE mode)
        private var hasRepeatedOnce = false

        // Dynamic Theme Colors
        private val _themeColors = MutableStateFlow<com.jabook.app.jabook.compose.core.theme.PlayerThemeColors?>(null)
        public val themeColors: StateFlow<com.jabook.app.jabook.compose.core.theme.PlayerThemeColors?> =
            _themeColors
                .asStateFlow()

        init {
            restoreStateSnapshot()
            restoreStateSnapshotFromDataStore()
            restorePlaybackSpeedFromSnapshotIfNeeded()
            restoreSleepTimerModeFromSnapshotIfNeeded()

            // CRITICAL: Restore saved position from database on init
            // This ensures position is restored in all scenarios:
            // - User paused and closed app
            // - Device battery died
            // - Phone call interrupted playback
            // - Other system events
            viewModelScope.launch {
                try {
                    val positionResult = playbackPositionRepository.getPosition(bookId).first().toTypedResult()
                    when (positionResult) {
                        is TypedResult.Success -> {
                            positionResult.data?.let { entity ->
                                savedPosition = entity.position
                                savedChapterIndex = entity.trackIndex
                                logger.d {
                                    "Restored position from database: chapter=$savedChapterIndex, position=${savedPosition}ms"
                                }
                            }
                        }
                        is TypedResult.Error -> {
                            logger.w(positionResult.error.cause) {
                                "Failed to restore position: ${positionResult.error.message}"
                            }
                        }
                        is TypedResult.Loading -> {
                            // Loading state, will be updated when ready
                        }
                    }
                } catch (e: Exception) {
                    logger.e({ "Error restoring position from database" }, e)
                }
            }

            // Persist player snapshot for process-death restore.
            viewModelScope.launch {
                combine(uiState, sleepTimerState) { state, timerState -> state to timerState }
                    .collect { (state, timerState) ->
                        if (state is PlayerState.Active) {
                            val snapshot =
                                PlayerStateSnapshotPolicy.capture(
                                    bookId = bookId,
                                    state = state,
                                    sleepTimerState = timerState,
                                )
                            savedStateHandle[STATE_SNAPSHOT_BOOK_ID] = snapshot.bookId
                            savedStateHandle[STATE_SNAPSHOT_POSITION_MS] = snapshot.positionMs
                            savedStateHandle[STATE_SNAPSHOT_CHAPTER_INDEX] = snapshot.chapterIndex
                            savedStateHandle[STATE_SNAPSHOT_PLAYBACK_SPEED] = snapshot.playbackSpeed
                            savedStateHandle[STATE_SNAPSHOT_SLEEP_MODE] = snapshot.sleepTimerMode

                            val persistentSnapshot = PlayerStateSnapshotPolicy.normalizeForPersistence(snapshot)
                            if (PlayerStateSnapshotPolicy.shouldPersistSnapshot(lastPersistedPlayerSnapshot, persistentSnapshot)) {
                                lastPersistedPlayerSnapshot = persistentSnapshot
                                runCatching {
                                    settingsRepository.updatePlayerStateSnapshot(
                                        com.jabook.app.jabook.compose.data.preferences.PlayerStateSnapshotPreference(
                                            bookId = persistentSnapshot.bookId,
                                            positionMs = persistentSnapshot.positionMs,
                                            chapterIndex = persistentSnapshot.chapterIndex,
                                            playbackSpeed = persistentSnapshot.playbackSpeed,
                                            sleepTimerMode = persistentSnapshot.sleepTimerMode,
                                        ),
                                    )
                                }.onFailure { error ->
                                    logger.w(error) { "Failed to persist player snapshot to DataStore" }
                                }
                            }
                        }
                    }
            }
        }

        // Store lyrics in a separate flow to avoid re-parsing on every seeking
        private val lyricsState =
            MutableStateFlow<ImmutableList<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>?>(null)

        // Backpressure guard for seekbar/UI: keep only latest position updates and
        // suppress jittery micro-updates that don't change visible state.
        private val uiPositionFlow: StateFlow<Long> =
            playerController.currentPosition
                .map { it.coerceAtLeast(0L) }
                .distinctUntilChanged { previous, current -> abs(current - previous) < POSITION_UI_EPSILON_MS }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = playerController.currentPosition.value.coerceAtLeast(0L),
                )

        /**
         * Combined UI state from book data, playback state, and settings.
         */
        public val uiState: StateFlow<PlayerState> =
            combine(
                getBookDetailsUseCase(bookId),
                getChaptersUseCase(bookId),
                playerController.isPlaying,
                uiPositionFlow,
                playerController.currentChapterIndex,
                playerController.currentBookId,
                settingsRepository.userPreferences,
                userPreferencesRepository.userData.map { it.playbackSpeed },
                sleepTimerRepository.timerState,
                chapterRepeatModeState,
            ) { args ->
                val book = args[0] as? Book

                @Suppress("UNCHECKED_CAST")
                val chapters = args[1] as List<Chapter>
                val playing = args[2] as Boolean
                val controllerPosition = args[3] as Long
                val controllerChapterIndex = args[4] as Int
                val controllerBookId = args[5] as String?
                val preferences = args[6] as com.jabook.app.jabook.compose.data.preferences.UserPreferences
                val playbackSpeed = args[7] as Float
                val sleepTimerState = args[8] as com.jabook.app.jabook.compose.domain.model.SleepTimerState
                val chapterRepeatMode = args[9] as ChapterRepeatMode

                if (book == null) {
                    PlayerState.Error("Book not found")
                } else {
                    // Calculate effective seek intervals
                    // Priority: Book Override -> Global Setting -> Hardcoded Default
                    val rewindInterval =
                        book.rewindDuration
                            ?: if (preferences.rewindDurationSeconds > 0) preferences.rewindDurationSeconds else 10
                    val forwardInterval =
                        book.forwardDuration
                            ?: if (preferences.forwardDurationSeconds > 0) preferences.forwardDurationSeconds else 30

                    val maxChapterIndex = (chapters.size - 1).coerceAtLeast(0)
                    val safeSavedChapterIndex = savedChapterIndex.coerceIn(0, maxChapterIndex)
                    val isControllerBoundToCurrentBook = controllerBookId == bookId
                    val hasControllerStateForCurrentBook =
                        isControllerBoundToCurrentBook &&
                            (
                                isControllerBoundToCurrentBook ||
                                    controllerPosition > 0L ||
                                    controllerChapterIndex > 0 ||
                                    playing
                            )

                    val chapterIndex =
                        if (hasControllerStateForCurrentBook) {
                            controllerChapterIndex.coerceIn(0, maxChapterIndex)
                        } else {
                            safeSavedChapterIndex
                        }

                    // Prefer controller position only when it's clearly bound to this book;
                    // otherwise keep DB-restored position to avoid transient UI jumps.
                    val position =
                        if (hasControllerStateForCurrentBook) {
                            controllerPosition.coerceAtLeast(0L)
                        } else {
                            savedPosition.coerceAtLeast(0L)
                        }

                    PlayerState.Active(
                        book = book,
                        chapters = chapters.toImmutableList(),
                        isPlaying = playing,
                        currentPosition = position,
                        currentChapterIndex = chapterIndex,
                        currentChapter = chapters.getOrNull(chapterIndex),
                        rewindInterval = rewindInterval,
                        forwardInterval = forwardInterval,
                        playbackSpeed = playbackSpeed,
                        sleepTimerMode = sleepTimerState.toPlayerSleepTimerMode(),
                        sleepTimerRemainingSeconds =
                            (sleepTimerState as? com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active)
                                ?.remainingSeconds,
                        chapterRepeatMode = chapterRepeatMode,
                        volumeBoostLevel =
                            runCatching {
                                com.jabook.app.jabook.audio.processors.VolumeBoostLevel
                                    .valueOf(preferences.volumeBoostLevel)
                            }.getOrElse { com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off },
                        skipSilence = preferences.skipSilence,
                        skipSilenceThresholdDb = preferences.skipSilenceThresholdDb,
                        skipSilenceMinMs = preferences.skipSilenceMinMs,
                        skipSilenceMode = preferences.skipSilenceMode,
                        normalizeVolume = preferences.normalizeVolume,
                        speechEnhancer = preferences.speechEnhancer,
                        autoVolumeLeveling = preferences.autoVolumeLeveling,
                    )
                }
            }.combine(_themeColors) { state, themeColors ->
                if (state is PlayerState.Active) {
                    state.copy(themeColors = themeColors)
                } else {
                    state
                }
            }.combine(lyricsState) { state, lyrics ->
                if (state is PlayerState.Active) {
                    state.copy(lyrics = lyrics)
                } else {
                    state
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PlayerState.Loading,
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
                        loadLyrics(chapter.fileUrl)
                    } else {
                        lyricsState.value = null
                    }
                }
            }
        }

        private companion object {
            private const val POSITION_UI_EPSILON_MS: Long = 150L
        }

        private suspend fun loadLyrics(audioPath: String) {
            try {
                // Use the repository to get lyrics (includes fallback to demo lyrics)
                val lyrics = lyricsRepository.getLyrics(audioPath)
                if (lyrics.isNotEmpty()) {
                    lyricsState.value = lyrics.toImmutableList()
                } else {
                    lyricsState.value = null
                }
            } catch (e: Exception) {
                logger.e({ "Failed to load lyrics" }, e)
                lyricsState.value = null
            }
        }

        // Load artwork and extract colors when book changes
        init {
            viewModelScope.launch {
                getBookDetailsUseCase(bookId).collect { book ->
                    if (book?.coverUrl != null) {
                        extractColorsFromCover(book.coverUrl)
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
                logger.e({ "Failed to extract dynamic colors" }, e)
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
         * Audio Processing Settings to be exposed to UI.
         * Maps UserPreferences to a UI-friendly data class or simply exposes necessary fields.
         * For simplicity, exposing UserPreferences directly or a mapped state would work.
         * Let's map to a local data class for cleaner UI usage.
         */
        public data class AudioSettingsState(
            val volumeBoostLevel: com.jabook.app.jabook.audio.processors.VolumeBoostLevel =
                com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off,
            val skipSilence: Boolean = false,
            val skipSilenceThresholdDb: Float = -32.0f,
            val skipSilenceMinMs: Int = 250,
            val skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode =
                com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP,
            val normalizeVolume: Boolean = true,
            val speechEnhancer: Boolean = false,
            val autoVolumeLeveling: Boolean = false,
        )

        public val audioSettings: StateFlow<AudioSettingsState> =
            settingsRepository.userPreferences
                .map { prefs ->
                    AudioSettingsState(
                        volumeBoostLevel =
                            try {
                                if (prefs.volumeBoostLevel.isNotEmpty()) {
                                    com.jabook.app.jabook.audio.processors.VolumeBoostLevel
                                        .valueOf(prefs.volumeBoostLevel)
                                } else {
                                    com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off
                                }
                            } catch (e: Exception) {
                                com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off
                            },
                        skipSilence = prefs.skipSilence,
                        skipSilenceThresholdDb = prefs.skipSilenceThresholdDb,
                        skipSilenceMinMs = prefs.skipSilenceMinMs,
                        skipSilenceMode = prefs.skipSilenceMode,
                        normalizeVolume = prefs.normalizeVolume,
                        speechEnhancer = prefs.speechEnhancer,
                        autoVolumeLeveling = prefs.autoVolumeLeveling,
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = AudioSettingsState(),
                )

        /**
         * Current sleep timer state.
         */
        public val sleepTimerState: StateFlow<com.jabook.app.jabook.compose.domain.model.SleepTimerState> =
            sleepTimerRepository.timerState

        // Unified player command dispatcher (incremental PlayerIntent migration)
        public fun dispatch(intent: PlayerIntent) {
            logger.d { "PlayerIntent received: $intent" }
            val reducedState = PlayerReducer.reduce(uiState.value, intent)
            if (uiState.value is PlayerState.Loading && reducedState is PlayerState.Loading && intent.isPlaybackControlIntent()) {
                emitEffect(PlayerEffect.ShowSnackbar("Player is not ready yet"))
                return
            }
            when (intent) {
                PlayerIntent.InitializePlayer -> initializePlayer()
                PlayerIntent.TogglePlayPause -> {
                    val currentState = uiState.value as? PlayerState.Active
                    val targetState = reducedState as? PlayerState.Active
                    if (currentState == null || targetState == null || currentState == targetState) {
                        logger.d { "TogglePlayPause produced no state change, skipping command" }
                        return
                    }
                    if (targetState.isPlaying) {
                        play()
                    } else {
                        pause()
                    }
                }
                PlayerIntent.Play -> {
                    if (reducedState == uiState.value) return
                    play()
                }
                PlayerIntent.Pause -> {
                    if (reducedState == uiState.value) return
                    pause()
                }
                PlayerIntent.SkipNext -> skipToNext()
                PlayerIntent.SkipPrevious -> skipToPrevious()
                is PlayerIntent.SeekTo -> {
                    val reducedPosition = (reducedState as? PlayerState.Active)?.currentPosition ?: intent.positionMs
                    seekTo(reducedPosition)
                }
                PlayerIntent.SeekForward -> {
                    val reducedPosition = (reducedState as? PlayerState.Active)?.currentPosition ?: return
                    seekTo(reducedPosition)
                }
                PlayerIntent.SeekBackward -> {
                    val reducedPosition = (reducedState as? PlayerState.Active)?.currentPosition ?: return
                    seekTo(reducedPosition)
                }
                is PlayerIntent.SelectChapter -> {
                    val reducedChapterIndex =
                        (reducedState as? PlayerState.Active)?.currentChapterIndex ?: intent.chapterIndex
                    skipToChapter(reducedChapterIndex)
                }
                PlayerIntent.ToggleChapterRepeat -> {
                    val targetMode = (reducedState as? PlayerState.Active)?.chapterRepeatMode ?: return
                    if (targetMode == chapterRepeatModeState.value) return
                    chapterRepeatModeState.value = targetMode
                    hasRepeatedOnce = PlayerReducer.reduceChapterChanged()
                }
                PlayerIntent.InitializeVisualizer -> initializeVisualizer()
                is PlayerIntent.SetVisualizerEnabled -> setVisualizerEnabled(intent.enabled)
                is PlayerIntent.SetPlaybackSpeed -> {
                    val reducedSpeed = (reducedState as? PlayerState.Active)?.playbackSpeed ?: return
                    setPlaybackSpeed(reducedSpeed)
                }
                is PlayerIntent.SetPitchCorrectionEnabled -> setPitchCorrectionEnabled(intent.enabled)
                is PlayerIntent.StartSleepTimer -> {
                    if (reducedState == uiState.value) {
                        logger.d { "Sleep timer state unchanged by reducer, skipping command" }
                        return
                    }
                    startSleepTimer(intent.minutes)
                }
                PlayerIntent.StartSleepTimerEndOfChapter -> {
                    if (reducedState == uiState.value) {
                        logger.d { "Sleep timer end-of-chapter already active, skipping command" }
                        return
                    }
                    startSleepTimerEndOfChapter()
                }
                PlayerIntent.StartSleepTimerEndOfTrack -> {
                    if (reducedState == uiState.value) {
                        logger.d { "Sleep timer end-of-track already active, skipping command" }
                        return
                    }
                    startSleepTimerEndOfTrack()
                }
                PlayerIntent.CancelSleepTimer -> {
                    if (reducedState == uiState.value) {
                        logger.d { "Sleep timer already idle, skipping cancel command" }
                        return
                    }
                    cancelSleepTimer()
                }
                is PlayerIntent.UpdateBookSeekSettings ->
                    if (reducedState == uiState.value) {
                        logger.d { "Book seek settings unchanged by reducer, skipping command" }
                        return
                    } else {
                        updateBookSeekSettings(
                            rewindSeconds = intent.rewindSeconds,
                            forwardSeconds = intent.forwardSeconds,
                        )
                    }
                PlayerIntent.ResetBookSeekSettings -> resetBookSeekSettings()
                is PlayerIntent.UpdateAudioSettings ->
                    if (reducedState == uiState.value) {
                        logger.d { "Audio settings intent has no changes, skipping command" }
                        return
                    } else {
                        val targetState = reducedState as? PlayerState.Active ?: return
                        updateAudioSettings(
                            volumeBoostLevel = targetState.volumeBoostLevel,
                            skipSilence = targetState.skipSilence,
                            skipSilenceThresholdDb = targetState.skipSilenceThresholdDb,
                            skipSilenceMinMs = targetState.skipSilenceMinMs,
                            skipSilenceMode = targetState.skipSilenceMode,
                            normalizeVolume = targetState.normalizeVolume,
                            speechEnhancer = targetState.speechEnhancer,
                            autoVolumeLeveling = targetState.autoVolumeLeveling,
                        )
                    }
                is PlayerIntent.ReportError -> {
                    val reason = (reducedState as? PlayerState.Error)?.message ?: intent.reason
                    emitEffect(PlayerEffect.ShowError(reason))
                }
            }
        }

        // Player control methods delegated to controller

        public fun play() {
            logger.d { "Action: Play requested" }
            val state = uiState.value
            if (state is PlayerState.Active) {
                // Ensure book is loaded before playing
                val isControllerBoundToCurrentBook = playerController.currentBookId.value == bookId
                if (!isControllerBoundToCurrentBook) {
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
                    }
                } else {
                    playerController.play()
                }
            } else {
                emitEffect(PlayerEffect.ShowSnackbar("Player is not ready yet"))
            }
        }

        public fun pause() {
            logger.d { "Action: Pause requested" }
            playerController.pause()
        }

        public fun seekTo(positionMs: Long) {
            val state = uiState.value as? PlayerState.Active
            val chapterDurationMs = state?.currentChapter?.duration?.inWholeMilliseconds
            val clampedPositionMs = PlayerIntentGuardPolicy.clampSeekPosition(positionMs, chapterDurationMs)
            logger.d { "Action: Seek requested to ${positionMs}ms (clamped=${clampedPositionMs}ms)" }
            playerController.seekTo(clampedPositionMs)
        }

        public fun skipToNext() {
            logger.d { "Action: Skip Next requested" }
            playerController.skipToNext()
        }

        public fun skipToPrevious() {
            logger.d { "Action: Skip Previous requested" }
            playerController.skipToPrevious()
        }

        public fun skipToChapter(chapterIndex: Int) {
            logger.d { "Action: Skip to Chapter index $chapterIndex requested" }
            playerController.skipToChapter(chapterIndex)
            // Reset repeat flag when manually changing chapters
            onChapterChanged()
            // Always start playback when user selects a chapter from the chapter selector
            // Use coroutine to ensure chapter switch completes before starting playback
            viewModelScope.launch {
                kotlinx.coroutines.delay(100L) // Small delay to ensure chapter switch completes
                playerController.play()
            }
        }

        public fun seekForward() {
            logger.d { "Action: Seek Forward requested" }
            val state = uiState.value
            if (state is PlayerState.Active && state.currentChapter != null) {
                val interval: Long = state.forwardInterval.toLong()
                val newPosition =
                    (playerController.currentPosition.value + interval * 1000)
                        .coerceAtMost(state.currentChapter.duration.inWholeMilliseconds)
                seekTo(newPosition)
            }
        }

        public fun seekBackward() {
            logger.d { "Action: Seek Backward requested" }
            val state = uiState.value
            if (state is PlayerState.Active) {
                val interval: Long = state.rewindInterval.toLong()
                val newPosition = (playerController.currentPosition.value - interval * 1000).coerceAtLeast(0)
                seekTo(newPosition)
            }
        }

        public fun setPlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                runCatching { userPreferencesRepository.setPlaybackSpeed(speed) }
                    .onFailure { error ->
                        logger.e({ "Failed to persist playback speed" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to save playback speed"))
                    }
            }
            viewModelScope.launch {
                runCatching { playerController.setPlaybackSpeed(speed) }
                    .onFailure { error ->
                        logger.e({ "Failed to set playback speed on player" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to update playback speed"))
                    }
            }
        }

        public fun setPitchCorrectionEnabled(enabled: Boolean) {
            playerController.setPitchCorrectionEnabled(enabled)
        }

        public fun initializeVisualizer() {
            playerController.initializeVisualizer()
        }

        public fun setVisualizerEnabled(enabled: Boolean) {
            playerController.setVisualizerEnabled(enabled)
        }

        public fun startSleepTimer(minutes: Int) {
            if (!PlayerIntentGuardPolicy.shouldStartFixedSleepTimer(sleepTimerState.value, minutes)) {
                logger.d { "Sleep timer already active with same target, skipping restart" }
                return
            }
            sleepTimerRepository.startTimer(minutes)
        }

        public fun startSleepTimerEndOfChapter() {
            if (!PlayerIntentGuardPolicy.shouldStartEndOfChapter(sleepTimerState.value)) {
                logger.d { "Sleep timer is already in end-of-chapter mode, skipping restart" }
                return
            }
            sleepTimerRepository.startTimerEndOfChapter()
        }

        public fun startSleepTimerEndOfTrack() {
            if (!PlayerIntentGuardPolicy.shouldStartEndOfTrack(sleepTimerState.value)) {
                logger.d { "Sleep timer is already in end-of-track mode, skipping restart" }
                return
            }
            sleepTimerRepository.startTimerEndOfTrack()
        }

        public fun cancelSleepTimer() {
            sleepTimerRepository.cancelTimer()
        }

        public fun updateBookSeekSettings(
            rewindSeconds: Int?,
            forwardSeconds: Int?,
        ) {
            viewModelScope.launch {
                runCatching { updateBookSettingsUseCase(bookId, rewindSeconds, forwardSeconds) }
                    .onFailure { error ->
                        logger.e({ "Failed to update book seek settings" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to update seek settings"))
                    }
            }
        }

        public fun resetBookSeekSettings() {
            viewModelScope.launch {
                runCatching { updateBookSettingsUseCase.resetForBook(bookId) }
                    .onFailure { error ->
                        logger.e({ "Failed to reset book seek settings" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to reset seek settings"))
                    }
            }
        }

        public fun updateAudioSettings(
            volumeBoostLevel: com.jabook.app.jabook.audio.processors.VolumeBoostLevel? = null,
            skipSilence: Boolean? = null,
            skipSilenceThresholdDb: Float? = null,
            skipSilenceMinMs: Int? = null,
            skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode? = null,
            normalizeVolume: Boolean? = null,
            speechEnhancer: Boolean? = null,
            autoVolumeLeveling: Boolean? = null,
        ) {
            viewModelScope.launch {
                runCatching {
                    settingsRepository.updateAudioSettings(
                        volumeBoost = volumeBoostLevel?.name,
                        skipSilence = skipSilence,
                        skipSilenceThresholdDb = skipSilenceThresholdDb,
                        skipSilenceMinMs = skipSilenceMinMs,
                        skipSilenceMode = skipSilenceMode,
                        normalizeVolume = normalizeVolume,
                        speechEnhancer = speechEnhancer,
                        autoVolumeLeveling = autoVolumeLeveling,
                    )
                }.onFailure { error ->
                    logger.e({ "Failed to update audio settings" }, error)
                    dispatch(PlayerIntent.ReportError("Failed to update audio settings"))
                }
            }
        }

        /**
         * Initialize player with book data if needed.
         * Restores saved position from database if available.
         */
        public fun initializePlayer() {
            val state = uiState.value
            val isControllerBoundToCurrentBook = playerController.currentBookId.value == bookId
            if (state is PlayerState.Active && !isControllerBoundToCurrentBook) {
                val filePaths = state.chapters.mapNotNull { it.fileUrl }
                if (filePaths.isNotEmpty()) {
                    // Single source-of-truth: initialize from unified uiState (controller/service-driven
                    // when bound, DB-restored only as bootstrap fallback before controller binds).
                    val initialChapterIndex = state.currentChapterIndex
                    val initialPosition = state.currentPosition

                    logger.d {
                        "Initializing player: chapter=$initialChapterIndex, position=${initialPosition}ms"
                    }

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
                }
            }
        }

        public fun reorderChapters(newOrderedIds: List<String>) {
            viewModelScope.launch {
                runCatching { booksRepository.updateChapterOrder(bookId, newOrderedIds) }
                    .onFailure { error ->
                        logger.e({ "Failed to reorder chapters" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to reorder chapters"))
                    }
            }
        }

        private fun emitEffect(effect: PlayerEffect) {
            _effects.tryEmit(effect)
        }

        /**
         * Handle chapter end - check if we need to repeat.
         * Called by AudioPlayerController when chapter ends.
         *
         * @return true if chapter should be repeated, false to continue to next
         */
        public fun onChapterEnded(): Boolean {
            val reduction =
                PlayerReducer.reduceChapterEnded(
                    mode = chapterRepeatModeState.value,
                    hasRepeatedOnce = hasRepeatedOnce,
                )
            hasRepeatedOnce = reduction.hasRepeatedOnce
            return reduction.shouldRepeat
        }

        /**
         * Reset repeat flag when chapter changes manually.
         */
        public fun onChapterChanged() {
            hasRepeatedOnce = PlayerReducer.reduceChapterChanged()
        }

        private fun restoreStateSnapshot() {
            val snapshotBookId: String = savedStateHandle[STATE_SNAPSHOT_BOOK_ID] ?: return
            if (snapshotBookId != bookId) return

            savedPosition = (savedStateHandle[STATE_SNAPSHOT_POSITION_MS] ?: 0L).coerceAtLeast(0L)
            savedChapterIndex = (savedStateHandle[STATE_SNAPSHOT_CHAPTER_INDEX] ?: 0).coerceAtLeast(0)
            savedPlaybackSpeed = (savedStateHandle[STATE_SNAPSHOT_PLAYBACK_SPEED] ?: 1.0f).coerceAtLeast(0f)
            savedSleepTimerMode = savedStateHandle[STATE_SNAPSHOT_SLEEP_MODE] ?: PlayerStateSnapshotPolicy.MODE_IDLE
            hasRestoredSnapshot = true

            logger.d {
                "Restored player snapshot: chapter=$savedChapterIndex, " +
                    "position=${savedPosition}ms, speed=$savedPlaybackSpeed, sleepMode=$savedSleepTimerMode"
            }
        }

        private fun restoreStateSnapshotFromDataStore() {
            viewModelScope.launch {
                if (savedChapterIndex > 0 || savedPosition > 0L) return@launch
                val snapshot = settingsRepository.playerStateSnapshot.first() ?: return@launch
                if (snapshot.bookId != bookId) return@launch
                savedPosition = snapshot.positionMs.coerceAtLeast(0L)
                savedChapterIndex = snapshot.chapterIndex.coerceAtLeast(0)
                savedPlaybackSpeed = snapshot.playbackSpeed.coerceAtLeast(0f)
                savedSleepTimerMode = snapshot.sleepTimerMode.ifBlank { PlayerStateSnapshotPolicy.MODE_IDLE }
                hasRestoredSnapshot = true
                logger.d {
                    "Restored player snapshot from DataStore: chapter=$savedChapterIndex, " +
                        "position=${savedPosition}ms, speed=$savedPlaybackSpeed, sleepMode=$savedSleepTimerMode"
                }
            }
        }

        private fun restorePlaybackSpeedFromSnapshotIfNeeded() {
            viewModelScope.launch {
                if (!hasRestoredSnapshot || savedPlaybackSpeed <= 0f) return@launch
                runCatching {
                    val currentSpeed = userPreferencesRepository.userData.first().playbackSpeed
                    if (kotlin.math.abs(currentSpeed - savedPlaybackSpeed) > 0.01f) {
                        userPreferencesRepository.setPlaybackSpeed(savedPlaybackSpeed)
                    }
                }.onFailure { error ->
                    logger.w(error) { "Failed to restore playback speed from player snapshot" }
                }
            }
        }

        private fun restoreSleepTimerModeFromSnapshotIfNeeded() {
            viewModelScope.launch {
                if (!hasRestoredSnapshot) return@launch
                when (savedSleepTimerMode) {
                    PlayerStateSnapshotPolicy.MODE_END_OF_CHAPTER -> {
                        if (PlayerIntentGuardPolicy.shouldStartEndOfChapter(sleepTimerState.value)) {
                            sleepTimerRepository.startTimerEndOfChapter()
                        }
                    }
                    PlayerStateSnapshotPolicy.MODE_END_OF_TRACK -> {
                        if (PlayerIntentGuardPolicy.shouldStartEndOfTrack(sleepTimerState.value)) {
                            sleepTimerRepository.startTimerEndOfTrack()
                        }
                    }
                    PlayerStateSnapshotPolicy.MODE_ACTIVE -> {
                        // Remaining seconds are intentionally not persisted in the snapshot.
                        logger.d { "Skipping restore for fixed sleep timer mode due to missing remaining seconds" }
                    }
                    PlayerStateSnapshotPolicy.MODE_IDLE -> Unit
                    else -> Unit
                }
            }
        }

        private fun PlayerIntent.isPlaybackControlIntent(): Boolean =
            when (this) {
                PlayerIntent.TogglePlayPause,
                PlayerIntent.Play,
                PlayerIntent.Pause,
                PlayerIntent.SkipNext,
                PlayerIntent.SkipPrevious,
                is PlayerIntent.SeekTo,
                PlayerIntent.SeekForward,
                PlayerIntent.SeekBackward,
                is PlayerIntent.SelectChapter,
                PlayerIntent.ToggleChapterRepeat,
                PlayerIntent.InitializeVisualizer,
                is PlayerIntent.SetVisualizerEnabled,
                is PlayerIntent.SetPlaybackSpeed,
                is PlayerIntent.SetPitchCorrectionEnabled,
                is PlayerIntent.StartSleepTimer,
                PlayerIntent.StartSleepTimerEndOfChapter,
                PlayerIntent.StartSleepTimerEndOfTrack,
                PlayerIntent.CancelSleepTimer,
                is PlayerIntent.UpdateBookSeekSettings,
                PlayerIntent.ResetBookSeekSettings,
                is PlayerIntent.UpdateAudioSettings,
                -> true
                PlayerIntent.InitializePlayer,
                is PlayerIntent.ReportError,
                -> false
            }

        private fun com.jabook.app.jabook.compose.domain.model.SleepTimerState.toPlayerSleepTimerMode(): PlayerSleepTimerMode =
            when (this) {
                com.jabook.app.jabook.compose.domain.model.SleepTimerState.Idle -> PlayerSleepTimerMode.IDLE
                is com.jabook.app.jabook.compose.domain.model.SleepTimerState.Active -> PlayerSleepTimerMode.FIXED
                com.jabook.app.jabook.compose.domain.model.SleepTimerState.EndOfChapter -> PlayerSleepTimerMode.END_OF_CHAPTER
                is com.jabook.app.jabook.compose.domain.model.SleepTimerState.EndOfTrack -> PlayerSleepTimerMode.END_OF_TRACK
            }
    }

private const val STATE_SNAPSHOT_BOOK_ID: String = "player_snapshot.book_id"
private const val STATE_SNAPSHOT_POSITION_MS: String = "player_snapshot.position_ms"
private const val STATE_SNAPSHOT_CHAPTER_INDEX: String = "player_snapshot.chapter_index"
private const val STATE_SNAPSHOT_PLAYBACK_SPEED: String = "player_snapshot.playback_speed"
private const val STATE_SNAPSHOT_SLEEP_MODE: String = "player_snapshot.sleep_mode"

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
public sealed interface PlayerState {
    /**
     * Loading state - fetching book data.
     */
    public data object Loading : PlayerState

    /**
     * Success state with book and playback info.
     */
    @Immutable
    public data class Active(
        val book: Book,
        val chapters: ImmutableList<Chapter>,
        val isPlaying: Boolean,
        val currentPosition: Long, // milliseconds
        val currentChapterIndex: Int,
        val currentChapter: Chapter?,
        val rewindInterval: Int,
        val forwardInterval: Int,
        val playbackSpeed: Float,
        val sleepTimerMode: PlayerSleepTimerMode,
        val sleepTimerRemainingSeconds: Int?,
        val chapterRepeatMode: ChapterRepeatMode,
        val volumeBoostLevel: com.jabook.app.jabook.audio.processors.VolumeBoostLevel =
            com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off,
        val skipSilence: Boolean = false,
        val skipSilenceThresholdDb: Float = -32.0f,
        val skipSilenceMinMs: Int = 250,
        val skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode =
            com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SKIP,
        val normalizeVolume: Boolean = true,
        val speechEnhancer: Boolean = false,
        val autoVolumeLeveling: Boolean = false,
        val themeColors: com.jabook.app.jabook.compose.core.theme.PlayerThemeColors? = null,
        val lyrics: ImmutableList<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>? = null,
    ) : PlayerState

    /**
     * Error state.
     */
    @Immutable
    public data class Error(
        val message: String,
    ) : PlayerState
}

public enum class PlayerSleepTimerMode {
    IDLE,
    FIXED,
    END_OF_CHAPTER,
    END_OF_TRACK,
}
