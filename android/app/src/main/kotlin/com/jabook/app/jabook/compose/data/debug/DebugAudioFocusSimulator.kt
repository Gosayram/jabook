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

package com.jabook.app.jabook.compose.data.debug

import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug helper that simulates common audio focus transitions against active player state.
 */
@Singleton
public class DebugAudioFocusSimulator
    @Inject
    constructor(
        private val player: ExoPlayer,
        loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("DebugAudioFocusSimulator")
        private var volumeBeforeDuck: Float? = null

        public fun simulateDuck() {
            val current = player.volume.coerceIn(0f, 1f)
            if (volumeBeforeDuck == null) {
                volumeBeforeDuck = current
            }
            val ducked = minOf(current, 0.2f)
            player.volume = ducked
            logger.i { "Simulated AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: $current -> $ducked" }
        }

        public fun simulateLossTransient() {
            player.pause()
            logger.i { "Simulated AUDIOFOCUS_LOSS_TRANSIENT: playback paused" }
        }

        public fun simulateGain() {
            val restoreVolume = (volumeBeforeDuck ?: player.volume).coerceIn(0f, 1f)
            volumeBeforeDuck = null
            player.volume = restoreVolume
            if (!player.playWhenReady) {
                player.play()
            }
            logger.i { "Simulated AUDIOFOCUS_GAIN: volume=$restoreVolume, playWhenReady=${player.playWhenReady}" }
        }
    }
