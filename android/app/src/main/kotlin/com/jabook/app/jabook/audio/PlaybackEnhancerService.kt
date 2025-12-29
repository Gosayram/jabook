// Copyright 2025 Jabook Contributors
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

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Volume boost levels for playback enhancement.
 *
 * Inspired by lissen-android implementation with LoudnessEnhancer.
 */
enum class PlaybackVolumeBoost {
    DISABLED,
    LOW, // 3 dB
    MEDIUM, // 6 dB
    HIGH, // 12 dB
    MAX, // 20 dB
}

/**
 * Maps volume boost level string from Proto to PlaybackVolumeBoost enum.
 *
 * Proto values: "Off", "Boost50", "Boost100", "Boost200", "Auto"
 * Enum values: DISABLED, LOW (3dB), MEDIUM (6dB), HIGH (12dB), MAX (20dB)
 */
fun mapVolumeBoostLevel(level: String): PlaybackVolumeBoost =
    when (level) {
        "Off" -> PlaybackVolumeBoost.DISABLED
        "Boost50" -> PlaybackVolumeBoost.LOW // ~3dB (50% boost)
        "Boost100" -> PlaybackVolumeBoost.MEDIUM // ~6dB (100% boost)
        "Boost200" -> PlaybackVolumeBoost.HIGH // ~12dB (200% boost)
        "Auto" -> PlaybackVolumeBoost.MAX // ~20dB (auto/max boost)
        else -> PlaybackVolumeBoost.DISABLED // Default to disabled for unknown values
    }

/**
 * Service for enhancing audio playback with volume boost using LoudnessEnhancer.
 *
 * Inspired by lissen-android PlaybackEnhancerService implementation.
 * This service automatically attaches to ExoPlayer's audio session and applies
 * volume boost based on user preferences.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlaybackEnhancerService
    @Inject
    constructor(
        private val player: ExoPlayer,
        private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.SettingsRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private var enhancer: LoudnessEnhancer? = null

        /**
         * Flow of volume boost levels from user preferences.
         */
        private val volumeBoostFlow: Flow<PlaybackVolumeBoost> =
            settingsRepository.userPreferences.map { preferences ->
                mapVolumeBoostLevel(preferences.volumeBoostLevel)
            }

        /**
         * Initializes the enhancer service.
         * Should be called once in AudioPlayerService.onCreate().
         */
        fun initialize() {
            player.addListener(
                object : Player.Listener {
                    override fun onAudioSessionIdChanged(audioSessionId: Int) {
                        // Get current value from flow (synchronous read)
                        val currentBoost = getCurrentVolumeBoost()
                        attachEnhancer(audioSessionId, currentBoost)
                    }
                },
            )

            // Attach to current audio session
            val currentBoost = getCurrentVolumeBoost()
            attachEnhancer(player.audioSessionId, currentBoost)

            // Observe changes in volume boost settings
            scope.launch {
                volumeBoostFlow.collectLatest { boost ->
                    updateGain(boost)
                }
            }
        }

        /**
         * Gets current volume boost level from settings.
         */
        private fun getCurrentVolumeBoost(): PlaybackVolumeBoost {
            // Read current value synchronously (for initial setup)
            // This is a fallback - the Flow will handle updates
            return try {
                val preferences = kotlinx.coroutines.runBlocking {
                    settingsRepository.userPreferences.firstOrNull()
                }
                preferences?.let { mapVolumeBoostLevel(it.volumeBoostLevel) }
                    ?: PlaybackVolumeBoost.DISABLED
            } catch (e: Exception) {
                android.util.Log.w("PlaybackEnhancerService", "Failed to get volume boost: ${e.message}", e)
                PlaybackVolumeBoost.DISABLED
            }
        }

        @OptIn(UnstableApi::class)
        private fun attachEnhancer(
            sessionId: Int,
            boost: PlaybackVolumeBoost,
        ) {
            enhancer?.release()
            enhancer = null

            if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

            try {
                enhancer = LoudnessEnhancer(sessionId)
                updateGain(boost)
                android.util.Log.d("PlaybackEnhancerService", "LoudnessEnhancer attached to session: $sessionId")
            } catch (ex: Exception) {
                android.util.Log.e("PlaybackEnhancerService", "Unable to attach LoudnessEnhancer: ${ex.message}", ex)
            }
        }

        private fun updateGain(value: PlaybackVolumeBoost) {
            try {
                when (value) {
                    PlaybackVolumeBoost.DISABLED -> {
                        enhancer?.enabled = false
                        android.util.Log.d("PlaybackEnhancerService", "Volume boost disabled")
                    }
                    else -> {
                        enhancer?.enabled = true
                        val gainMb = boostToMb(value)
                        enhancer?.setTargetGain(gainMb)
                        android.util.Log.d("PlaybackEnhancerService", "Volume boost set: $value (${gainMb}mB)")
                    }
                }
            } catch (ex: Exception) {
                android.util.Log.e("PlaybackEnhancerService", "Unable to update volume gain with $value: ${ex.message}", ex)
            }
        }

        /**
         * Converts volume boost level to millibels (mB).
         * 1 dB = 100 mB
         */
        private fun boostToMb(value: PlaybackVolumeBoost): Int =
            when (value) {
                PlaybackVolumeBoost.DISABLED -> 0
                PlaybackVolumeBoost.LOW -> dbToMb(3f) // 3 dB = 300 mB
                PlaybackVolumeBoost.MEDIUM -> dbToMb(6f) // 6 dB = 600 mB
                PlaybackVolumeBoost.HIGH -> dbToMb(12f) // 12 dB = 1200 mB
                PlaybackVolumeBoost.MAX -> dbToMb(20f) // 20 dB = 2000 mB
            }

        /**
         * Converts decibels to millibels.
         */
        private fun dbToMb(db: Float): Int = (db * 100f).toInt()

        /**
         * Releases the enhancer.
         * Should be called when service is destroyed.
         */
        fun release() {
            enhancer?.release()
            enhancer = null
            android.util.Log.d("PlaybackEnhancerService", "LoudnessEnhancer released")
        }
    }
