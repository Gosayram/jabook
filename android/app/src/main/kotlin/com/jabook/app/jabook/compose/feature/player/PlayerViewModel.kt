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
import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.audio.PlaylistItem
import com.jabook.app.jabook.audio.processors.SpeedMemoryHierarchy
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.usecase.library.GetBookDetailsUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

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

        private val seekbarWaveformHandler =
            PlayerSeekbarWaveformHandler(
                visualizerWaveformData = visualizerWaveformData,
                viewModelScope = viewModelScope,
            )

        public val seekbarWaveformData: StateFlow<FloatArray> = seekbarWaveformHandler.seekbarWaveformData
        public val bookmarks: StateFlow<List<BookmarkItem>> =
            bookmarkRepository
                .observeBookmarks(bookId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        private val restoredBootstrapSnapshot = MutableStateFlow<RestoredBootstrapSnapshot?>(null)
        private val isPlaybackRestoreReady = MutableStateFlow(false)

        private val chapterRepeatHandler = PlayerChapterRepeatHandler(playerController = playerController)
        private val lyricsHandler =
            PlayerLyricsHandler(
                bookId = bookId,
                getChaptersUseCase = getChaptersUseCase,
                lyricsRepository = lyricsRepository,
                playerController = playerController,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
            )
        private val themeColorsHandler =
            PlayerThemeColorsHandler(
                bookId = bookId,
                context = context,
                getBookDetailsUseCase = getBookDetailsUseCase,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
            )

        // Dynamic Theme Colors
        public val themeColors: StateFlow<com.jabook.app.jabook.compose.core.theme.PlayerThemeColors?> =
            themeColorsHandler.themeColors

        // Backpressure guard for seekbar/UI: keep only latest position updates and
        // suppress jittery micro-updates that don't change visible state.
        public val currentPosition: StateFlow<Long> =
            playerController.currentPosition
                .map { it.coerceAtLeast(0L) }
                .distinctUntilChanged { previous, current -> abs(current - previous) < POSITION_UI_EPSILON_MS }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = playerController.currentPosition.value.coerceAtLeast(0L),
                )

        /** Whether the current playlist has a following chapter that can be selected. */
        public val hasNextChapter: StateFlow<Boolean> = playerController.hasNextChapter

        /** Whether the current playlist has a preceding chapter that can be selected. */
        public val hasPreviousChapter: StateFlow<Boolean> = playerController.hasPreviousChapter

        /**
         * Combined UI state from book data, playback state, and settings.
         */
        public val uiState: PlayerStateFlowContract =
            buildPlayerUiState(
                scope = viewModelScope,
                context = context,
                bookId = bookId,
                initialChapterIndexOverride = initialChapterIndexOverride,
                bookFlow = getBookDetailsUseCase(bookId),
                chaptersFlow = getChaptersUseCase(bookId).map(::sortChaptersForPlayback),
                isPlaying = playerController.isPlaying,
                currentChapterIndex = playerController.currentChapterIndex,
                controllerBookId = playerController.currentBookId,
                preferences = settingsRepository.userPreferences,
                playbackSpeed = userPreferencesRepository.userData.map { it.playbackSpeed },
                sleepTimerState = sleepTimerRepository.timerState,
                chapterRepeatMode = chapterRepeatHandler.chapterRepeatMode,
                restoredBootstrapSnapshot = restoredBootstrapSnapshot,
                isPlaybackRestoreReady = isPlaybackRestoreReady,
                themeColors = themeColorsHandler.themeColors,
                lyrics = lyricsHandler.lyricsState,
            )

        public data class NextBookAutoplayState(
            val nextBook: Book,
            val secondsLeft: Int,
            val totalSeconds: Int,
        )

        private val seriesAutoplayHandler =
            PlayerSeriesAutoplayHandler(
                uiState = uiState,
                playerController = playerController,
                userPreferencesRepository = userPreferencesRepository,
                booksRepository = booksRepository,
                viewModelScope = viewModelScope,
                navigateToBook = { nextBookId -> emitEffect(PlayerEffect.NavigateToBook(nextBookId)) },
            )

        public val nextBookAutoplayState: StateFlow<NextBookAutoplayState?> =
            seriesAutoplayHandler.nextBookAutoplayState

        public fun continueSeriesNow() {
            seriesAutoplayHandler.continueNow()
        }

        public fun dismissSeriesAutoplay() {
            seriesAutoplayHandler.dismiss()
        }

        // Resume after long pause dialog state (TASK-PLAYER-38)
        public data class ResumeAfterLongPauseData(
            val chapterName: String,
            val chapterPosition: String,
            val daysAgo: Int,
        )

        private val resumeAfterLongPauseHandler =
            PlayerResumeAfterLongPauseHandler(
                bookId = bookId,
                uiState = uiState,
                listeningSessionRepository = listeningSessionRepository,
                playerController = playerController,
                viewModelScope = viewModelScope,
            )

        public val resumeAfterLongPauseState: StateFlow<ResumeAfterLongPauseData?> =
            resumeAfterLongPauseHandler.resumeAfterLongPauseState

        public fun dismissResumeAfterLongPause() {
            resumeAfterLongPauseHandler.dismiss()
        }

        public fun resumeAfterLongPauseContinue() {
            resumeAfterLongPauseHandler.continuePlayback()
        }

        public fun resumeAfterLongPauseRestartChapter() {
            resumeAfterLongPauseHandler.restartChapter()
        }

        public fun resumeAfterLongPauseSelectChapter() {
            resumeAfterLongPauseHandler.selectChapter()
        }

        // AB repeat state
        private val abRepeatHandler =
            PlayerABRepeatHandler(
                playerController = playerController,
                uiState = uiState,
                viewModelScope = viewModelScope,
                emitEffect = ::emitEffect,
            )

        public val abRepeatState: StateFlow<ABRepeatState> = abRepeatHandler.abRepeatState

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

        private val speedHandler =
            PlayerSpeedHandler(
                bookId = bookId,
                playerController = playerController,
                settingsRepository = settingsRepository,
                userPreferencesRepository = userPreferencesRepository,
                booksRepository = booksRepository,
                uiState = uiState,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
                dispatchIntent = ::dispatch,
            )

        private val playbackBootstrapHandler =
            PlayerPlaybackBootstrapHandler(
                bookId = bookId,
                playerController = playerController,
                userPreferencesRepository = userPreferencesRepository,
                booksRepository = booksRepository,
                restoredBootstrapSnapshot = restoredBootstrapSnapshot,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
                applyPlaybackSpeed = { speed -> speedHandler.applyPlaybackSpeed(speed = speed, rememberForBook = false) },
            )

        private val intentDispatcher =
            PlayerIntentDispatcher(
                uiState = uiState,
                commandExecutor = commandExecutor,
                commandChannel = commandChannel,
                context = context,
                visualizerMode = visualizerMode,
                settingsRepository = settingsRepository,
                chapterRepeatHandler = chapterRepeatHandler,
                abRepeatHandler = abRepeatHandler,
                playerController = playerController,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
                emitEffect = ::emitEffect,
            )

        // Unified player command dispatcher (incremental PlayerIntent migration)
        public fun dispatch(intent: PlayerIntent) {
            intentDispatcher.dispatch(intent)
        }

        // Player control methods delegated to controller

        public fun play() {
            logger.d { "Action: Play requested" }
            val state = uiState.value
            if (state is PlayerState.Active) {
                // Ensure book is loaded before playing
                val isControllerBoundToCurrentBook = playerController.currentBookId.value == bookId
                if (!isControllerBoundToCurrentBook) {
                    playbackBootstrapHandler.loadBookForPlayback(
                        state = state,
                        autoPlay = true, // Auto-play after loading
                        resolveHierarchicalSpeed = false,
                    )
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
            speedHandler.applyPlaybackSpeed(speed = speed, rememberForBook = true)
        }

        public fun startHoldToBoost(currentPlaybackSpeed: Float) {
            speedHandler.startHoldToBoost(currentPlaybackSpeed)
        }

        public fun endHoldToBoost() {
            speedHandler.endHoldToBoost()
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
                playerController = playerController,
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
                val playlistItems =
                    state.chapters.mapNotNull { chapter ->
                        chapter.fileUrl?.let { path -> PlaylistItem(path, chapter.id, chapter.startMs, chapter.endMs) }
                    }
                val filePaths = playlistItems.map(PlaylistItem::path)
                if (filePaths.isNotEmpty()) {
                    // Single source-of-truth: initialize from unified uiState (controller/service-driven
                    // when bound, DB-restored only as bootstrap fallback before controller binds).
                    val initialChapterIndex = state.currentChapterIndex
                    val initialPosition = playerController.currentPosition.value

                    logger.d {
                        "Initializing player: chapter=$initialChapterIndex, position=${initialPosition}ms"
                    }

                    playerController.loadBook(
                        filePaths = filePaths,
                        playlistItems = playlistItems,
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
                                    speedHandler.applyPlaybackSpeed(
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
        public fun onChapterEnded(): Boolean = chapterRepeatHandler.onChapterEnded()

        /** Returns whether native repeat-one should remain enabled after a completed repeat. */
        public fun onChapterRepeated(): Boolean = chapterRepeatHandler.onChapterRepeated()

        /**
         * Reset repeat flag when chapter changes manually.
         */
        public fun onChapterChanged() {
            chapterRepeatHandler.onChapterChanged()
            abRepeatHandler.reset()
        }

        private val sessionHintsHandler =
            PlayerSessionHintsHandler(
                context = context,
                uiState = uiState,
                playerController = playerController,
                viewModelScope = viewModelScope,
                emitEffect = ::emitEffect,
            )

        private val stateRestoreHandler =
            PlayerStateRestoreHandler(
                bookId = bookId,
                savedStateHandle = savedStateHandle,
                settingsRepository = settingsRepository,
                userPreferencesRepository = userPreferencesRepository,
                sleepTimerRepository = sleepTimerRepository,
                playbackPositionRepository = playbackPositionRepository,
                sleepTimerState = sleepTimerState,
                uiState = uiState,
                playerController = playerController,
                restoredBootstrapSnapshot = restoredBootstrapSnapshot,
                isPlaybackRestoreReady = isPlaybackRestoreReady,
                viewModelScope = viewModelScope,
                loggerFactory = loggerFactory,
            )

        // Single initialization point: runs after all handler properties are initialized.
        // The call order matches the original collectors one-to-one.
        init {
            stateRestoreHandler.restoreFromSavedState()
            stateRestoreHandler.restoreFromDataStore()
            stateRestoreHandler.restorePlaybackSpeedFromSnapshotIfNeeded()
            stateRestoreHandler.restoreSleepTimerModeFromSnapshotIfNeeded()
            sessionHintsHandler.observeSleepTimerResumeHint()
            sessionHintsHandler.observePhoneCallBookmarkHint()
            speedHandler.observeHoldToBoostSpeedSetting()
            resumeAfterLongPauseHandler.observe()
            seekbarWaveformHandler.observe()
            abRepeatHandler.observePosition()
            sessionHintsHandler.observeEqRecommendation()

            viewModelScope.launch {
                commandFlow.collect { command ->
                    commandExecutor.execute(command)
                }
            }
            viewModelScope.launch {
                playerController.terminalPlaybackErrors.collect { message ->
                    emitEffect(PlayerEffect.ShowError(message))
                }
            }

            stateRestoreHandler.restorePositionFromDatabase()
            stateRestoreHandler.observeSnapshotPersistence()
            lyricsHandler.observe()
            seriesAutoplayHandler.observeTrigger()
            themeColorsHandler.observe()
        }

        private companion object {
            private const val POSITION_UI_EPSILON_MS: Long = 150L
        }
    }

internal fun resolveDeleteBookmarkFailureReason(deleteResult: Result<Unit>): String? =
    if (deleteResult.isFailure) {
        "Failed to delete bookmark"
    } else {
        null
    }
