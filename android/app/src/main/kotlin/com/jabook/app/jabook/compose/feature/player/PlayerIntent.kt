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
 * Unified UI -> ViewModel command contract for player actions.
 *
 * This is an incremental migration layer toward a strict intent-driven player architecture.
 */
public sealed interface PlayerIntent {
    public data object InitializePlayer : PlayerIntent

    public data object TogglePlayPause : PlayerIntent

    public data object Play : PlayerIntent

    public data object Pause : PlayerIntent

    public data object SkipNext : PlayerIntent

    public data object SkipPrevious : PlayerIntent

    public data class SeekTo(
        val positionMs: Long,
    ) : PlayerIntent

    public data object SeekForward : PlayerIntent

    public data object SeekBackward : PlayerIntent

    public data class SelectChapter(
        val chapterIndex: Int,
        val positionMs: Long = 0L,
    ) : PlayerIntent

    public data object ToggleChapterRepeat : PlayerIntent

    public data object InitializeVisualizer : PlayerIntent

    public data class SetVisualizerEnabled(
        val enabled: Boolean,
    ) : PlayerIntent

    public data object CycleVisualizerMode : PlayerIntent

    public data class SetPlaybackSpeed(
        val speed: Float,
    ) : PlayerIntent

    public data class SetPitchCorrectionEnabled(
        val enabled: Boolean,
    ) : PlayerIntent

    public data class StartSleepTimer(
        val minutes: Int,
    ) : PlayerIntent

    public data object StartSleepTimerEndOfChapter : PlayerIntent

    public data object StartSleepTimerEndOfTrack : PlayerIntent

    public data object CancelSleepTimer : PlayerIntent

    public data class UpdateBookSeekSettings(
        val rewindSeconds: Int?,
        val forwardSeconds: Int?,
    ) : PlayerIntent

    public data object ResetBookSeekSettings : PlayerIntent

    public data class UpdateAudioSettings(
        val volumeBoostLevel: com.jabook.app.jabook.audio.processors.VolumeBoostLevel? = null,
        val skipSilence: Boolean? = null,
        val skipSilenceThresholdDb: Float? = null,
        val skipSilenceMinMs: Int? = null,
        val skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode? = null,
        val normalizeVolume: Boolean? = null,
        val speechEnhancer: Boolean? = null,
        val autoVolumeLeveling: Boolean? = null,
    ) : PlayerIntent

    public data object ToggleABRepeat : PlayerIntent

    /**
     * Set global equalizer preset.
     */
    public data class SetEqualizerPreset(
        val presetName: String,
    ) : PlayerIntent

    /**
     * Internal intent for deterministic error-state reduction and testing.
     */
    public data class ReportError(
        val reason: String,
    ) : PlayerIntent
}

/** Returns whether this intent controls playback and must be rejected while the player loads. */
internal fun PlayerIntent.isPlaybackControlIntent(): Boolean =
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
