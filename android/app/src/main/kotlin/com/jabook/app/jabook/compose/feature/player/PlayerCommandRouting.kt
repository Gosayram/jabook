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
 * Command model executed by [PlayerCommandExecutor].
 *
 * Commands are intent side-effects separated from reducer state transitions.
 */
internal sealed interface PlayerCommand {
    data object InitializePlayer : PlayerCommand

    data object Play : PlayerCommand

    data object Pause : PlayerCommand

    data object SkipToNext : PlayerCommand

    data object SkipToPrevious : PlayerCommand

    data class SeekTo(
        val positionMs: Long,
    ) : PlayerCommand

    data class SkipToChapter(
        val chapterIndex: Int,
    ) : PlayerCommand

    data object InitializeVisualizer : PlayerCommand

    data class SetVisualizerEnabled(
        val enabled: Boolean,
    ) : PlayerCommand

    data class SetPlaybackSpeed(
        val speed: Float,
    ) : PlayerCommand

    data class SetPitchCorrectionEnabled(
        val enabled: Boolean,
    ) : PlayerCommand

    data class StartSleepTimer(
        val minutes: Int,
    ) : PlayerCommand

    data object StartSleepTimerEndOfChapter : PlayerCommand

    data object StartSleepTimerEndOfTrack : PlayerCommand

    data object CancelSleepTimer : PlayerCommand

    data class UpdateBookSeekSettings(
        val rewindSeconds: Int?,
        val forwardSeconds: Int?,
    ) : PlayerCommand

    data object ResetBookSeekSettings : PlayerCommand

    data class UpdateAudioSettings(
        val volumeBoostLevel: com.jabook.app.jabook.audio.processors.VolumeBoostLevel? = null,
        val skipSilence: Boolean? = null,
        val skipSilenceThresholdDb: Float? = null,
        val skipSilenceMinMs: Int? = null,
        val skipSilenceMode: com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode? = null,
        val normalizeVolume: Boolean? = null,
        val speechEnhancer: Boolean? = null,
        val autoVolumeLeveling: Boolean? = null,
    ) : PlayerCommand
}

internal class PlayerCommandExecutor(
    private val initializePlayer: () -> Unit,
    private val play: () -> Unit,
    private val pause: () -> Unit,
    private val skipToNext: () -> Unit,
    private val skipToPrevious: () -> Unit,
    private val seekTo: (Long) -> Unit,
    private val skipToChapter: (Int) -> Unit,
    private val initializeVisualizer: () -> Unit,
    private val setVisualizerEnabled: (Boolean) -> Unit,
    private val setPlaybackSpeed: (Float) -> Unit,
    private val setPitchCorrectionEnabled: (Boolean) -> Unit,
    private val startSleepTimer: (Int) -> Unit,
    private val startSleepTimerEndOfChapter: () -> Unit,
    private val startSleepTimerEndOfTrack: () -> Unit,
    private val cancelSleepTimer: () -> Unit,
    private val updateBookSeekSettings: (Int?, Int?) -> Unit,
    private val resetBookSeekSettings: () -> Unit,
    private val updateAudioSettings: (
        com.jabook.app.jabook.audio.processors.VolumeBoostLevel?,
        Boolean?,
        Float?,
        Int?,
        com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode?,
        Boolean?,
        Boolean?,
        Boolean?,
    ) -> Unit,
) {
    fun execute(command: PlayerCommand) {
        when (command) {
            PlayerCommand.InitializePlayer -> initializePlayer()
            PlayerCommand.Play -> play()
            PlayerCommand.Pause -> pause()
            PlayerCommand.SkipToNext -> skipToNext()
            PlayerCommand.SkipToPrevious -> skipToPrevious()
            is PlayerCommand.SeekTo -> seekTo(command.positionMs)
            is PlayerCommand.SkipToChapter -> skipToChapter(command.chapterIndex)
            PlayerCommand.InitializeVisualizer -> initializeVisualizer()
            is PlayerCommand.SetVisualizerEnabled -> setVisualizerEnabled(command.enabled)
            is PlayerCommand.SetPlaybackSpeed -> setPlaybackSpeed(command.speed)
            is PlayerCommand.SetPitchCorrectionEnabled -> setPitchCorrectionEnabled(command.enabled)
            is PlayerCommand.StartSleepTimer -> startSleepTimer(command.minutes)
            PlayerCommand.StartSleepTimerEndOfChapter -> startSleepTimerEndOfChapter()
            PlayerCommand.StartSleepTimerEndOfTrack -> startSleepTimerEndOfTrack()
            PlayerCommand.CancelSleepTimer -> cancelSleepTimer()
            is PlayerCommand.UpdateBookSeekSettings ->
                updateBookSeekSettings(command.rewindSeconds, command.forwardSeconds)
            PlayerCommand.ResetBookSeekSettings -> resetBookSeekSettings()
            is PlayerCommand.UpdateAudioSettings ->
                updateAudioSettings(
                    command.volumeBoostLevel,
                    command.skipSilence,
                    command.skipSilenceThresholdDb,
                    command.skipSilenceMinMs,
                    command.skipSilenceMode,
                    command.normalizeVolume,
                    command.speechEnhancer,
                    command.autoVolumeLeveling,
                )
        }
    }
}

