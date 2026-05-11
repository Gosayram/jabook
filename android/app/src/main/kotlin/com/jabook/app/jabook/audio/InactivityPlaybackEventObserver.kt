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

package com.jabook.app.jabook.audio

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils

/**
 * Observes player events relevant to inactivity tracking.
 *
 * Extracted from [InactivityTimer] to keep timer orchestration and event
 * interpretation separate.
 */
internal class InactivityPlaybackEventObserver(
    private val player: ExoPlayer,
    private val checkAndStartTimer: () -> Unit,
    private val resetTimer: (InactivityCommandSource) -> Unit,
) {
    val listener: Player.Listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    LogUtils.d(
                        "InactivityTimer",
                        "Playback started (isPlaying=true), resetting inactivity timer",
                    )
                    resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
                } else {
                    LogUtils.d(
                        "InactivityTimer",
                        "Playback paused/stopped (isPlaying=false), checking if should start timer",
                    )
                    checkAndStartTimer()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (!player.playWhenReady) {
                            checkAndStartTimer()
                        } else {
                            resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
                        }
                    }
                    Player.STATE_ENDED -> checkAndStartTimer()
                    Player.STATE_IDLE, Player.STATE_BUFFERING -> resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
                }
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                LogUtils.d(
                    "InactivityTimer",
                    "Media item transition detected (user action), resetting inactivity timer",
                )
                resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    LogUtils.d(
                        "InactivityTimer",
                        "Position discontinuity (seek) detected (user action), resetting inactivity timer",
                    )
                    resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                LogUtils.d(
                    "InactivityTimer",
                    "Playback parameters changed (speed=${playbackParameters.speed}, user action), resetting inactivity timer",
                )
                resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                LogUtils.d("InactivityTimer", "Repeat mode changed (user action), resetting inactivity timer")
                resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                LogUtils.d("InactivityTimer", "Shuffle mode changed (user action), resetting inactivity timer")
                resetTimer(InactivityCommandSource.PLAYBACK_INTERNAL)
            }
        }
}
