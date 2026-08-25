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
 * Factory for creating chains of AudioProcessors based on audio settings.
 *
 * ponytail: hot-swap via ProxyAudioProcessor was removed; recreate-on-settings-change is the accepted path
 *
 * This factory manages the order and configuration of audio processors
 * for ExoPlayer. Processors are applied in a specific order to ensure
 * optimal audio quality.
 */
@UnstableApi
public object AudioProcessorFactory {
    /**
     * Result of creating a processor chain.
     * Contains the list of processors and a reference to the LoudnessNormalizer if created.
     */
    public data class ProcessorChainResult(
        val processors: List<AudioProcessor>,
        val loudnessNormalizer: LoudnessNormalizer? = null,
    )

    /**
     * Creates a chain of AudioProcessors based on the provided settings.
     *
     * Processor order (important for quality):
     * 1. LoudnessNormalizer (if enabled) - normalizes volume first
     * 2. SpeechCompressorAudioProcessor (if enabled) - 3-band speech compression
     * 3. VolumeBoostProcessor (if enabled) - applies gain boost
     * 4. DynamicRangeCompressor (if enabled) - compresses dynamic range
     * 5. SpeechEnhancer (if enabled) - enhances speech clarity
     * 6. AutoVolumeLeveler (if enabled) - maintains consistent volume
     * 7. SkipSilenceAudioProcessor (if enabled) - removes silent parts
     * 8. NoiseGateAudioProcessor (if enabled) - reduces noise floor
     *
     * @param settings Audio processing settings
     * @param outputFramesPerBuffer Device output buffer size, resolved outside the audio hot path.
     * @return Result containing list of AudioProcessors and optional LoudnessNormalizer
     */
    public fun createProcessorChain(
        settings: AudioProcessingSettings,
        outputFramesPerBuffer: Int? = null,
    ): ProcessorChainResult {
        val processors = mutableListOf<AudioProcessor>()
        var loudnessNormalizer: LoudnessNormalizer? = null

        try {
            // 1. Loudness Normalization (applied first for baseline volume)
            if (settings.normalizeVolume) {
                try {
                    val normalizer = LoudnessNormalizer(settings)
                    processors.add(normalizer)
                    loudnessNormalizer = normalizer
                    LogUtils.d("AudioProcessorFactory", "Added LoudnessNormalizer to chain")
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create LoudnessNormalizer", e)
                }
            }

            // 2. Speech Compressor (applied after normalization, before boost)
            if (settings.speechCompressorLevel != SpeechCompressorLevel.Off) {
                try {
                    val compressor = SpeechCompressorAudioProcessor(settings.speechCompressorLevel)
                    processors.add(compressor)
                    LogUtils.d(
                        "AudioProcessorFactory",
                        "Added SpeechCompressorAudioProcessor (${settings.speechCompressorLevel}) to chain",
                    )
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create SpeechCompressorAudioProcessor", e)
                }
            }

            // 3. Volume Boost (applied after normalization and speech compression)
            if (settings.volumeBoostLevel != VolumeBoostLevel.Off) {
                try {
                    val boostProcessor = VolumeBoostProcessor(settings.volumeBoostLevel)
                    processors.add(boostProcessor)
                    LogUtils.d(
                        "AudioProcessorFactory",
                        "Added VolumeBoostProcessor (${settings.volumeBoostLevel}) to chain",
                    )
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create VolumeBoostProcessor", e)
                }
            }

            // 4. Dynamic Range Compression (applied after boost)
            if (settings.drcLevel != DRCLevel.Off) {
                try {
                    val compressor = DynamicRangeCompressor(settings.drcLevel)
                    processors.add(compressor)
                    LogUtils.d(
                        "AudioProcessorFactory",
                        "Added DynamicRangeCompressor (${settings.drcLevel}) to chain",
                    )
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create DynamicRangeCompressor", e)
                }
            }

            // 5. Speech Enhancer (applied after compression)
            if (settings.speechEnhancer) {
                try {
                    val enhancer = SpeechEnhancer()
                    processors.add(enhancer)
                    LogUtils.d("AudioProcessorFactory", "Added SpeechEnhancer to chain")
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create SpeechEnhancer", e)
                }
            }

            // 6. Auto Volume Leveling (applied last for final volume control)
            if (settings.autoVolumeLeveling) {
                try {
                    val leveler = AutoVolumeLeveler()
                    processors.add(leveler)
                    LogUtils.d("AudioProcessorFactory", "Added AutoVolumeLeveler to chain")
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create AutoVolumeLeveler", e)
                }
            }

            // 7. Skip Silence (applied at the very end to remove silent parts)
            if (settings.skipSilence) {
                try {
                    val silenceSkippingProcessor =
                        SkipSilenceAudioProcessor(
                            enabled = true,
                            silenceThresholdNormalized = settings.skipSilenceThresholdNormalized,
                            minSilenceDurationMs = settings.skipSilenceMinDurationMs,
                            mode = settings.skipSilenceMode,
                            retainWindowMs = settings.retainWindowMs,
                            outputFramesPerBuffer = outputFramesPerBuffer,
                        )
                    processors.add(silenceSkippingProcessor)

                    LogUtils.d(
                        "AudioProcessorFactory",
                        "Added SkipSilenceAudioProcessor to chain (threshold=${settings.skipSilenceThresholdNormalized}, minMs=${settings.skipSilenceMinDurationMs}, retainMs=${settings.retainWindowMs})",
                    )
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create SkipSilenceAudioProcessor", e)
                }
            }

            // 8. Noise Gate (applied after skip silence, reduces noise floor between speech)
            if (settings.noiseGateLevel != NoiseGateLevel.Off) {
                try {
                    val noiseGate = NoiseGateAudioProcessor(settings.noiseGateLevel)
                    processors.add(noiseGate)
                    LogUtils.d(
                        "AudioProcessorFactory",
                        "Added NoiseGateAudioProcessor (${settings.noiseGateLevel}) to chain",
                    )
                } catch (e: Exception) {
                    LogUtils.e("AudioProcessorFactory", "Failed to create NoiseGateAudioProcessor", e)
                }
            }

            LogUtils.i(
                "AudioProcessorFactory",
                "Created processor chain with ${processors.size} processors: " +
                    processors.joinToString { it.javaClass.simpleName },
            )
        } catch (e: Exception) {
            LogUtils.e("AudioProcessorFactory", "Error creating processor chain", e)
        }

        return ProcessorChainResult(processors, loudnessNormalizer)
    }
}

