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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import com.jabook.app.jabook.compose.core.util.HapticManager
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Slider drag-vs-live-position state machine shared by the portrait and landscape player layouts.
 *
 * Slider state-machine v2:
 * - livePosition = timeline progress (single source from player timeline)
 * - dragPosition = transient local drag value
 * - pendingSeekPosition = last user seek target until player converges
 */
@Stable
internal class PlayerSeekState internal constructor(
    initialTimeline: ChapterSeekbarTimeline,
) {
    internal var timeline by mutableStateOf(initialTimeline)
    internal var chapters by mutableStateOf<List<Chapter>>(emptyList())
    internal var currentChapterIndex by mutableIntStateOf(0)
    internal var bookmarks by mutableStateOf<List<BookmarkItem>>(emptyList())
    internal var abRepeat by mutableStateOf(ABRepeatState())
    internal var valueFormatter: ValueFormatter = ValueFormatter { "" }

    var dragPosition by mutableStateOf<Float?>(null)
        internal set
    var pendingSeekPosition by mutableStateOf<Float?>(null)
        internal set
    var coalescedPlayerProgress by mutableStateOf(initialTimeline.progress)
        internal set
    private var lastSliderHapticProgress by mutableStateOf<Float?>(null)

    val isDragging: Boolean
        get() = dragPosition != null

    val displayedProgress: State<Float> =
        derivedStateOf {
            PlayerSliderStateMachinePolicy.displayedProgress(
                liveProgress = coalescedPlayerProgress,
                dragProgress = dragPosition,
                pendingSeekProgress = pendingSeekPosition,
            )
        }

    val previewSeekTarget: State<ChapterSeekTarget> =
        derivedStateOf {
            ChapterSeekbarPolicy.resolveSeekTarget(
                chapters = chapters,
                progress = displayedProgress.value,
            )
        }

    val bookmarkMarkersFractions: State<List<Float>> =
        derivedStateOf {
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )
        }

    val abRepeatFractions: State<Pair<Float, Float>?> =
        derivedStateOf {
            if (abRepeat.phase == ABRepeatPhase.ACTIVE && timeline.totalDurationMs > 0L) {
                Pair(
                    (abRepeat.pointA.toFloat() / timeline.totalDurationMs.toFloat()).coerceIn(0f, 1f),
                    (abRepeat.pointB.toFloat() / timeline.totalDurationMs.toFloat()).coerceIn(0f, 1f),
                )
            } else {
                null
            }
        }

    val currentGlobalPositionMs: Long
        get() =
            if (isDragging && timeline.totalDurationMs > 0) {
                (displayedProgress.value.coerceIn(0f, 1f) * timeline.totalDurationMs.toFloat()).toLong()
            } else {
                timeline.globalPositionMs
            }

    internal fun onSliderValueChange(
        newProgress: Float,
        hapticFeedback: HapticFeedback,
    ) {
        pendingSeekPosition = null
        val constrainedProgress = newProgress.coerceIn(0f, 1f)
        val shouldTriggerHaptic =
            lastSliderHapticProgress == null ||
                abs(constrainedProgress - (lastSliderHapticProgress ?: constrainedProgress)) >= 0.05f
        if (shouldTriggerHaptic) {
            HapticManager.performTap(hapticFeedback)
            lastSliderHapticProgress = constrainedProgress
        }
        dragPosition = constrainedProgress
    }

    internal fun onSliderValueChangeFinished(
        onSeek: (Long) -> Unit,
        onSelectChapter: (Int, Long) -> Unit,
    ) {
        val targetProgress = dragPosition ?: displayedProgress.value
        if (timeline.totalDurationMs > 0 && targetProgress.isFinite()) {
            val target =
                ChapterSeekbarPolicy.resolveSeekTarget(
                    chapters = chapters,
                    progress = targetProgress,
                )
            pendingSeekPosition = targetProgress
            if (target.chapterIndex != currentChapterIndex) {
                onSelectChapter(target.chapterIndex, target.chapterPositionMs)
            } else {
                onSeek(target.chapterPositionMs)
            }
        }
        dragPosition = null
        lastSliderHapticProgress = null
    }
}

/**
 * Remembers [PlayerSeekState] for [state] and keeps its live/pending/drag progress reconciled with
 * the player timeline (coalescing, pending-seek convergence, 1.5s stale-seek safety timeout).
 */
@Composable
internal fun rememberPlayerSeekState(
    state: PlayerState.Active,
    abRepeatState: ABRepeatState,
): PlayerSeekState {
    // ponytail: computed inline — derivedStateOf keyed on currentPosition was pure per-tick overhead
    val chapterTimeline =
        ChapterSeekbarPolicy.buildTimeline(
            chapters = state.chapters,
            currentChapterIndex = state.currentChapterIndex,
            currentChapterPositionMs = state.currentPosition.coerceAtLeast(0L),
        )
    val seekState = remember { PlayerSeekState(chapterTimeline) }
    seekState.timeline = chapterTimeline
    seekState.chapters = state.chapters
    seekState.currentChapterIndex = state.currentChapterIndex
    seekState.bookmarks = state.bookmarks
    seekState.abRepeat = abRepeatState
    seekState.valueFormatter =
        remember(chapterTimeline.totalDurationMs) {
            ValueFormatter { progressValue: Float ->
                val clamped = progressValue.coerceIn(0f, 1f)
                PlayerTimeFormatter.formatDuration((chapterTimeline.totalDurationMs * clamped).toLong())
            }
        }
    val playerProgress = chapterTimeline.progress

    // Coalesce rapid progress deltas to reduce jitter/recomposition pressure on slider.
    LaunchedEffect(playerProgress, chapterTimeline.totalDurationMs) {
        seekState.coalescedPlayerProgress =
            PlayerSliderStateMachinePolicy.coalesceLiveProgress(
                previousProgress = seekState.coalescedPlayerProgress,
                incomingProgress = playerProgress,
                totalDurationMs = chapterTimeline.totalDurationMs,
            )
    }

    // Keep pending seek state until player progress converges near user target
    // to avoid post-seek jump-back jitter.
    LaunchedEffect(playerProgress, seekState.pendingSeekPosition, seekState.isDragging) {
        if (!seekState.isDragging && seekState.pendingSeekPosition != null) {
            val result =
                SliderSeekSyncPolicy.resolveFromPlayerProgress(
                    playerProgress = playerProgress,
                    currentSliderPosition = seekState.pendingSeekPosition ?: playerProgress,
                    isDragging = false,
                    awaitingSeekSync = true,
                )
            if (!result.awaitingSeekSync) {
                seekState.pendingSeekPosition = null
            }
        }
    }

    // Reset stale drag-seek state on chapter/duration changes to avoid jump-back race
    // when player timeline is rebuilt after chapter switch.
    LaunchedEffect(chapterTimeline.totalDurationMs, state.currentChapterIndex) {
        if (!seekState.isDragging) {
            seekState.coalescedPlayerProgress = playerProgress
            seekState.pendingSeekPosition = null
        }
    }

    // Guard against stale pending seek flag if player progress update is delayed.
    LaunchedEffect(seekState.pendingSeekPosition) {
        if (seekState.pendingSeekPosition != null) {
            delay(1500L)
            seekState.pendingSeekPosition = null
        }
    }

    return seekState
}
