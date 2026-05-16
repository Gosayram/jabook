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

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.util.LogUtils

/**
 * Manages a stable set of [ProxyAudioProcessor] instances that live for the
 * entire lifetime of an ExoPlayer instance.
 *
 * When the user changes audio settings (e.g. toggles the equalizer, enables
 * speech enhancement), only the *delegate* inside each proxy is swapped — the
 * ExoPlayer and its processor pipeline remain intact. This avoids the costly
 * destroy-and-recreate cycle that interrupts playback.
 *
 * ## Processor slot order (matches [AudioProcessorFactory])
 *
 * | Slot | Purpose                | Setting that controls it       |
 * |------|------------------------|--------------------------------|
 * | 0    | Loudness normalization | [AudioProcessingSettings.normalizeVolume] |
 * | 1    | Volume boost           | [AudioProcessingSettings.volumeBoostLevel] |
 * | 2    | Dynamic range compress | [AudioProcessingSettings.drcLevel] |
 * | 3    | Speech enhancement     | [AudioProcessingSettings.speechEnhancer] |
 * | 4    | Auto volume leveling   | [AudioProcessingSettings.autoVolumeLeveling] |
 * | 5    | Skip silence           | [AudioProcessingSettings.skipSilence] |
 *
 * ## Usage
 *
 * ```kotlin
 * // At ExoPlayer creation time (once):
 * val proxies = chainManager.proxies()
 * val exoPlayer = ExoPlayer.Builder(context)
 *     .setAudioProcessors(proxies.toTypedArray())
 *     .build()
 *
 * // Later, when settings change (any number of times):
 * chainManager.applySettings(newSettings)
 * ```
 *
 * P-01: Hot-swap audio processors without restarting the player.
 */
@UnstableApi
public class AudioProcessorChainManager {
    /** The nine proxy slots, each wrapping a single processing concern. */
    private val loudnessProxy = ProxyAudioProcessor()
    private val boostProxy = ProxyAudioProcessor()
    private val drcProxy = ProxyAudioProcessor()
    private val speechProxy = ProxyAudioProcessor()
    private val levelerProxy = ProxyAudioProcessor()
    private val skipSilenceProxy = ProxyAudioProcessor()
    private val equalizerProxy = ProxyAudioProcessor()
    private val noiseSuppressionProxy = ProxyAudioProcessor()
    private val reverbProxy = ProxyAudioProcessor() // P-29: Reverb

    /**
     * Returns the ordered list of [ProxyAudioProcessor] instances that should
     * be passed to [ExoPlayer.Builder.setAudioProcessors].
     *
     * The returned list is **stable** — the same proxies are returned on every
     * call — so it is safe to call this method once and cache the result.
     */
    public fun proxies(): List<AudioProcessor> =
        listOf(
            loudnessProxy,
            boostProxy,
            drcProxy,
            speechProxy,
            levelerProxy,
            skipSilenceProxy,
            equalizerProxy,
            noiseSuppressionProxy,
            reverbProxy, // P-29: Add reverb
        )

/**
     * Applies [AudioProcessingSettings] by swapping the delegate inside each
     * proxy slot.
     *
     * This method is idempotent: if a setting has not changed, the
     * corresponding delegate is not replaced.
     *
     * @param settings The new audio processing settings.
     * @return The [LoudnessNormalizer] if one was created, `null` otherwise.
     */
    public fun applySettings(settings: AudioProcessingSettings): LoudnessNormalizer? {
        var loudnessNormalizer: LoudnessNormalizer? = null

        // 1. Loudness Normalization
        loudnessProxy.swapDelegate(
            if (settings.normalizeVolume) {
                LoudnessNormalizer(settings).also { loudnessNormalizer = it }
            } else {
                PassthroughAudioProcessor()
            },
        )

        // 2. Volume Boost
        boostProxy.swapDelegate(
            if (settings.volumeBoostLevel != VolumeBoostLevel.Off) {
                VolumeBoostProcessor(settings.volumeBoostLevel)
            } else {
                PassthroughAudioProcessor()
            },
        )

        // 3. Dynamic Range Compression
        drcProxy.swapDelegate(
            if (settings.drcLevel != DRCLevel.Off) {
                DynamicRangeCompressor(settings.drcLevel)
            } else {
                PassthroughAudioProcessor()
            },
        )

        // 4. Speech Enhancement
        speechProxy.swapDelegate(
            if (settings.speechEnhancer) {
                SpeechEnhancer()
            } else {
                PassthroughAudioProcessor()
            },
        )

        // 5. Auto Volume Leveling
        levelerProxy.swapDelegate(
            if (settings.autoVolumeLeveling) {
                AutoVolumeLeveler()
            } else {
                PassthroughAudioProcessor()
            },
        )

        // 6. Skip Silence
        skipSilenceProxy.swapDelegate(
            if (settings.skipSilence) {
                SkipSilenceAudioProcessor(
                    enabled = true,
                    silenceThresholdNormalized = settings.skipSilenceThresholdNormalized,
                    minSilenceDurationMs = settings.skipSilenceMinDurationMs,
                    mode = settings.skipSilenceMode,
                    retainWindowMs = settings.retainWindowMs,
                )
            } else {
                PassthroughAudioProcessor()
            },
        )

        // 10. Echo (P-30)
        echoProxy.swapDelegate(
            if (settings.echoEnabled) {
                EchoAudioProcessor(
                    strength = settings.echoStrength,
                    delayMs = settings.echoDelayMs,
                    decay = settings.echoDecay,
                )
            } else {
                PassthroughAudioProcessor()
            },
        )

        LogUtils.i(
            TAG,
            "Applied settings via proxy chain: " +
                "normalize=${settings.normalizeVolume}, " +
                "boost=${settings.volumeBoostLevel}, " +
                "drc=${settings.drcLevel}, " +
                "speech=${settings.speechEnhancer}, " +
                "leveler=${settings.autoVolumeLeveling}, " +
                "skipSilence=${settings.skipSilence}, " +
                "equalizer=${settings.equalizerEnabled}, " +
                "noiseSuppression=${settings.noiseSuppressionEnabled}, " +
                "reverb=${settings.reverbEnabled}, " +
                "echo=${settings.echoEnabled}",
        )

        return loudnessNormalizer
    }

    /**
     * Returns the [ProxyAudioProcessor] that manages the loudness normalizer
     * slot. Useful for updating ReplayGain at runtime without a full settings
     * change.
     */
    public fun loudnessProxy(): ProxyAudioProcessor = loudnessProxy

    private companion object {
        private const val TAG = "AudioProcChainMgr"
    }
}
