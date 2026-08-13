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
import androidx.media3.common.Player
import androidx.navigation.toRoute
import coil3.SingletonImageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.HoldToBoostPolicy
import com.jabook.app.jabook.audio.SleepTimerPersistence
import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.audio.processors.EqContextRecommendationPolicy
import com.jabook.app.jabook.audio.processors.SpeedMemoryHierarchy
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.model.toTypedResult
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import com.jabook.app.jabook.compose.domain.model.Result as TypedResult

internal fun sortChaptersForPlayback(chapters: List<Chapter>): List<Chapter> =
    chapters
        .filter {
            !it.fileUrl.isNullOrBlank()
        }.sortedBy(Chapter::chapterIndex)

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
        private val bookmarkRepository: com.jabook.app.jabook.compose.data.repository.BookmarkRepository,
        private val playbackPositionRepository: PlaybackPositionRepository,
        private val lyricsRepository: com.jabook.app.jabook.data.lyrics.LyricsRepository,
        private val audioVisualizerStateBridge: com.jabook.app.jabook.audio.AudioVisualizerStateBridge,
        private val listeningSessionRepository: ListeningSessionRepository,
        private val loggerFactory: LoggerFactory,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val logger = loggerFactory.get("PlayerViewModel")
        private var holdToBoostPolicy = HoldToBoostPolicy(boostSpeed = DEFAULT_HOLD_TO_BOOST_SPEED)

        // Get bookId from navigation arguments
        private val args = savedStateHandle.toRoute<PlayerRoute>()
        private val bookId = args.bookId
        private val initialChapterIndexOverride = args.chapterIndex

        private val _effects = Channel<PlayerEffect>(Channel.BUFFERED)
        public val effects: PlayerEventFlowContract = _effects.receiveAsFlow()
        private val commandChannel: Channel<PlayerCommand> = Channel(Channel.BUFFERED)
        private val commandFlow: PlayerCommandFlowContract = commandChannel.receiveAsFlow()
        private val commandExecutor =
            PlayerCommandExecutor(
                initializePlayer = ::initializePlayer,
                play = ::play,
                pause = ::pause,
                skipToNext = ::skipToNext,
                skipToPrevious = ::skipToPrevious,
                seekTo = ::seekTo,
                skipToChapter = ::skipToChapter,
                initializeVisualizer = ::initializeVisualizer,
                setVisualizerEnabled = ::setVisualizerEnabled,
                setPlaybackSpeed = ::setPlaybackSpeed,
                setPitchCorrectionEnabled = ::setPitchCorrectionEnabled,
                startSleepTimer = ::startSleepTimer,
                startSleepTimerEndOfChapter = ::startSleepTimerEndOfChapter,
                startSleepTimerEndOfTrack = ::startSleepTimerEndOfTrack,
                cancelSleepTimer = ::cancelSleepTimer,
                updateBookSeekSettings = ::updateBookSeekSettings,
                resetBookSeekSettings = ::resetBookSeekSettings,
                updateAudioSettings = ::updateAudioSettings,
            )

        // Player Stats for Nerds
        public val playerStats: StateFlow<PlayerStats> = playerController.playerStats
        public val visualizerWaveformData: StateFlow<FloatArray> = audioVisualizerStateBridge.waveformData
        public val isAudioOffloaded: StateFlow<Boolean> = audioVisualizerStateBridge.isAudioOffloaded
        public val visualizerMode: StateFlow<Int> =
            settingsRepository.audioVisualizerMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = 0,
                )
        private val _seekbarWaveformData = MutableStateFlow(FloatArray(SEEKBAR_WAVEFORM_CACHE_SIZE))
        public val seekbarWaveformData: StateFlow<FloatArray> = _seekbarWaveformData.asStateFlow()
        public val bookmarks: StateFlow<List<BookmarkItem>> =
            bookmarkRepository
                .observeBookmarks(bookId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        private var lastPersistedPlayerSnapshot: PlayerStateSnapshot? = null
        private val restoredBootstrapSnapshot = MutableStateFlow<RestoredBootstrapSnapshot?>(null)
        private val isPlaybackRestoreReady = MutableStateFlow(false)
        private var hasShownSleepTimerResumeHint: Boolean = false
        private var hasShownEqRecommendation: Boolean = false
        private var hasShownSmartResumeRecapHint: Boolean = false
        private var hasShownResumeAfterLongPause: Boolean = false
        private var hasTriggeredSeriesAutoplay: Boolean = false
        private var autoplayDismissedUntilChapterChange: Boolean = false
        private var seriesAutoplayJob: Job? = null

        private val _nextBookAutoplayState = MutableStateFlow<NextBookAutoplayState?>(null)
        public val nextBookAutoplayState: StateFlow<NextBookAutoplayState?> = _nextBookAutoplayState.asStateFlow()

        // Resume after long pause dialog state (TASK-PLAYER-38)
        private val _resumeAfterLongPauseState = MutableStateFlow<ResumeAfterLongPauseData?>(null)
        public val resumeAfterLongPauseState: StateFlow<ResumeAfterLongPauseData?> =
            _resumeAfterLongPauseState.asStateFlow()

        // Chapter repeat mode state
        private val chapterRepeatModeState = MutableStateFlow(ChapterRepeatMode.OFF)

        // AB repeat state
        private val _abRepeatState = MutableStateFlow(ABRepeatState())
        public val abRepeatState: StateFlow<ABRepeatState> = _abRepeatState.asStateFlow()

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
            observeSleepTimerResumeHint()
            observePhoneCallBookmarkHint()
            observeSmartResumeSuggestion()
            observeHoldToBoostSpeedSetting()
            observeResumeAfterLongPause()
            observeSeekbarWaveformCache()
            observeABRepeatPosition()
            observeEqRecommendation()

            viewModelScope.launch {
                commandFlow.collect { command ->
                    commandExecutor.execute(command)
                }
            }

            // CRITICAL: Restore saved position from database on init
            // This ensures position is restored in all scenarios:
            // - User paused and closed app
            // - Device battery died
            // - Phone call interrupted playback
            // - Other system events
            viewModelScope.launch {
                try {
                    val positionResult =
                        playbackPositionRepository
                            .getPosition(bookId)
                            .firstTerminalResult()
                            .toTypedResult()
                    when (positionResult) {
                        is TypedResult.Success -> {
                            positionResult.data?.let { entity ->
                                val currentSnapshot = restoredBootstrapSnapshot.value
                                restoredBootstrapSnapshot.value =
                                    RestoredBootstrapSnapshot(
                                        positionMs = entity.position.coerceAtLeast(0L),
                                        chapterIndex = entity.trackIndex.coerceAtLeast(0),
                                        playbackSpeed = currentSnapshot?.playbackSpeed ?: 1.0f,
                                        sleepTimerMode = currentSnapshot?.sleepTimerMode ?: PlayerStateSnapshotPolicy.MODE_IDLE,
                                        hasRestoredSpeed = currentSnapshot?.hasRestoredSpeed ?: false,
                                    )
                                logger.d {
                                    "Restored position from database: chapter=${entity.trackIndex}, position=${entity.position}ms"
                                }
                            }
                        }
                        is TypedResult.Error -> {
                            logger.w(positionResult.error.cause) {
                                "Failed to restore position: ${positionResult.error.message}"
                            }
                        }
                        is TypedResult.Loading -> Unit
                    }
                } catch (e: Exception) {
                    logger.e({ "Error restoring position from database" }, e)
                } finally {
                    isPlaybackRestoreReady.value = true
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
        public val uiState: PlayerStateFlowContract =
            combine(
                getBookDetailsUseCase(bookId),
                getChaptersUseCase(bookId).map(::sortChaptersForPlayback),
                playerController.isPlaying,
                uiPositionFlow,
                playerController.currentChapterIndex,
                playerController.currentBookId,
                settingsRepository.userPreferences,
                userPreferencesRepository.userData.map { it.playbackSpeed },
                sleepTimerRepository.timerState,
                chapterRepeatModeState,
                restoredBootstrapSnapshot,
                isPlaybackRestoreReady,
            ) { args ->
                val isRestoreReady = args[11] as Boolean
                if (!isRestoreReady) return@combine PlayerState.Loading
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
                } else if (chapters.isEmpty()) {
                    PlayerState.Error(context.getString(R.string.noChaptersFoundInSearch))
                } else {
                    // Calculate effective seek intervals
                    // Priority: Book Override -> Global Setting -> Hardcoded Default
                    val rewindInterval =
                        book.rewindDuration
                            ?: if (preferences.rewindDurationSeconds > 0) preferences.rewindDurationSeconds else 10
                    val forwardInterval =
                        book.forwardDuration
                            ?: if (preferences.forwardDurationSeconds > 0) preferences.forwardDurationSeconds else 30
                    val defaultRewindInterval =
                        if (preferences.rewindDurationSeconds > 0) {
                            preferences.rewindDurationSeconds
                        } else {
                            10
                        }
                    val defaultForwardInterval =
                        if (preferences.forwardDurationSeconds > 0) {
                            preferences.forwardDurationSeconds
                        } else {
                            30
                        }

                    val maxChapterIndex = (chapters.size - 1).coerceAtLeast(0)
                    val bootstrapSnapshot = args[10] as RestoredBootstrapSnapshot?
                    val safeSavedChapterIndex = (bootstrapSnapshot?.chapterIndex ?: 0).coerceIn(0, maxChapterIndex)
                    val isControllerBoundToCurrentBook = controllerBookId == bookId
                    // Once controller is bound to this book, it is the single source of truth
                    // even when position/chapter are zero (freshly initialized state).
                    val hasControllerStateForCurrentBook = isControllerBoundToCurrentBook

                    val chapterIndex =
                        if (hasControllerStateForCurrentBook) {
                            controllerChapterIndex.coerceIn(0, maxChapterIndex)
                        } else if (initialChapterIndexOverride != null) {
                            initialChapterIndexOverride.coerceIn(0, maxChapterIndex)
                        } else {
                            safeSavedChapterIndex
                        }

                    // Prefer controller position only when it's clearly bound to this book;
                    // otherwise keep DB-restored position to avoid transient UI jumps.
                    val position =
                        if (hasControllerStateForCurrentBook) {
                            controllerPosition.coerceAtLeast(0L)
                        } else if (initialChapterIndexOverride != null) {
                            0L
                        } else {
                            (bootstrapSnapshot?.positionMs ?: 0L).coerceAtLeast(0L)
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
                        defaultRewindInterval = defaultRewindInterval,
                        defaultForwardInterval = defaultForwardInterval,
                        hasBookSeekOverride = book.rewindDuration != null || book.forwardDuration != null,
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

        init {
            observeChapterLyrics()
            observeSeriesAutoplayTrigger()
        }

        private companion object {
            private const val POSITION_UI_EPSILON_MS: Long = 150L
            private const val POSITION_AUTOPLAY_EVAL_BUCKET_MS: Long = 250L
            private const val AUTOPLAY_COUNTDOWN_SECONDS: Int = 10
        }

        public data class NextBookAutoplayState(
            val nextBook: Book,
            val secondsLeft: Int,
            val totalSeconds: Int,
        )

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun observeChapterLyrics() {
            viewModelScope.launch {
                combine(
                    getChaptersUseCase(bookId).map(::sortChaptersForPlayback),
                    playerController.currentChapterIndex,
                ) { chapters, index ->
                    chapters.getOrNull(index)?.fileUrl
                }.distinctUntilChanged()
                    .flatMapLatest { fileUrl ->
                        if (fileUrl.isNullOrBlank()) {
                            flowOf<ImmutableList<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>?>(null)
                        } else {
                            flow<ImmutableList<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>?> {
                                emit(loadLyricsOrNull(fileUrl))
                            }
                        }
                    }.collect { lyrics ->
                        lyricsState.value = lyrics
                    }
            }
        }

        private fun observeSeriesAutoplayTrigger() {
            viewModelScope.launch {
                val throttledPositionFlow =
                    playerController.currentPosition
                        .map { positionMs ->
                            val bucket = positionMs.coerceAtLeast(0L) / POSITION_AUTOPLAY_EVAL_BUCKET_MS
                            bucket * POSITION_AUTOPLAY_EVAL_BUCKET_MS
                        }.distinctUntilChanged()

                val autoPlayNextFlow = userPreferencesRepository.userData.map { it.autoPlayNext }

                combine(
                    uiState,
                    playerController.isPlaying,
                    throttledPositionFlow,
                    playerController.duration,
                    autoPlayNextFlow,
                ) { state, isPlaying, positionMs, durationMs, autoPlayNext ->
                    TriggerSeriesAutoplaySnapshot(
                        state = state,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        autoPlayNext = autoPlayNext,
                        hasTriggeredSeriesAutoplay = hasTriggeredSeriesAutoplay,
                    )
                }.collect { snapshot ->
                    val activeState = snapshot.state as? PlayerState.Active ?: return@collect
                    val isLastChapter = activeState.currentChapterIndex >= (activeState.chapters.size - 1).coerceAtLeast(0)
                    val autoplayDecision =
                        evaluateSeriesAutoplayDecision(
                            isLastChapter = isLastChapter,
                            isPlaying = snapshot.isPlaying,
                            positionMs = snapshot.positionMs,
                            durationMs = snapshot.durationMs,
                            hasTriggeredSeriesAutoplay = snapshot.hasTriggeredSeriesAutoplay,
                        )

                    if (autoplayDecision.shouldTriggerAutoplay && !autoplayDismissedUntilChapterChange && snapshot.autoPlayNext) {
                        hasTriggeredSeriesAutoplay = true
                        maybeStartSeriesAutoplay(activeState.book)
                    } else if (autoplayDecision.shouldResetAutoplay || !snapshot.autoPlayNext) {
                        // Explicit dismiss should survive play/pause and near-end jitter
                        // until the user leaves the last chapter.
                        // Also reset if autoplay is disabled.
                        if (!isLastChapter) {
                            autoplayDismissedUntilChapterChange = false
                            hasTriggeredSeriesAutoplay = false
                        } else if (!autoplayDismissedUntilChapterChange && autoplayDecision.shouldResetAutoplay) {
                            hasTriggeredSeriesAutoplay = false
                        }
                        seriesAutoplayJob?.cancel()
                        seriesAutoplayJob = null
                        _nextBookAutoplayState.value = null
                    }
                }
            }
        }

        private fun maybeStartSeriesAutoplay(currentBook: Book) {
            seriesAutoplayJob?.cancel()
            seriesAutoplayJob =
                viewModelScope.launch {
                    val allBooks = booksRepository.getAllBooks().first()
                    val nextBook = findNextBookInSeries(currentBook, allBooks) ?: return@launch
                    startAutoplayCountdown(nextBook)
                }
        }

        private suspend fun startAutoplayCountdown(nextBook: Book) {
            for (seconds in AUTOPLAY_COUNTDOWN_SECONDS downTo 0) {
                if (!currentCoroutineContext().isActive) return
                _nextBookAutoplayState.value =
                    NextBookAutoplayState(
                        nextBook = nextBook,
                        secondsLeft = seconds,
                        totalSeconds = AUTOPLAY_COUNTDOWN_SECONDS,
                    )
                if (seconds > 0) delay(1_000L)
            }
            if (!currentCoroutineContext().isActive) return
            _nextBookAutoplayState.value = null
            emitEffect(PlayerEffect.NavigateToBook(nextBook.id))
        }

        public fun continueSeriesNow() {
            val nextBook = _nextBookAutoplayState.value?.nextBook ?: return
            seriesAutoplayJob?.cancel()
            seriesAutoplayJob = null
            _nextBookAutoplayState.value = null
            autoplayDismissedUntilChapterChange = false
            emitEffect(PlayerEffect.NavigateToBook(nextBook.id))
        }

        public fun dismissSeriesAutoplay() {
            seriesAutoplayJob?.cancel()
            seriesAutoplayJob = null
            _nextBookAutoplayState.value = null
            hasTriggeredSeriesAutoplay = true
            autoplayDismissedUntilChapterChange = true
        }

        private fun findNextBookInSeries(
            currentBook: Book,
            allBooks: List<Book>,
        ): Book? {
            val currentDescriptor = parseSeriesDescriptor(currentBook) ?: return null
            return allBooks
                .asSequence()
                .filter { it.id != currentBook.id }
                .mapNotNull { candidate ->
                    val descriptor = parseSeriesDescriptor(candidate) ?: return@mapNotNull null
                    if (descriptor.seriesKey != currentDescriptor.seriesKey) return@mapNotNull null
                    if (!candidate.author.equals(currentBook.author, ignoreCase = true)) return@mapNotNull null
                    if (descriptor.order <= currentDescriptor.order) return@mapNotNull null
                    descriptor.order to candidate
                }.minByOrNull { (order, _) -> order }
                ?.second
        }

        private data class SeriesDescriptor(
            val seriesKey: String,
            val order: Int,
        )

        private fun parseSeriesDescriptor(book: Book): SeriesDescriptor? {
            val normalizedTitle = book.title.trim()
            val patterns =
                listOf(
                    Regex("""(?i)^(.*?)[\s\-–—:]*\b(?:book|книга|том|часть)\s*([0-9]{1,4})\b"""),
                    Regex("""(?i)^(.*?)[\s\-–—:]*[#№]\s*([0-9]{1,4})\b"""),
                )
            for (pattern in patterns) {
                val match = pattern.find(normalizedTitle) ?: continue
                val rawKey =
                    match.groupValues
                        .getOrNull(1)
                        .orEmpty()
                        .trim()
                val order = match.groupValues.getOrNull(2)?.toIntOrNull() ?: continue
                if (rawKey.isBlank()) continue
                return SeriesDescriptor(seriesKey = rawKey.lowercase(Locale.ROOT), order = order)
            }
            return null
        }

        private data class TriggerSeriesAutoplaySnapshot(
            val state: PlayerState,
            val isPlaying: Boolean,
            val positionMs: Long,
            val durationMs: Long,
            val autoPlayNext: Boolean,
            val hasTriggeredSeriesAutoplay: Boolean,
        )

        private suspend fun loadLyricsOrNull(
            audioPath: String,
        ): ImmutableList<com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine>? {
            try {
                // Sidecar lyrics are optional.
                val lyrics = lyricsRepository.getLyrics(audioPath)
                return if (lyrics.isNotEmpty()) lyrics.toImmutableList() else null
            } catch (e: Exception) {
                logger.e({ "Failed to load lyrics" }, e)
                return null
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
                        com.jabook.app.jabook.compose.core.theme.DynamicThemeManager.extractColorsCached(
                            coverUrl = coverUrl,
                            bitmap = bitmap,
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

        public val lastSleepTimerDurationMinutes: StateFlow<Int?> = sleepTimerRepository.lastFixedDurationMinutes

        // Unified player command dispatcher (incremental PlayerIntent migration)
        public fun dispatch(intent: PlayerIntent) {
            logger.d { "PlayerIntent received: $intent" }
            val currentState = uiState.value
            val chapterNavigationDecision = resolveChapterNavigationIntent(intent, currentState)
            val effectiveIntent = chapterNavigationDecision.intent
            val reducedState = PlayerReducer.reduce(currentState, effectiveIntent)
            if (
                currentState is PlayerState.Loading &&
                reducedState is PlayerState.Loading &&
                effectiveIntent.isPlaybackControlIntent()
            ) {
                emitEffect(PlayerEffect.ShowSnackbar("Player is not ready yet"))
                return
            }
            handleIntentSideEffects(
                intent = effectiveIntent,
                currentState = currentState,
                reducedState = reducedState,
            )
            maybeEmitChapterNavigationUndo(chapterNavigationDecision)
        }

        private fun resolveChapterNavigationIntent(
            intent: PlayerIntent,
            state: PlayerState,
        ): ChapterNavigationDecision =
            (state as? PlayerState.Active)?.let { activeState ->
                ChapterNavigationIntentPolicy.resolve(intent = intent, state = activeState)
            } ?: ChapterNavigationDecision(intent = intent)

        private fun maybeEmitChapterNavigationUndo(decision: ChapterNavigationDecision) {
            val targetChapter = decision.movedToChapterDisplayIndex ?: return
            val undoChapterIndex = decision.undoChapterIndex ?: return
            emitEffect(
                PlayerEffect.ShowSnackbar(
                    message = context.getString(R.string.playerChapterNavigationSnackbar, targetChapter),
                    actionLabel = context.getString(R.string.undoAction),
                    actionIntent = PlayerIntent.SelectChapter(chapterIndex = undoChapterIndex),
                ),
            )
        }

        private fun handleIntentSideEffects(
            intent: PlayerIntent,
            currentState: PlayerState,
            reducedState: PlayerState,
        ) {
            if (handleCommandIntent(intent, currentState, reducedState)) return
            when (intent) {
                PlayerIntent.ToggleChapterRepeat -> {
                    val targetMode = (reducedState as? PlayerState.Active)?.chapterRepeatMode ?: return
                    if (targetMode == chapterRepeatModeState.value) return
                    chapterRepeatModeState.value = targetMode
                    hasRepeatedOnce = false
                    playerController.setRepeatMode(
                        if (targetMode == ChapterRepeatMode.OFF) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE,
                    )
                }
                is PlayerIntent.CycleVisualizerMode -> {
                    val currentMode = visualizerMode.value
                    val nextMode = (currentMode + 1) % 4
                    viewModelScope.launch {
                        settingsRepository.updateAudioVisualizerMode(nextMode)
                    }
                }
                PlayerIntent.ToggleABRepeat -> {
                    val currentABState = _abRepeatState.value
                    val activeState = uiState.value as? PlayerState.Active ?: return
                    val chapterIndex = activeState.currentChapterIndex
                    val position = activeState.currentPosition
                    when (currentABState.phase) {
                        ABRepeatPhase.INACTIVE -> {
                            _abRepeatState.value =
                                ABRepeatState(pointA = position, chapterIndex = chapterIndex, phase = ABRepeatPhase.A_SET)
                        }
                        ABRepeatPhase.A_SET -> {
                            when {
                                currentABState.chapterIndex != chapterIndex -> {
                                    _abRepeatState.value =
                                        ABRepeatState(pointA = position, chapterIndex = chapterIndex, phase = ABRepeatPhase.A_SET)
                                }
                                !isValidABRepeatRange(currentABState.pointA, position) -> {
                                    emitEffect(PlayerEffect.ShowSnackbar("Point B must be after point A"))
                                }
                                else -> {
                                    _abRepeatState.value =
                                        ABRepeatState(
                                            pointA = currentABState.pointA,
                                            pointB = position,
                                            chapterIndex = chapterIndex,
                                            phase = ABRepeatPhase.ACTIVE,
                                        )
                                }
                            }
                        }
                        ABRepeatPhase.ACTIVE -> {
                            _abRepeatState.value = ABRepeatState()
                        }
                    }
                }
                is PlayerIntent.SetEqualizerPreset -> {
                    viewModelScope.launch {
                        runCatching { settingsRepository.updateEqualizerPreset(intent.presetName) }
                            .onFailure { error ->
                                logger.w(error) { "Failed to update EQ preset" }
                            }
                    }
                }
                is PlayerIntent.ReportError -> {
                    val reason = (reducedState as? PlayerState.Error)?.message ?: intent.reason
                    emitEffect(PlayerEffect.ShowError(reason))
                }
                else -> Unit
            }
        }

        private fun handleCommandIntent(
            intent: PlayerIntent,
            currentState: PlayerState,
            reducedState: PlayerState,
        ): Boolean =
            if (!PlayerIntentCommandRouter.isCommandIntent(intent)) {
                false
            } else {
                val command = PlayerIntentCommandRouter.routeIntent(intent, currentState, reducedState)
                if (command == null) {
                    logger.d { "Command intent produced no command: $intent" }
                } else {
                    dispatchCommand(command)
                }
                true
            }

        private fun dispatchCommand(command: PlayerCommand) {
            viewModelScope.launch {
                runCatching { commandChannel.send(command) }
                    .onFailure { error ->
                        logger.w(error) { "Command dispatch failed for $command" }
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

        public fun seekToBookmark(bookmark: BookmarkItem) {
            val state = uiState.value as? PlayerState.Active ?: return
            val targetChapter = state.chapters.getOrNull(bookmark.chapterIndex) ?: return
            val targetPositionMs =
                PlayerIntentGuardPolicy.clampSeekPosition(
                    requestedPositionMs = bookmark.resolvePositionMs(targetChapter.duration.inWholeMilliseconds),
                    chapterDurationMs = targetChapter.duration.inWholeMilliseconds,
                )
            // If bookmark is in a different chapter, we need to seek to that chapter first
            if (state.currentChapterIndex != bookmark.chapterIndex) {
                playerController.skipToChapter(bookmark.chapterIndex, targetPositionMs)
                return
            }
            // The current chapter can be seeked directly.
            seekTo(targetPositionMs)
        }

        public fun toggleFavorite() {
            val state = uiState.value as? PlayerState.Active ?: return
            viewModelScope.launch {
                booksRepository.setFavorite(state.book.id, !state.book.isFavorite)
            }
        }

        public fun skipToNext() {
            logger.d { "Action: Skip Next requested" }
            playerController.skipToNext()
        }

        public fun skipToPrevious() {
            logger.d { "Action: Skip Previous requested" }
            playerController.skipToPrevious()
        }

        public fun skipToChapter(
            chapterIndex: Int,
            positionMs: Long = 0L,
        ) {
            logger.d { "Action: Skip to Chapter index $chapterIndex positionMs=$positionMs requested" }
            playerController.skipToChapter(chapterIndex, positionMs)
            // Reset repeat flag when manually changing chapters
            onChapterChanged()
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
            applyPlaybackSpeed(speed = speed, rememberForBook = true)
        }

        private fun applyPlaybackSpeed(
            speed: Float,
            rememberForBook: Boolean,
        ) {
            val clampedSpeed = speed.coerceIn(0.5f, 3.5f)
            viewModelScope.launch {
                runCatching { playerController.setPlaybackSpeed(clampedSpeed) }
                    .onFailure { error ->
                        logger.e({ "Failed to set playback speed on player" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to update playback speed"))
                    }
            }
            if (!rememberForBook) return
            viewModelScope.launch {
                runCatching {
                    val activeState = uiState.value as? PlayerState.Active
                    val listenedMs = activeState?.currentPosition ?: playerController.currentPosition.value
                    if (
                        SpeedMemoryHierarchy.shouldRecordBookSpeed(
                            listenedMs = listenedMs,
                            previousSpeed = null,
                            newSpeed = clampedSpeed,
                        )
                    ) {
                        booksRepository.updatePreferredPlaybackSpeed(bookId = bookId, speed = clampedSpeed)
                    }
                }.onFailure { error ->
                    logger.w(error) { "Failed to persist per-book playback speed preference" }
                }
            }
            viewModelScope.launch {
                runCatching { userPreferencesRepository.setPlaybackSpeed(clampedSpeed) }
                    .onFailure { error ->
                        logger.e({ "Failed to persist playback speed" }, error)
                        dispatch(PlayerIntent.ReportError("Failed to save playback speed"))
                    }
            }
        }

        public fun startHoldToBoost(currentPlaybackSpeed: Float) {
            val boostedSpeed = holdToBoostPolicy.onPress(currentPlaybackSpeed)
            dispatch(PlayerIntent.SetPlaybackSpeed(boostedSpeed))
        }

        public fun endHoldToBoost() {
            val restoreSpeed = holdToBoostPolicy.onRelease() ?: return
            dispatch(PlayerIntent.SetPlaybackSpeed(restoreSpeed))
        }

        private fun observeHoldToBoostSpeedSetting() {
            viewModelScope.launch {
                settingsRepository.userPreferences
                    .map { it.holdToBoostSpeed }
                    .distinctUntilChanged()
                    .collect { configuredSpeed ->
                        holdToBoostPolicy = HoldToBoostPolicy(boostSpeed = resolveHoldToBoostSpeed(configuredSpeed))
                    }
            }
        }

        private fun observeSeekbarWaveformCache() {
            viewModelScope.launch {
                visualizerWaveformData.collect { chunk ->
                    _seekbarWaveformData.value =
                        withContext(Dispatchers.Default) {
                            mergeWaveformWindow(
                                currentWindow = _seekbarWaveformData.value,
                                incomingChunk = chunk,
                                targetSize = SEEKBAR_WAVEFORM_CACHE_SIZE,
                            )
                        }
                }
            }
        }

        private fun observeABRepeatPosition() {
            viewModelScope.launch {
                combine(
                    playerController.currentPosition,
                    playerController.currentChapterIndex,
                    _abRepeatState,
                ) { position, chapterIndex, abState ->
                    Triple(position, chapterIndex, abState)
                }.collect { (position, chapterIndex, abState) ->
                    if (abState.phase != ABRepeatPhase.INACTIVE && abState.chapterIndex != chapterIndex) {
                        _abRepeatState.value = ABRepeatState()
                    } else if (
                        abState.phase == ABRepeatPhase.ACTIVE &&
                        isValidABRepeatRange(abState.pointA, abState.pointB) &&
                        position >= abState.pointB
                    ) {
                        playerController.seekTo(abState.pointA)
                    }
                }
            }
        }

        public fun setPitchCorrectionEnabled(enabled: Boolean) {
            playerController.setPitchCorrectionEnabled(enabled)
        }

        // P-92: Bookmark operations extracted to PlayerBookmarkHandler
        private val bookmarkHandler =
            PlayerBookmarkHandler(
                bookmarkRepository = bookmarkRepository,
                uiState = uiState,
                bookmarks = bookmarks,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
                reportError = { msg -> dispatch(PlayerIntent.ReportError(msg)) },
            )

        public fun addBookmarkAtCurrentPosition(noteText: String? = null) {
            bookmarkHandler.addBookmarkAtCurrentPosition(noteText)
        }

        public fun addBookmarkAtPosition(
            chapterIndex: Int,
            positionMs: Long,
            noteText: String? = null,
            onCreated: (BookmarkItem?) -> Unit = {},
        ) {
            bookmarkHandler.addBookmarkAtPosition(chapterIndex, positionMs, noteText, onCreated)
        }

        public fun updateBookmarkContent(
            bookmarkId: String,
            noteText: String?,
            noteAudioPath: String? = null,
        ) {
            bookmarkHandler.updateBookmarkContent(bookmarkId, noteText, noteAudioPath)
        }

        public fun deleteBookmark(bookmarkId: String) {
            bookmarkHandler.deleteBookmark(bookmarkId)
        }

        public fun initializeVisualizer() {
            playerController.initializeVisualizer()
        }

        public fun setVisualizerEnabled(enabled: Boolean) {
            playerController.setVisualizerEnabled(enabled)
        }

        // P-92: Sleep timer operations extracted to PlayerSleepTimerHandler
        private val sleepTimerHandler =
            PlayerSleepTimerHandler(
                sleepTimerRepository = sleepTimerRepository,
                sleepTimerState = sleepTimerState,
                loggerFactory = loggerFactory,
            )

        public fun startSleepTimer(minutes: Int) {
            sleepTimerHandler.startSleepTimer(minutes)
        }

        public fun startSleepTimerEndOfChapter() {
            sleepTimerHandler.startSleepTimerEndOfChapter()
        }

        public fun startSleepTimerEndOfTrack() {
            sleepTimerHandler.startSleepTimerEndOfTrack()
        }

        public fun cancelSleepTimer() {
            sleepTimerHandler.cancelSleepTimer()
        }

        public data class ResumeAfterLongPauseData(
            val chapterName: String,
            val chapterPosition: String,
            val daysAgo: Int,
        )

        // P-92: Book and audio settings operations extracted to PlayerSettingsHandler
        private val settingsHandler =
            PlayerSettingsHandler(
                bookId = bookId,
                updateBookSettingsUseCase = updateBookSettingsUseCase,
                settingsRepository = settingsRepository,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
                reportError = { msg -> dispatch(PlayerIntent.ReportError(msg)) },
            )

        public fun updateBookSeekSettings(
            rewindSeconds: Int?,
            forwardSeconds: Int?,
        ) {
            settingsHandler.updateBookSeekSettings(rewindSeconds, forwardSeconds)
        }

        public fun resetBookSeekSettings() {
            settingsHandler.resetBookSeekSettings()
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
            settingsHandler.updateAudioSettings(
                volumeBoostLevel = volumeBoostLevel,
                skipSilence = skipSilence,
                skipSilenceThresholdDb = skipSilenceThresholdDb,
                skipSilenceMinMs = skipSilenceMinMs,
                skipSilenceMode = skipSilenceMode,
                normalizeVolume = normalizeVolume,
                speechEnhancer = speechEnhancer,
                autoVolumeLeveling = autoVolumeLeveling,
            )
        }

        /**
         * Initialize player with book data if needed.
         * Restores saved position from database if available.
         */
        public fun initializePlayer() {
            val state = uiState.value
            val isControllerBoundToCurrentBook = playerController.currentBookId.value == bookId

            // The service can already be bound after returning to the player screen.
            // Its callbacks still belong to this ViewModel in that case.
            playerController.setOnChapterEndedCallback { onChapterEnded() }
            playerController.setOnChapterRepeatedCallback { onChapterRepeated() }
            playerController.setOnChapterChangedCallback { onChapterChanged() }

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

                    val shouldSkipHierarchicalSpeedApply = restoredBootstrapSnapshot.value?.hasRestoredSpeed ?: false
                    if (!shouldSkipHierarchicalSpeedApply) {
                        viewModelScope.launch {
                            runCatching {
                                val globalSpeed = userPreferencesRepository.userData.first().playbackSpeed
                                val resolvedSpeed =
                                    booksRepository.resolvePreferredPlaybackSpeed(
                                        bookId = bookId,
                                        globalSpeed = globalSpeed,
                                    )
                                if (SpeedMemoryHierarchy.hasMeaningfulSpeedDelta(globalSpeed, resolvedSpeed)) {
                                    applyPlaybackSpeed(
                                        speed = resolvedSpeed,
                                        rememberForBook = false,
                                    )
                                }
                            }.onFailure { error ->
                                logger.w(error) { "Failed to resolve hierarchical playback speed for book" }
                            }
                        }
                    }
                }
            }
        }

        private fun emitEffect(effect: PlayerEffect) {
            // ponytail: trySend on BUFFERED(64) — holds events while UI is briefly
            // detached; switch to UNLIMITED if a real burst is ever observed.
            val result = _effects.trySend(effect)
            if (result.isFailure) {
                logger.w { "Player effect buffer full, dropped: $effect" }
            }
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

        /** Returns whether native repeat-one should remain enabled after a completed repeat. */
        public fun onChapterRepeated(): Boolean {
            val mode = chapterRepeatModeState.value
            return when (mode) {
                ChapterRepeatMode.INFINITE -> PlayerReducer.shouldKeepNativeChapterRepeat(mode)
                ChapterRepeatMode.ONCE -> {
                    hasRepeatedOnce = true
                    chapterRepeatModeState.value = PlayerReducer.clearOneTimeChapterRepeat(mode)
                    false
                }
                ChapterRepeatMode.OFF -> false
            }
        }

        /**
         * Reset repeat flag when chapter changes manually.
         */
        public fun onChapterChanged() {
            hasRepeatedOnce = false
            val nextRepeatMode = PlayerReducer.clearOneTimeChapterRepeat(chapterRepeatModeState.value)
            if (nextRepeatMode != chapterRepeatModeState.value) {
                chapterRepeatModeState.value = nextRepeatMode
                playerController.setRepeatMode(Player.REPEAT_MODE_OFF)
            }
            _abRepeatState.value = ABRepeatState()
        }

        private fun restoreStateSnapshot() {
            val snapshotBookId: String = savedStateHandle[STATE_SNAPSHOT_BOOK_ID] ?: return
            if (snapshotBookId != bookId) return

            val restoredPosition = (savedStateHandle[STATE_SNAPSHOT_POSITION_MS] ?: 0L).coerceAtLeast(0L)
            val restoredChapterIndex = (savedStateHandle[STATE_SNAPSHOT_CHAPTER_INDEX] ?: 0).coerceAtLeast(0)
            val restoredSpeed = (savedStateHandle[STATE_SNAPSHOT_PLAYBACK_SPEED] ?: 1.0f).coerceAtLeast(0f)
            val restoredSleepMode = savedStateHandle[STATE_SNAPSHOT_SLEEP_MODE] ?: PlayerStateSnapshotPolicy.MODE_IDLE
            restoredBootstrapSnapshot.value =
                RestoredBootstrapSnapshot(
                    positionMs = restoredPosition,
                    chapterIndex = restoredChapterIndex,
                    playbackSpeed = restoredSpeed,
                    sleepTimerMode = restoredSleepMode,
                    hasRestoredSpeed = restoredSpeed > 0f,
                )

            logger.d {
                "Restored player snapshot: chapter=$restoredChapterIndex, " +
                    "position=${restoredPosition}ms, speed=$restoredSpeed, sleepMode=$restoredSleepMode"
            }
        }

        private fun restoreStateSnapshotFromDataStore() {
            viewModelScope.launch {
                val existingSnapshot = restoredBootstrapSnapshot.value
                if ((existingSnapshot?.chapterIndex ?: 0) > 0 || (existingSnapshot?.positionMs ?: 0L) > 0L) return@launch
                val snapshot = settingsRepository.playerStateSnapshot.first() ?: return@launch
                val restoredWhileReading = restoredBootstrapSnapshot.value
                if ((restoredWhileReading?.chapterIndex ?: 0) > 0 || (restoredWhileReading?.positionMs ?: 0L) > 0L) {
                    return@launch
                }
                if (snapshot.bookId != bookId) return@launch
                val restoredPosition = snapshot.positionMs.coerceAtLeast(0L)
                val restoredChapterIndex = snapshot.chapterIndex.coerceAtLeast(0)
                val restoredSpeed = snapshot.playbackSpeed.coerceAtLeast(0f)
                val restoredSleepMode = snapshot.sleepTimerMode.ifBlank { PlayerStateSnapshotPolicy.MODE_IDLE }
                restoredBootstrapSnapshot.value =
                    RestoredBootstrapSnapshot(
                        positionMs = restoredPosition,
                        chapterIndex = restoredChapterIndex,
                        playbackSpeed = restoredSpeed,
                        sleepTimerMode = restoredSleepMode,
                        hasRestoredSpeed = restoredSpeed > 0f,
                    )
                restorePlaybackSpeedFromSnapshotIfNeeded()
                restoreSleepTimerModeFromSnapshotIfNeeded()
                logger.d {
                    "Restored player snapshot from DataStore: chapter=$restoredChapterIndex, " +
                        "position=${restoredPosition}ms, speed=$restoredSpeed, sleepMode=$restoredSleepMode"
                }
            }
        }

        private fun restorePlaybackSpeedFromSnapshotIfNeeded() {
            viewModelScope.launch {
                val bootstrapSnapshot = restoredBootstrapSnapshot.value ?: return@launch
                if (bootstrapSnapshot.playbackSpeed <= 0f) return@launch
                runCatching {
                    val currentSpeed = userPreferencesRepository.userData.first().playbackSpeed
                    if (kotlin.math.abs(currentSpeed - bootstrapSnapshot.playbackSpeed) > 0.01f) {
                        userPreferencesRepository.setPlaybackSpeed(bootstrapSnapshot.playbackSpeed)
                    }
                }.onFailure { error ->
                    logger.w(error) { "Failed to restore playback speed from player snapshot" }
                }
            }
        }

        private fun restoreSleepTimerModeFromSnapshotIfNeeded() {
            viewModelScope.launch {
                val bootstrapSnapshot = restoredBootstrapSnapshot.value ?: return@launch
                when (bootstrapSnapshot.sleepTimerMode) {
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

        private fun observeSleepTimerResumeHint() {
            viewModelScope.launch {
                uiState.collect { state ->
                    val activeState = state as? PlayerState.Active ?: return@collect
                    val wasLastStopBySleepTimer = wasLastStoppedBySleepTimerFlagSet()
                    if (
                        SleepTimerResumeHintPolicy.shouldShowHint(
                            wasLastStopBySleepTimer = wasLastStopBySleepTimer,
                            isPlaying = activeState.isPlaying,
                            hasAlreadyShownInSession = hasShownSleepTimerResumeHint,
                        )
                    ) {
                        hasShownSleepTimerResumeHint = true
                        emitEffect(PlayerEffect.ShowSnackbar(context.getString(R.string.sleepTimerResumeHint)))
                    }
                }
            }
        }

        private fun observePhoneCallBookmarkHint() {
            viewModelScope.launch {
                uiState.collect { state ->
                    val activeState = state as? PlayerState.Active ?: return@collect
                    if (!activeState.isPlaying) return@collect
                    if (AudioPlayerService.phoneCallBookmarkCreated) {
                        AudioPlayerService.phoneCallBookmarkCreated = false
                        emitEffect(PlayerEffect.ShowSnackbar(context.getString(R.string.phoneCallBookmarkSnackbar)))
                    }
                }
            }
        }

        private fun observeSmartResumeSuggestion() {
            viewModelScope.launch {
                uiState.collect { state ->
                    val activeState = state as? PlayerState.Active ?: return@collect
                    if (!activeState.isPlaying || hasShownSmartResumeRecapHint) return@collect
                    val suggestion = playerController.consumeSmartResumeSuggestion() ?: return@collect
                    hasShownSmartResumeRecapHint = true
                    emitEffect(
                        PlayerEffect.ShowSnackbar(
                            message =
                                context.getString(
                                    R.string.smartResumeRecapSuggestion,
                                    suggestion.pauseDurationMs / 3_600_000L,
                                ),
                            actionLabel = context.getString(R.string.smartResumeRecapAction),
                            actionIntent = PlayerIntent.SeekTo(suggestion.recapStartMs),
                        ),
                    )
                }
            }
        }

        private fun observeEqRecommendation() {
            viewModelScope.launch {
                uiState.collect { state ->
                    val activeState = state as? PlayerState.Active ?: return@collect
                    if (hasShownEqRecommendation) return@collect
                    hasShownEqRecommendation = true

                    val hourOfDay =
                        java.util.Calendar
                            .getInstance()
                            .get(java.util.Calendar.HOUR_OF_DAY)
                    val audioOutputType = EqContextRecommendationPolicy.detectAudioOutputType(context)
                    val recommendation = EqContextRecommendationPolicy(context).recommend(hourOfDay, audioOutputType, null)
                    if (recommendation != null) {
                        emitEffect(
                            PlayerEffect.ShowSnackbar(
                                message = context.getString(R.string.eq_recommendation_message, recommendation.displayName),
                                actionLabel = context.getString(R.string.eq_recommendation_apply),
                                actionIntent = PlayerIntent.SetEqualizerPreset(recommendation.name),
                            ),
                        )
                    }
                }
            }
        }

        private fun observeResumeAfterLongPause() {
            viewModelScope.launch {
                uiState.collect { state ->
                    val activeState = state as? PlayerState.Active ?: return@collect
                    if (hasShownResumeAfterLongPause) return@collect
                    val lastTimestamp = listeningSessionRepository.getLastListeningTimestamp(bookId) ?: return@collect
                    val daysAgo = ((System.currentTimeMillis() - lastTimestamp) / 86_400_000L).toInt()
                    if (daysAgo < 7) return@collect
                    hasShownResumeAfterLongPause = true
                    val chapter = activeState.currentChapter
                    val chapterName = chapter?.title ?: (chapter?.displayNumber?.toString() ?: "—")
                    val positionFormatted =
                        com.jabook.app.jabook.compose.feature.player.PlayerTimeFormatter.formatDuration(
                            activeState.currentPosition,
                        )
                    _resumeAfterLongPauseState.value =
                        ResumeAfterLongPauseData(
                            chapterName = chapterName,
                            chapterPosition = positionFormatted,
                            daysAgo = daysAgo,
                        )
                }
            }
        }

        public fun dismissResumeAfterLongPause() {
            _resumeAfterLongPauseState.value = null
        }

        public fun resumeAfterLongPauseContinue() {
            _resumeAfterLongPauseState.value = null
        }

        public fun resumeAfterLongPauseRestartChapter() {
            _resumeAfterLongPauseState.value = null
            val state = uiState.value as? PlayerState.Active ?: return
            playerController.skipToChapter(state.currentChapterIndex, 0L)
            viewModelScope.launch {
                delay(100L)
                playerController.play()
            }
        }

        public fun resumeAfterLongPauseSelectChapter() {
            _resumeAfterLongPauseState.value = null
        }

        private fun wasLastStoppedBySleepTimerFlagSet(): Boolean {
            val prefs = context.getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(SleepTimerPersistence.KEY_LAST_STOPPED_BY_SLEEP_TIMER, false)
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
                PlayerIntent.ToggleABRepeat,
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
                is PlayerIntent.SetEqualizerPreset,
                PlayerIntent.InitializePlayer,
                is PlayerIntent.ReportError,
                PlayerIntent.CycleVisualizerMode,
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
private const val DEFAULT_HOLD_TO_BOOST_SPEED: Float = 2.5f
private const val SEEKBAR_WAVEFORM_CACHE_SIZE: Int = 1000

private fun resolveHoldToBoostSpeed(configuredSpeed: Float): Float =
    when (configuredSpeed) {
        2.0f,
        2.5f,
        3.0f,
        -> configuredSpeed
        else -> DEFAULT_HOLD_TO_BOOST_SPEED
    }

private fun mergeWaveformWindow(
    currentWindow: FloatArray,
    incomingChunk: FloatArray,
    targetSize: Int,
): FloatArray {
    if (targetSize <= 0) return FloatArray(0)
    if (incomingChunk.isEmpty()) return currentWindow

    if (incomingChunk.size >= targetSize) {
        val result = FloatArray(targetSize)
        val start = incomingChunk.size - targetSize
        for (i in 0 until targetSize) {
            result[i] = kotlin.math.abs(incomingChunk[start + i]).coerceIn(0f, 1f)
        }
        return result
    }

    val shift = incomingChunk.size
    val keep = (targetSize - shift).coerceAtLeast(0)
    val result = FloatArray(targetSize)

    if (keep > 0 && currentWindow.isNotEmpty()) {
        val copyLength = minOf(keep, currentWindow.size)
        val fromIndex = (currentWindow.size - copyLength).coerceAtLeast(0)
        System.arraycopy(currentWindow, fromIndex, result, keep - copyLength, copyLength)
    }

    for (i in incomingChunk.indices) {
        result[keep + i] = kotlin.math.abs(incomingChunk[i]).coerceIn(0f, 1f)
    }

    return result
}

private data class RestoredBootstrapSnapshot(
    val positionMs: Long,
    val chapterIndex: Int,
    val playbackSpeed: Float,
    val sleepTimerMode: String,
    val hasRestoredSpeed: Boolean = false,
)

internal data class SeriesAutoplayDecision(
    val shouldTriggerAutoplay: Boolean,
    val shouldResetAutoplay: Boolean,
)

internal const val SERIES_AUTOPLAY_END_TOLERANCE_MS: Long = 750L

internal fun evaluateSeriesAutoplayDecision(
    isLastChapter: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasTriggeredSeriesAutoplay: Boolean,
): SeriesAutoplayDecision {
    val isTrackEnded = durationMs > 0L && positionMs >= (durationMs - SERIES_AUTOPLAY_END_TOLERANCE_MS)
    return SeriesAutoplayDecision(
        shouldTriggerAutoplay = isLastChapter && !isPlaying && isTrackEnded && !hasTriggeredSeriesAutoplay,
        shouldResetAutoplay = !isLastChapter || isPlaying || (hasTriggeredSeriesAutoplay && !isTrackEnded),
    )
}

internal fun resolveDeleteBookmarkFailureReason(deleteResult: Result<Unit>): String? =
    if (deleteResult.isFailure) {
        "Failed to delete bookmark"
    } else {
        null
    }