/**
 * Audio processing settings for configuring processors.
 *
 * This class holds all settings needed to configure audio processors.
 * Settings can come from global AudioSettingsManager or book-specific BookAudioSettings.
 */
public data class AudioProcessingSettings(
    val normalizeVolume: Boolean = true,
    val speechCompressorLevel: SpeechCompressorLevel = SpeechCompressorLevel.Off,
    val volumeBoostLevel: VolumeBoostLevel = VolumeBoostLevel.Off,
    val drcLevel: DRCLevel = DRCLevel.Off,
    val speechEnhancer: Boolean = false,
    val autoVolumeLeveling: Boolean = false,
    val skipSilence: Boolean = false,
    val skipSilenceThresholdNormalized: Float = 0.015f,
    val skipSilenceMinDurationMs: Int = 250,
    val skipSilenceMode: SkipSilenceMode = SkipSilenceMode.SKIP,
    /**
     * Duration in ms of silence retained before speech resumes.
     *
     * Keeps the last [retainWindowMs] of each silence block so that
     * silence→speech transitions sound natural rather than clipped.
     * Range: 50–80 ms (default 65 ms).
     */
    val retainWindowMs: Int = DEFAULT_RETAIN_WINDOW_MS,
    val isCrossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Long = 0L,
    /** Crossfade duration between different books (0 = instant). */
    val crossfadeBetweenBooksMs: Long = 0L,
    val noiseGateLevel: NoiseGateLevel = NoiseGateLevel.Off,
    val preferredLanguageCode: String = "ru",
) {
    public companion object {
        /** Default retain window (65 ms) — balance between smoothness and skip efficiency. */
        public const val DEFAULT_RETAIN_WINDOW_MS: Int = 65

        /**
         * Creates default settings (all features disabled).
         */
        public fun defaults(): AudioProcessingSettings =
            AudioProcessingSettings(
                normalizeVolume = true, // Enabled by default for consistent volume
                speechCompressorLevel = SpeechCompressorLevel.Off,
                volumeBoostLevel = VolumeBoostLevel.Off,
                drcLevel = DRCLevel.Off,
                speechEnhancer = false,
                autoVolumeLeveling = false,
                skipSilence = false,
                skipSilenceThresholdNormalized = 0.015f,
                skipSilenceMinDurationMs = 250,
                skipSilenceMode = SkipSilenceMode.SKIP,
                retainWindowMs = DEFAULT_RETAIN_WINDOW_MS,
                isCrossfadeEnabled = false,
                crossfadeDurationMs = 2000L,
                crossfadeBetweenBooksMs = 500L,
            )

        /**
         * Lightweight check: does [settings] enable any audio processor?
         *
         * Mirrors the conditions in [AudioProcessorFactory.createProcessorChain] without
         * allocating the processor objects. Used as a default for [MediaModule.hasProcessors].
         */
        public fun hasAnyProcessorEnabled(settings: AudioProcessingSettings): Boolean =
            settings.normalizeVolume ||
                settings.speechCompressorLevel != SpeechCompressorLevel.Off ||
                settings.volumeBoostLevel != VolumeBoostLevel.Off ||
                settings.drcLevel != DRCLevel.Off ||
                settings.speechEnhancer ||
                settings.autoVolumeLeveling ||
                settings.skipSilence ||
                settings.noiseGateLevel != NoiseGateLevel.Off
    }
}

public enum class SkipSilenceMode {
    SKIP,
    SPEED_UP,
}

/**
 * Volume boost level enum.
 */
public enum class VolumeBoostLevel {
    Off,
    Boost50, // +50% gain
    Boost100, // +100% gain
    Boost200, // +200% gain
    Auto, // Automatic boost based on RMS analysis
}

/**
 * Dynamic Range Compression level enum.
 */
public enum class DRCLevel {
    Off,
    Gentle, // Gentle compression for subtle effect
    Medium, // Medium compression for balanced effect
    Strong, // Strong compression for maximum effect
}

/**
 * Speech Compressor intensity level enum.
 */
public enum class SpeechCompressorLevel {
    Off,
    Gentle, // Threshold -15 dB, gentle compression
    Moderate, // Threshold -20 dB, moderate compression
    Aggressive, // Threshold -25 dB, aggressive compression
}

/**
 * Noise gate intensity level enum.
 */
public enum class NoiseGateLevel {
    Off,
    Light, // Subtle noise floor reduction
    Medium, // Balanced noise reduction
    Strong, // Aggressive noise reduction
}
