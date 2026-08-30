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
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
public open class AudioEqualizerManager
    @Inject
    constructor(
        private val player: ExoPlayer,
        private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.SettingsRepository,
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

        /**
         * Persisted custom band gains (mB) from `customEqBands`, padded/truncated
         * to [EqualizerPreset.BAND_COUNT]. Used only when preset is CUSTOM.
         */
        private var customBandGainsMb: IntArray = IntArray(0)

        /** Flow of EQ presets combined with persisted custom band gains. */
        private val presetFlow: Flow<Pair<EqualizerPreset, IntArray>> =
            combine(
                settingsRepository.userPreferences,
                settingsRepository.customEqBands,
            ) { preferences, bands ->
                val preset = mapPresetName(preferences.equalizerPreset)
                val gains =
                    IntArray(EqualizerPreset.BAND_COUNT) { i -> bands.getOrElse(i) { 0 } }
                preset to gains
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
                    presetFlow.collectLatest { (preset, gains) ->
                        currentPreset = preset
                        customBandGainsMb = gains
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
            LogUtils.d(TAG, "Equalizer released")
        }

/**
         * Re-attaches the Equalizer to a specific audio session — called by the service
         * whenever the ACTIVE player changes (the injected singleton player's session is
         * idle while the custom processor player is in use).
         */
        public fun attachToAudioSession(audioSessionId: Int) {
            attachEqualizer(audioSessionId, currentPreset)
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
                val eq = createEqualizer(sessionId)
                equalizer = eq
                applyPresetToEq(eq, preset)
                LogUtils.d(TAG, "Equalizer attached to session $sessionId, preset=${preset.name}")
            } catch (ex: Exception) {
                LogUtils.e(TAG, "Failed to attach Equalizer: ${ex.message}", ex)
                CrashDiagnostics.reportNonFatal("audio_equalizer_attach", ex)
            }
        }

        /** Factory hook — overridable for tests. */
        protected open fun createEqualizer(sessionId: Int): Equalizer = Equalizer(0, sessionId)

        private fun applyPreset(preset: EqualizerPreset) {
            val eq = equalizer ?: return
            try {
                applyPresetToEq(eq, preset)
                LogUtils.d(TAG, "Applied preset ${preset.name}")
            } catch (ex: Exception) {
                LogUtils.e(TAG, "Failed to apply preset ${preset.name}: ${ex.message}", ex)
            }
        }

        /**
         * Maps a [EqualizerPreset]'s band gains onto the device [Equalizer].
         *
         * The device may have fewer or more bands than [EqualizerPreset.BAND_COUNT].
         * Mapping is frequency-aware: for each device band we query [Equalizer.getCenterFreq]
         * and interpolate the preset gain at that frequency between the two surrounding
         * preset points ([EqualizerPreset.BAND_CENTER_FREQS_HZ]). When frequency data is
         * unavailable we fall back to the legacy evenly-spaced index mapping. Each gain is
         * clamped to the device's per-band min/max range.
         */
        private fun applyPresetToEq(
            eq: Equalizer,
            preset: EqualizerPreset,
        ) {
            val numBands = eq.numberOfBands.toInt()
            if (numBands <= 0) return

            // Enable the equalizer
            eq.enabled = true

            val isCustom = preset == EqualizerPreset.CUSTOM && customBandGainsMb.isNotEmpty()
            val presetGains = if (isCustom) customBandGainsMb else preset.bandGainsMb
            val preamp =
                if (isCustom) {
                    EqualizerPreset.calculateSafePreamp(customBandGainsMb)
                } else {
                    preset.effectivePreamp()
                }

            for (i in 0 until numBands) {
                val presetGainMb =
                    try {
                        val centerFreqMhz = eq.getCenterFreq(i.toShort())
                        if (centerFreqMhz > 0) {
                            interpolateGainMb(centerFreqMhz / 1000f, presetGains)
                        } else {
                            legacyIndexGainMb(i, numBands, presetGains)
                        }
                    } catch (_: Exception) {
                        legacyIndexGainMb(i, numBands, presetGains)
                    }
                // Apply preamp offset to prevent clipping from positive band gains
                var gainMb = presetGainMb + preamp

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

        /** Linear interpolation of preset gains at [freqHz] between neighboring preset points. */
        private fun interpolateGainMb(
            freqHz: Float,
            presetGainsMb: IntArray,
        ): Int {
            val presetFreqs = EqualizerPreset.BAND_CENTER_FREQS_HZ
            val lastIndex = minOf(presetFreqs.size, presetGainsMb.size) - 1
            if (freqHz <= presetFreqs[0]) return presetGainsMb[0]
            if (freqHz >= presetFreqs[lastIndex]) return presetGainsMb[lastIndex]
            var j = 0
            while (j < lastIndex - 1 && freqHz > presetFreqs[j + 1]) j++
            val t =
                (freqHz - presetFreqs[j].toFloat()) / (presetFreqs[j + 1] - presetFreqs[j]).toFloat()
            val gain = presetGainsMb[j] + t * (presetGainsMb[j + 1] - presetGainsMb[j])
            return Math.round(gain)
        }

        /** Legacy evenly-spaced index mapping used when the device frequency is unknown. */
        private fun legacyIndexGainMb(
            bandIndex: Int,
            numBands: Int,
            presetGainsMb: IntArray,
        ): Int =
            (bandIndex.toDouble() * (presetGainsMb.size.toDouble() / numBands.toDouble()))
                .toInt()
                .coerceIn(0, presetGainsMb.size - 1)
                .let { presetGainsMb[it] }

        private fun releaseEqualizer() {
            try {
                equalizer?.release()
            } catch (ex: Exception) {
                LogUtils.e(TAG, "Failed to release Equalizer: ${ex.message}", ex)
                CrashDiagnostics.reportNonFatal("audio_equalizer_release", ex)
            }
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
