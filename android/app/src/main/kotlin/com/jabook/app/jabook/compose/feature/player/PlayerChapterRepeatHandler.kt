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

import androidx.media3.common.Player
import com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Chapter repeat mode lifecycle: mode state, native repeat-mode mirroring, and once-mode tracking.
 *
 * @param playerController Controller for native repeat mode changes
 */
internal class PlayerChapterRepeatHandler(
    private val playerController: AudioPlayerController,
) {
    private val _chapterRepeatMode = MutableStateFlow(ChapterRepeatMode.OFF)

    /** Current chapter repeat mode. */
    val chapterRepeatMode: StateFlow<ChapterRepeatMode> = _chapterRepeatMode.asStateFlow()

    // Track if we've already repeated once (for ONCE mode)
    private var hasRepeatedOnce = false

    /**
     * Handles [PlayerIntent.ToggleChapterRepeat] against the reduced state.
     */
    fun onToggleChapterRepeat(reducedState: PlayerState) {
        val targetMode = (reducedState as? PlayerState.Active)?.chapterRepeatMode ?: return
        if (targetMode == _chapterRepeatMode.value) return
        _chapterRepeatMode.value = targetMode
        hasRepeatedOnce = false
        playerController.setRepeatMode(
            if (targetMode == ChapterRepeatMode.OFF) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE,
        )
    }

    /**
     * Handle chapter end - check if we need to repeat.
     * Called by AudioPlayerController when chapter ends.
     *
     * @return true if chapter should be repeated, false to continue to next
     */
    fun onChapterEnded(): Boolean {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = _chapterRepeatMode.value,
                hasRepeatedOnce = hasRepeatedOnce,
            )
        hasRepeatedOnce = reduction.hasRepeatedOnce
        return reduction.shouldRepeat
    }

    /** Returns whether native repeat-one should remain enabled after a completed repeat. */
    fun onChapterRepeated(): Boolean {
        val mode = _chapterRepeatMode.value
        return when (mode) {
            ChapterRepeatMode.INFINITE -> PlayerReducer.shouldKeepNativeChapterRepeat(mode)
            ChapterRepeatMode.ONCE -> {
                hasRepeatedOnce = true
                _chapterRepeatMode.value = PlayerReducer.clearOneTimeChapterRepeat(mode)
                false
            }
            ChapterRepeatMode.OFF -> false
        }
    }

    /**
     * Reset repeat flag when chapter changes manually.
     */
    fun onChapterChanged() {
        hasRepeatedOnce = false
        val nextRepeatMode = PlayerReducer.clearOneTimeChapterRepeat(_chapterRepeatMode.value)
        if (nextRepeatMode != _chapterRepeatMode.value) {
            _chapterRepeatMode.value = nextRepeatMode
            playerController.setRepeatMode(Player.REPEAT_MODE_OFF)
        }
    }
}
