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

/**
 * Pure reducer helpers for player state transitions.
 */
public object PlayerReducer {
    public fun reduce(
        state: PlayerState,
        intent: PlayerIntent,
    ): PlayerState =
        when (state) {
            PlayerState.Loading -> reduceLoading(state, intent)
            is PlayerState.Active -> reduceActive(state, intent)
            is PlayerState.Error -> reduceError(state, intent)
        }

    public fun nextChapterRepeatMode(current: ChapterRepeatMode): ChapterRepeatMode =
        when (current) {
            ChapterRepeatMode.OFF -> ChapterRepeatMode.ONCE
            ChapterRepeatMode.ONCE -> ChapterRepeatMode.INFINITE
            ChapterRepeatMode.INFINITE -> ChapterRepeatMode.OFF
        }

    public fun reduceChapterEnded(
        mode: ChapterRepeatMode,
        hasRepeatedOnce: Boolean,
    ): ChapterEndReduction =
        when (mode) {
            ChapterRepeatMode.OFF -> ChapterEndReduction(shouldRepeat = false, hasRepeatedOnce = false)
            ChapterRepeatMode.ONCE -> {
                if (!hasRepeatedOnce) {
                    ChapterEndReduction(shouldRepeat = true, hasRepeatedOnce = true)
                } else {
                    ChapterEndReduction(shouldRepeat = false, hasRepeatedOnce = false)
                }
            }
            ChapterRepeatMode.INFINITE -> ChapterEndReduction(shouldRepeat = true, hasRepeatedOnce = hasRepeatedOnce)
        }

    public fun reduceChapterChanged(): Boolean = false

    private fun reduceLoading(
        state: PlayerState,
        intent: PlayerIntent,
    ): PlayerState =
        when (intent) {
            is PlayerIntent.ReportError -> PlayerState.Error(intent.reason)
            else -> state
        }

    private fun reduceActive(
        state: PlayerState.Active,
        intent: PlayerIntent,
    ): PlayerState =
        when (intent) {
            PlayerIntent.Play -> {
                if (state.isPlaying) {
                    state
                } else {
                    state.copy(isPlaying = true)
                }
            }
            PlayerIntent.Pause -> {
                if (!state.isPlaying) {
                    state
                } else {
                    state.copy(isPlaying = false)
                }
            }
            PlayerIntent.TogglePlayPause -> state.copy(isPlaying = !state.isPlaying)
            is PlayerIntent.SeekTo -> {
                val clampedPosition =
                    PlayerIntentGuardPolicy.clampSeekPosition(
                        requestedPositionMs = intent.positionMs,
                        chapterDurationMs = state.currentChapter?.duration?.inWholeMilliseconds,
                    )
                state.copy(currentPosition = clampedPosition)
            }
            PlayerIntent.SeekForward -> {
                val requestedPosition = state.currentPosition + state.forwardInterval * 1_000L
                val clampedPosition =
                    PlayerIntentGuardPolicy.clampSeekPosition(
                        requestedPositionMs = requestedPosition,
                        chapterDurationMs = state.currentChapter?.duration?.inWholeMilliseconds,
                    )
                state.copy(currentPosition = clampedPosition)
            }
            PlayerIntent.SeekBackward -> {
                val requestedPosition = state.currentPosition - state.rewindInterval * 1_000L
                val clampedPosition =
                    PlayerIntentGuardPolicy.clampSeekPosition(
                        requestedPositionMs = requestedPosition,
                        chapterDurationMs = state.currentChapter?.duration?.inWholeMilliseconds,
                    )
                state.copy(currentPosition = clampedPosition)
            }
            is PlayerIntent.SelectChapter -> {
                if (state.chapters.isEmpty()) {
                    state
                } else {
                    val maxIndex = state.chapters.lastIndex
                    val clampedIndex = intent.chapterIndex.coerceIn(0, maxIndex)
                    val selectedChapter = state.chapters[clampedIndex]
                    state.copy(
                        currentChapterIndex = clampedIndex,
                        currentChapter = selectedChapter,
                        currentPosition = 0L,
                    )
                }
            }
            PlayerIntent.ToggleChapterRepeat -> {
                state.copy(chapterRepeatMode = nextChapterRepeatMode(state.chapterRepeatMode))
            }
            is PlayerIntent.StartSleepTimer -> {
                val requestedSeconds = intent.minutes.coerceAtLeast(1) * 60
                val isSameFixedTimer =
                    state.sleepTimerMode == PlayerSleepTimerMode.FIXED &&
                        state.sleepTimerRemainingSeconds != null &&
                        kotlin.math.abs(state.sleepTimerRemainingSeconds - requestedSeconds) <= SAME_TIMER_EPSILON_SECONDS
                if (isSameFixedTimer) {
                    state
                } else {
                    state.copy(
                        sleepTimerMode = PlayerSleepTimerMode.FIXED,
                        sleepTimerRemainingSeconds = requestedSeconds,
                    )
                }
            }
            PlayerIntent.StartSleepTimerEndOfChapter -> {
                if (state.sleepTimerMode == PlayerSleepTimerMode.END_OF_CHAPTER) {
                    state
                } else {
                    state.copy(
                        sleepTimerMode = PlayerSleepTimerMode.END_OF_CHAPTER,
                        sleepTimerRemainingSeconds = null,
                    )
                }
            }
            PlayerIntent.StartSleepTimerEndOfTrack -> {
                if (state.sleepTimerMode == PlayerSleepTimerMode.END_OF_TRACK) {
                    state
                } else {
                    state.copy(
                        sleepTimerMode = PlayerSleepTimerMode.END_OF_TRACK,
                        sleepTimerRemainingSeconds = null,
                    )
                }
            }
            PlayerIntent.CancelSleepTimer -> {
                if (state.sleepTimerMode == PlayerSleepTimerMode.IDLE) {
                    state
                } else {
                    state.copy(
                        sleepTimerMode = PlayerSleepTimerMode.IDLE,
                        sleepTimerRemainingSeconds = null,
                    )
                }
            }
            is PlayerIntent.ReportError -> PlayerState.Error(intent.reason)
            else -> state
        }

    private fun reduceError(
        state: PlayerState.Error,
        intent: PlayerIntent,
    ): PlayerState =
        when (intent) {
            is PlayerIntent.ReportError -> PlayerState.Error(intent.reason)
            PlayerIntent.InitializePlayer -> PlayerState.Loading
            else -> state
        }
}

private const val SAME_TIMER_EPSILON_SECONDS: Int = 2

public data class ChapterEndReduction(
    val shouldRepeat: Boolean,
    val hasRepeatedOnce: Boolean,
)
