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

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the system [Equalizer] audio effect lifecycle for audiobook playback.
 *
 * Follows the same pattern as [PlaybackEnhancerService]:
 * - Attaches to ExoPlayer's audio session on init / session change.
 * - Observes a Flow of [EqualizerPreset] from user preferences.
 * - Recreates the Equalizer when the audio session ID changes, restoring
 *   the current preset automatically.
 *
 * ## Thread safety
 *
 * The [Equalizer] is created and modified only from the main thread (via
 * [Player.Listener] callbacks and coroutine `Dispatchers.Main.immediate`).
 * The settings flow is collected on [Dispatchers.Default] but posts back
 * to main for safety.
 *
 * @property player The ExoPlayer instance to attach to.
 * @property settingsRepository Repository providing user EQ preferences.
 * @property eqFactory Factory for creating [Equalizer] instances (injectable for testing).
 */
@Singleton
public class AudioEqualizerManager
    @Inject
    constructor(
        private val player: ExoPlayer,
        private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.SettingsRepository,
        private val eqFactory: (Int) -> Equalizer = { sessionId -> Equalizer(0, sessionId) },
    ) {
        private val scopeJob = SupervisorJob()
        private val scope =
            CoroutineScope(
                scopeJob + Dispatchers.Main.immediate + loggingCoroutineExceptionHandler("AudioEqualizerManager"),
            )
        private var presetCollectionJob: Job? = null

        /** The current system Equalizer, or null if disabled / not yet attached. */
        private var equalizer: Equalizer? = null

        /** The last preset that was applied — used to restore after session change. */
        private var currentPreset: EqualizerPreset = EqualizerPreset.DEFAULT

        /** Flow of EQ presets from user preferences. */
        private val presetFlow: Flow<EqualizerPreset> =
            settingsRepository.userPreferences.map { preferences ->
                mapPresetName(preferences.equalizerPreset)
            }

        /** Player listener that re-attaches the Equalizer when audio session changes. */
        private val playerListener =
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachEqualizer(audioSessionId, currentPreset)
                }
            }

        /**
         * Initializes the manager. Call once from `AudioPlayerService.onCreate()`.
         *
         * Registers a player listener and subscribes to EQ preference changes.
         */
        public fun initialize() {
            player.addListener(playerListener)

            // Attach with currently known preset; collector below applies persisted preset once emitted.
            attachEqualizer(player.audioSessionId, currentPreset)

            // Observe preference changes
            presetCollectionJob?.cancel()
            presetCollectionJob =
                scope.launch {
                    presetFlow.collectLatest { preset ->
                        currentPreset = preset
                        applyPreset(preset)
                    }
                }
        }

        /**
         * Releases the Equalizer and unregisters the player listener.
         * Call from `AudioPlayerService.onDestroy()`.
         */
        public fun release() {
            player.removeListener(playerListener)
            presetCollectionJob?.cancel()
            presetCollectionJob = null
            equalizer?.release()
            equalizer = null
            scope.cancel()
            Log.d(TAG, "Equalizer released")
        }

        /**
         * Returns the number of EQ bands supported by the device, or 0 if
         * the Equalizer is not currently attached.
         */
        public fun getBandCount(): Int {
            val eq = equalizer ?: return 0
            return try {
                eq.numberOfBands.toInt()
            } catch (_: Exception) {
                0
            }
        }

        /**
         * Returns the center frequency of band [bandIndex] in milliHertz,
         * or 0 if the Equalizer is not available.
         */
        public fun getCenterFreq(bandIndex: Int): Int {
            val eq = equalizer ?: return 0
            return try {
                eq.getCenterFreq(bandIndex.toShort())
            } catch (_: Exception) {
                0
            }
        }

        /**
         * Returns the current band level for [bandIndex] in millibels,
         * or 0 if the Equalizer is not available.
         */
        public fun getBandLevel(bandIndex: Int): Int {
            val eq = equalizer ?: return 0
            return try {
                eq.getBandLevel(bandIndex.toShort()).toInt()
            } catch (_: Exception) {
                0
            }
        }

        // ---- Internal helpers ----

        private fun attachEqualizer(
            sessionId: Int,
            preset: EqualizerPreset,
        ) {
            releaseEqualizer()

            if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

            try {
                val eq = eqFactory(sessionId)
                equalizer = eq
                applyPresetToEq(eq, preset)
                Log.d(TAG, "Equalizer attached to session $sessionId, preset=${preset.name}")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to attach Equalizer: ${ex.message}", ex)
            }
        }

        private fun applyPreset(preset: EqualizerPreset) {
            val eq = equalizer ?: return
            try {
                applyPresetToEq(eq, preset)
                Log.d(TAG, "Applied preset ${preset.name}")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to apply preset ${preset.name}: ${ex.message}", ex)
            }
        }

        /**
         * Maps a [EqualizerPreset]'s band gains onto the device [Equalizer].
         *
         * The device may have fewer or more bands than [EqualizerPreset.BAND_COUNT].
         * We map linearly: if the device has N bands, we pick gains at evenly-spaced
         * indices from the preset array, then clamp each gain to the device's
         * per-band min/max range.
         */
        private fun applyPresetToEq(
            eq: Equalizer,
            preset: EqualizerPreset,
        ) {
            val numBands = eq.numberOfBands.toInt()
            if (numBands <= 0) return

            // Enable the equalizer
            eq.enabled = true

            val presetGains = preset.bandGainsMb
            val preamp = preset.effectivePreamp()

            for (i in 0 until numBands) {
                // Map device band index to preset index (evenly distributed)
                val presetIndex =
                    (i.toDouble() * (presetGains.size.toDouble() / numBands.toDouble()))
                        .toInt()
                        .coerceIn(0, presetGains.size - 1)
                // Apply preamp offset to prevent clipping from positive band gains
                var gainMb = presetGains[presetIndex] + preamp

                // Clamp to device band limits
                try {
                    val minLevel = eq.bandLevelRange[0].toInt()
                    val maxLevel = eq.bandLevelRange[1].toInt()
                    gainMb = gainMb.coerceIn(minLevel, maxLevel)
                } catch (_: Exception) {
                    // If range query fails, use gain as-is
                }

                eq.setBandLevel(i.toShort(), gainMb.toShort())
            }
        }

        private fun releaseEqualizer() {
            equalizer?.release()
            equalizer = null
        }

        private companion object {
            private const val TAG = "AudioEqualizerManager"
        }
    }

/**
 * Maps a preference string to [EqualizerPreset].
 * Falls back to [EqualizerPreset.DEFAULT] for unknown values.
 */
public fun mapPresetName(name: String): EqualizerPreset = EqualizerPreset.entries.find { it.name == name } ?: EqualizerPreset.DEFAULT
