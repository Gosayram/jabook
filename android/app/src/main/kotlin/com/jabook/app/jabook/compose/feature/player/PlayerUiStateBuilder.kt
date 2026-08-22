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
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Bootstrap snapshot restored from SavedStateHandle/DataStore before the controller binds. */
internal data class RestoredBootstrapSnapshot(
    val positionMs: Long,
    val chapterIndex: Int,
    val playbackSpeed: Float,
    val sleepTimerMode: String,
    val hasRestoredSpeed: Boolean = false,
)

private data class PlayerPlaybackBundle(
    val book: Book?,
    val chapters: List<Chapter>,
    val isPlaying: Boolean,
    val chapterIndex: Int,
)

private data class PlayerConfigBundle(
    val controllerBookId: String?,
    val preferences: UserPreferences,
    val playbackSpeed: Float,
    val sleepTimerState: SleepTimerState,
    val chapterRepeatMode: ChapterRepeatMode,
)

private data class PlayerRestoreBundle(
    val bootstrapSnapshot: RestoredBootstrapSnapshot?,
    val isRestoreReady: Boolean,
)

/**
 * Builds the combined player [PlayerState] flow from typed nested combines.
 */
internal fun buildPlayerUiState(
    scope: CoroutineScope,
    context: Context,
    bookId: String,
    initialChapterIndexOverride: Int?,
    bookFlow: Flow<Book?>,
    chaptersFlow: Flow<List<Chapter>>,
    isPlaying: StateFlow<Boolean>,
    currentChapterIndex: StateFlow<Int>,
    controllerBookId: StateFlow<String?>,
    preferences: Flow<UserPreferences>,
    playbackSpeed: Flow<Float>,
    sleepTimerState: StateFlow<SleepTimerState>,
    chapterRepeatMode: StateFlow<ChapterRepeatMode>,
    restoredBootstrapSnapshot: StateFlow<RestoredBootstrapSnapshot?>,
    isPlaybackRestoreReady: StateFlow<Boolean>,
    themeColors: StateFlow<PlayerThemeColors?>,
    lyrics: StateFlow<ImmutableList<LyricLine>?>,
): PlayerStateFlowContract =
    combine(
        combine(bookFlow, chaptersFlow, isPlaying, currentChapterIndex) {
            book,
            chapters,
            playing,
            chapterIdx,
            ->
            PlayerPlaybackBundle(
                book = book,
                chapters = chapters,
                isPlaying = playing,
                chapterIndex = chapterIdx,
            )
        },
        combine(controllerBookId, preferences, playbackSpeed, sleepTimerState, chapterRepeatMode) {
            controllerBook,
            prefs,
            speed,
            timerState,
            repeatMode,
            ->
            PlayerConfigBundle(
                controllerBookId = controllerBook,
                preferences = prefs,
                playbackSpeed = speed,
                sleepTimerState = timerState,
                chapterRepeatMode = repeatMode,
            )
        },
        combine(restoredBootstrapSnapshot, isPlaybackRestoreReady) { bootstrap, restoreReady ->
            PlayerRestoreBundle(bootstrapSnapshot = bootstrap, isRestoreReady = restoreReady)
        },
    ) { playback, config, restore ->
        if (!restore.isRestoreReady) {
            PlayerState.Loading
        } else if (playback.book == null) {
            PlayerState.Error(context.getString(R.string.book_not_found))
        } else if (playback.chapters.isEmpty()) {
            PlayerState.Error(context.getString(R.string.noChaptersFoundInSearch))
        } else {
            val book = playback.book
            val chapters = playback.chapters

            // Calculate effective seek intervals
            // Priority: Book Override -> Global Setting -> Hardcoded Default
            val rewindInterval =
                book.rewindDuration
                    ?: if (config.preferences.rewindDurationSeconds > 0) config.preferences.rewindDurationSeconds else 10
            val forwardInterval =
                book.forwardDuration
                    ?: if (config.preferences.forwardDurationSeconds > 0) config.preferences.forwardDurationSeconds else 30
            val defaultRewindInterval =
                if (config.preferences.rewindDurationSeconds > 0) {
                    config.preferences.rewindDurationSeconds
                } else {
                    10
                }
            val defaultForwardInterval =
                if (config.preferences.forwardDurationSeconds > 0) {
                    config.preferences.forwardDurationSeconds
                } else {
                    30
                }

            val maxChapterIndex = (chapters.size - 1).coerceAtLeast(0)
            val safeSavedChapterIndex = (restore.bootstrapSnapshot?.chapterIndex ?: 0).coerceIn(0, maxChapterIndex)
            val isControllerBoundToCurrentBook = config.controllerBookId == bookId
            // Once controller is bound to this book, it is the single source of truth
            // even when position/chapter are zero (freshly initialized state).
            val hasControllerStateForCurrentBook = isControllerBoundToCurrentBook

            val chapterIndex =
                if (hasControllerStateForCurrentBook) {
                    playback.chapterIndex.coerceIn(0, maxChapterIndex)
                } else if (initialChapterIndexOverride != null) {
                    initialChapterIndexOverride.coerceIn(0, maxChapterIndex)
                } else {
                    safeSavedChapterIndex
                }

            PlayerState.Active(
                book = book,
                chapters = chapters.toImmutableList(),
                isPlaying = playback.isPlaying,
                currentChapterIndex = chapterIndex,
                currentChapter = chapters.getOrNull(chapterIndex),
                rewindInterval = rewindInterval,
                forwardInterval = forwardInterval,
                defaultRewindInterval = defaultRewindInterval,
                defaultForwardInterval = defaultForwardInterval,
                hasBookSeekOverride = book.rewindDuration != null || book.forwardDuration != null,
                playbackSpeed = config.playbackSpeed,
                sleepTimerMode = config.sleepTimerState.toPlayerSleepTimerMode(),
                sleepTimerRemainingSeconds =
                    (config.sleepTimerState as? SleepTimerState.Active)
                        ?.remainingSeconds,
                chapterRepeatMode = config.chapterRepeatMode,
                volumeBoostLevel =
                    runCatching {
                        com.jabook.app.jabook.audio.processors.VolumeBoostLevel
                            .valueOf(config.preferences.volumeBoostLevel)
                    }.getOrElse { com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off },
                skipSilence = config.preferences.skipSilence,
                skipSilenceThresholdDb = config.preferences.skipSilenceThresholdDb,
                skipSilenceMinMs = config.preferences.skipSilenceMinMs,
                skipSilenceMode = config.preferences.skipSilenceMode,
                normalizeVolume = config.preferences.normalizeVolume,
                speechEnhancer = config.preferences.speechEnhancer,
                autoVolumeLeveling = config.preferences.autoVolumeLeveling,
            )
        }
    }.combine(themeColors) { state, colors ->
        if (state is PlayerState.Active) {
            state.copy(themeColors = colors)
        } else {
            state
        }
    }.combine(lyrics) { state, currentLyrics ->
        if (state is PlayerState.Active) {
            state.copy(lyrics = currentLyrics)
        } else {
            state
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerState.Loading,
    )

private fun SleepTimerState.toPlayerSleepTimerMode(): PlayerSleepTimerMode =
    when (this) {
        SleepTimerState.Idle -> PlayerSleepTimerMode.IDLE
        is SleepTimerState.Active -> PlayerSleepTimerMode.FIXED
        SleepTimerState.EndOfChapter -> PlayerSleepTimerMode.END_OF_CHAPTER
        is SleepTimerState.EndOfTrack -> PlayerSleepTimerMode.END_OF_TRACK
    }
