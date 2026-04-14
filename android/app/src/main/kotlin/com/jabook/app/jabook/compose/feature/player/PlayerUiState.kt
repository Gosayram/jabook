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

import androidx.compose.runtime.Immutable
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.collections.immutable.ImmutableList

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
        val defaultRewindInterval: Int = rewindInterval,
        val defaultForwardInterval: Int = forwardInterval,
        val hasBookSeekOverride: Boolean = false,
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