internal object PlayerIntentCommandRouter {
    fun isCommandIntent(intent: PlayerIntent): Boolean =
        isPlaybackIntent(intent) ||
            isSleepTimerIntent(intent) ||
            isSettingsIntent(intent) ||
            when (intent) {
                PlayerIntent.InitializePlayer,
                PlayerIntent.InitializeVisualizer,
                is PlayerIntent.SetVisualizerEnabled,
                is PlayerIntent.SetPitchCorrectionEnabled,
                -> true
                else -> false
            }

    fun routeIntent(
        intent: PlayerIntent,
        currentState: PlayerState,
        reducedState: PlayerState,
    ): PlayerCommand? =
        when {
            isPlaybackIntent(intent) -> routePlaybackIntent(intent, currentState, reducedState)
            isSleepTimerIntent(intent) -> routeSleepTimerIntent(intent, currentState, reducedState)
            isSettingsIntent(intent) -> routeSettingsIntent(intent, currentState, reducedState)
            else ->
                when (intent) {
                    PlayerIntent.InitializePlayer -> PlayerCommand.InitializePlayer
                    PlayerIntent.InitializeVisualizer -> PlayerCommand.InitializeVisualizer
                    is PlayerIntent.SetVisualizerEnabled -> PlayerCommand.SetVisualizerEnabled(intent.enabled)
                    is PlayerIntent.SetPitchCorrectionEnabled ->
                        PlayerCommand.SetPitchCorrectionEnabled(intent.enabled)
                    else -> null
                }
        }

    fun isPlaybackIntent(intent: PlayerIntent): Boolean =
        when (intent) {
            PlayerIntent.TogglePlayPause,
            PlayerIntent.Play,
            PlayerIntent.Pause,
            PlayerIntent.SkipNext,
            PlayerIntent.SkipPrevious,
            is PlayerIntent.SeekTo,
            PlayerIntent.SeekForward,
            PlayerIntent.SeekBackward,
            is PlayerIntent.SelectChapter,
            is PlayerIntent.SetPlaybackSpeed,
            -> true
            else -> false
        }

    fun isSleepTimerIntent(intent: PlayerIntent): Boolean =
        when (intent) {
            is PlayerIntent.StartSleepTimer,
            PlayerIntent.StartSleepTimerEndOfChapter,
            PlayerIntent.StartSleepTimerEndOfTrack,
            PlayerIntent.CancelSleepTimer,
            -> true
            else -> false
        }

    fun routeSleepTimerIntent(
        intent: PlayerIntent,
        currentState: PlayerState,
        reducedState: PlayerState,
    ): PlayerCommand? {
        if (reducedState == currentState) return null
        return when (intent) {
            is PlayerIntent.StartSleepTimer -> PlayerCommand.StartSleepTimer(intent.minutes)
            PlayerIntent.StartSleepTimerEndOfChapter -> PlayerCommand.StartSleepTimerEndOfChapter
            PlayerIntent.StartSleepTimerEndOfTrack -> PlayerCommand.StartSleepTimerEndOfTrack
            PlayerIntent.CancelSleepTimer -> PlayerCommand.CancelSleepTimer
            else -> null
        }
    }

    fun isSettingsIntent(intent: PlayerIntent): Boolean =
        when (intent) {
            is PlayerIntent.UpdateBookSeekSettings,
            PlayerIntent.ResetBookSeekSettings,
            is PlayerIntent.UpdateAudioSettings,
            -> true
            else -> false
        }

