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

package com.jabook.app.jabook.audio.processors

import android.media.audiofx.BassBoost
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
public class BassBoostManager
    @Inject
    constructor(
        private val player: ExoPlayer,
    ) {
        private var bassBoost: BassBoost? = null
        private var currentStrength: Short = 0
        private var isReleased = false

        private val playerListener =
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachBassBoost(audioSessionId, currentStrength)
                }
            }

        public fun initialize() {
            player.addListener(playerListener)
            attachBassBoost(player.audioSessionId, currentStrength)
        }

        public fun setStrength(strength: Int) {
            val normalizedStrength = (strength.coerceIn(0, 100) * 10).toShort()
            currentStrength = normalizedStrength
            bassBoost?.setStrength(normalizedStrength)
        }

        public fun release() {
            if (isReleased) return
            isReleased = true
            player.removeListener(playerListener)
            releaseBassBoost()
        }

        private fun attachBassBoost(
            sessionId: Int,
            strength: Short,
        ) {
            releaseBassBoost()
            if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
            try {
                val bb = BassBoost(0, sessionId)
                bb.setStrength(strength)
                bb.enabled = strength > 0
                bassBoost = bb
                LogUtils.i("BassBoostManager", "Attached to session $sessionId, strength=$strength")
            } catch (e: Exception) {
                LogUtils.e("BassBoostManager", "Failed to attach BassBoost", e)
                bassBoost = null
            }
        }

        private fun releaseBassBoost() {
            try {
                bassBoost?.enabled = false
                bassBoost?.release()
            } catch (_: Exception) {
            }
            bassBoost = null
        }
    }
