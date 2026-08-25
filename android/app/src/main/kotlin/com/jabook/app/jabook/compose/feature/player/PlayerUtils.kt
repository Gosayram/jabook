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