    fun routeSettingsIntent(
        intent: PlayerIntent,
        currentState: PlayerState,
        reducedState: PlayerState,
    ): PlayerCommand? =
        when (intent) {
            is PlayerIntent.UpdateBookSeekSettings -> {
                if (reducedState == currentState) {
                    null
                } else {
                    val targetState = reducedState as? PlayerState.Active ?: return null
                    val targetRewindSeconds =
                        if (targetState.rewindInterval == targetState.defaultRewindInterval) {
                            null
                        } else {
                            targetState.rewindInterval
                        }
                    val targetForwardSeconds =
                        if (targetState.forwardInterval == targetState.defaultForwardInterval) {
                            null
                        } else {
                            targetState.forwardInterval
                        }
                    PlayerCommand.UpdateBookSeekSettings(
                        rewindSeconds = targetRewindSeconds,
                        forwardSeconds = targetForwardSeconds,
                    )
                }
            }
            PlayerIntent.ResetBookSeekSettings -> {
                if (reducedState == currentState) {
                    null
                } else {
                    PlayerCommand.ResetBookSeekSettings
                }
            }
            is PlayerIntent.UpdateAudioSettings -> {
                if (reducedState == currentState) {
                    null
                } else {
                    val targetState = reducedState as? PlayerState.Active ?: return null
                    PlayerCommand.UpdateAudioSettings(
                        volumeBoostLevel = targetState.volumeBoostLevel,
                        skipSilence = targetState.skipSilence,
                        skipSilenceThresholdDb = targetState.skipSilenceThresholdDb,
                        skipSilenceMinMs = targetState.skipSilenceMinMs,
                        skipSilenceMode = targetState.skipSilenceMode,
                        normalizeVolume = targetState.normalizeVolume,
                        speechEnhancer = targetState.speechEnhancer,
                        autoVolumeLeveling = targetState.autoVolumeLeveling,
                    )
                }
            }
            else -> null
        }

    fun routePlaybackIntent(
        intent: PlayerIntent,
        currentState: PlayerState,
        reducedState: PlayerState,
    ): PlayerCommand? =
        when (intent) {
            PlayerIntent.TogglePlayPause -> {
                val activeCurrentState = currentState as? PlayerState.Active
                val targetState = reducedState as? PlayerState.Active
                if (activeCurrentState == null || targetState == null || activeCurrentState == targetState) {
                    null
                } else if (targetState.isPlaying) {
                    PlayerCommand.Play
                } else {
                    PlayerCommand.Pause
                }
            }
            PlayerIntent.Play -> {
                if (reducedState == currentState) {
                    null
                } else {
                    PlayerCommand.Play
                }
            }
            PlayerIntent.Pause -> {
                if (reducedState == currentState) {
                    null
                } else {
                    PlayerCommand.Pause
                }
            }
            PlayerIntent.SkipNext -> PlayerCommand.SkipToNext
            PlayerIntent.SkipPrevious -> PlayerCommand.SkipToPrevious
            is PlayerIntent.SeekTo -> {
                val reducedPosition = (reducedState as? PlayerState.Active)?.currentPosition ?: intent.positionMs
                PlayerCommand.SeekTo(reducedPosition)
            }
            PlayerIntent.SeekForward -> {
                val reducedPosition = (reducedState as? PlayerState.Active)?.currentPosition ?: return null
                PlayerCommand.SeekTo(reducedPosition)
            }
            PlayerIntent.SeekBackward -> {
                val reducedPosition = (reducedState as? PlayerState.Active)?.currentPosition ?: return null
                PlayerCommand.SeekTo(reducedPosition)
            }
            is PlayerIntent.SelectChapter -> {
                val reducedChapterIndex = (reducedState as? PlayerState.Active)?.currentChapterIndex ?: intent.chapterIndex
                PlayerCommand.SkipToChapter(reducedChapterIndex)
            }
            is PlayerIntent.SetPlaybackSpeed -> {
                if (reducedState == currentState) {
                    null
                } else {
                    val reducedSpeed = (reducedState as? PlayerState.Active)?.playbackSpeed ?: return null
                    PlayerCommand.SetPlaybackSpeed(reducedSpeed)
                }
            }
            else -> null
        }
}
