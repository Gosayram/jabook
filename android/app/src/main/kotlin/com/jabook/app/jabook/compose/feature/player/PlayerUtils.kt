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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.jabook.app.jabook.compose.feature.player.PlayerTimeFormatter
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

internal fun formatDuration(durationMs: Long): String = PlayerTimeFormatter.formatDuration(durationMs)

internal fun formatPlaybackSpeedLabel(playbackSpeed: Float): String = PlayerTimeFormatter.formatPlaybackSpeedLabel(playbackSpeed)

internal const val HOLD_TO_BOOST_ACTIVATION_DELAY_MS: Long = 300L

internal fun deleteBookmarkVoiceNotes(
    filesDir: File,
    bookmarkId: String,
) {
    runCatching {
        val dir = bookmarkVoiceNoteDirectory(filesDir).toOkioPath()
        val fs = FileSystem.SYSTEM
        fs
            .list(dir)
            .filter { it.name.startsWith("bookmark_${bookmarkId}_") && it.name.endsWith(".m4a") }
            .forEach { runCatching { fs.delete(it) } }
    }
}

internal fun playerStateContentKey(state: PlayerState): String =
    when (state) {
        is PlayerState.Loading -> "loading"
        is PlayerState.Active -> "active"
        is PlayerState.Error -> "error"
        PlayerState.Empty -> "empty"
    }

internal data class ChapterBoundaryHapticDecision(
    val shouldPerformHaptic: Boolean,
    val nextSkipTriggeredHaptic: Boolean,
    val nextLastChapterBoundaryIndex: Int,
)

internal fun resolveChapterBoundaryHapticDecision(
    previousChapterIndex: Int,
    newChapterIndex: Int,
    skipTriggeredHaptic: Boolean,
): ChapterBoundaryHapticDecision? {
    if (newChapterIndex == previousChapterIndex) return null
    return if (skipTriggeredHaptic) {
        ChapterBoundaryHapticDecision(
            shouldPerformHaptic = false,
            nextSkipTriggeredHaptic = false,
            nextLastChapterBoundaryIndex = newChapterIndex,
        )
    } else {
        ChapterBoundaryHapticDecision(
            shouldPerformHaptic = true,
            nextSkipTriggeredHaptic = false,
            nextLastChapterBoundaryIndex = newChapterIndex,
        )
    }
}

internal fun mapKeyEventToPlayerIntent(keyEvent: androidx.compose.ui.input.key.KeyEvent): PlayerIntent? =
    when (keyEvent.key) {
        Key.Spacebar -> PlayerIntent.TogglePlayPause
        Key.DirectionLeft ->
            if (keyEvent.isShiftPressed) {
                PlayerIntent.SkipPrevious
            } else {
                PlayerIntent.SeekBackward
            }
        Key.DirectionRight ->
            if (keyEvent.isShiftPressed) {
                PlayerIntent.SkipNext
            } else {
                PlayerIntent.SeekForward
            }
        else -> null
    }

// ponytail: 10s/30s mirror PlayerUiStateBuilder's hardcoded fallbacks; per-book/global configured
// intervals live in PlayerViewModel state (SavedStateHandle-bound), unreachable at the app root.
internal const val ROOT_SEEK_REWIND_SECONDS: Int = 10
internal const val ROOT_SEEK_FORWARD_SECONDS: Int = 30

/**
 * Root-level global arrow-key seek (spotube SeekAction pattern): bare Left/Right arrow keys seek
 * the active session from anywhere outside the PlayerScreen. Reuses [mapKeyEventToPlayerIntent]
 * so arrow semantics stay defined in one place, but requires ALL modifiers released —
 * shift+arrow (chapter skip), ctrl/alt/meta+arrow combos return null so the caller can return
 * false and let normal focus traversal proceed.
 *
 * Returns the seek delta in milliseconds, or null when this event is not a bare arrow seek.
 */
internal fun mapBareArrowKeyUpToSeekDeltaMs(keyEvent: androidx.compose.ui.input.key.KeyEvent): Long? {
    if (keyEvent.isShiftPressed || keyEvent.isCtrlPressed || keyEvent.isAltPressed || keyEvent.isMetaPressed) {
        return null
    }
    return when (mapKeyEventToPlayerIntent(keyEvent)) {
        PlayerIntent.SeekBackward -> -ROOT_SEEK_REWIND_SECONDS * 1000L
        PlayerIntent.SeekForward -> ROOT_SEEK_FORWARD_SECONDS * 1000L
        else -> null
    }
}
