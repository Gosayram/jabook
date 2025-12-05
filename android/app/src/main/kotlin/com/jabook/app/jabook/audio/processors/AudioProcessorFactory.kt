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

package com.jabook.app.jabook.audio.processors

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi

/**
 * Factory for creating chains of AudioProcessors based on audio settings.
 *
 * This factory manages the order and configuration of audio processors
 * for ExoPlayer. Processors are applied in a specific order to ensure
 * optimal audio quality.
 */
@OptIn(UnstableApi::class)
object AudioProcessorFactory {
    /**
     * Creates a chain of AudioProcessors based on the provided settings.
     *
     * Processor order (important for quality):
     * 1. LoudnessNormalizer (if enabled) - normalizes volume first
     * 2. VolumeBoostProcessor (if enabled) - applies gain boost
     * 3. DynamicRangeCompressor (if enabled) - compresses dynamic range
     * 4. SpeechEnhancer (if enabled) - enhances speech clarity
     * 5. AutoVolumeLeveler (if enabled) - maintains consistent volume
     *
     * @param settings Audio processing settings
     * @return List of AudioProcessors to apply, or empty list if none enabled
     */
    fun createProcessorChain(settings: AudioProcessingSettings): List<AudioProcessor> {
        val processors = mutableListOf<AudioProcessor>()

        try {
            // 1. Loudness Normalization (applied first for baseline volume)
            if (settings.normalizeVolume) {
                try {
                    val normalizer = LoudnessNormalizer(settings)
                    processors.add(normalizer)
                    android.util.Log.d("AudioProcessorFactory", "Added LoudnessNormalizer to chain")
                } catch (e: Exception) {
                    android.util.Log.e("AudioProcessorFactory", "Failed to create LoudnessNormalizer", e)
                }
            }

            // 2. Volume Boost (applied after normalization)
            if (settings.volumeBoostLevel != VolumeBoostLevel.Off) {
                try {
                    val boostProcessor = VolumeBoostProcessor(settings.volumeBoostLevel)
                    processors.add(boostProcessor)
                    android.util.Log.d("AudioProcessorFactory", "Added VolumeBoostProcessor (${settings.volumeBoostLevel}) to chain")
                } catch (e: Exception) {
                    android.util.Log.e("AudioProcessorFactory", "Failed to create VolumeBoostProcessor", e)
                }
            }

            // 3. Dynamic Range Compression (applied after boost)
            if (settings.drcLevel != DRCLevel.Off) {
                try {
                    val compressor = DynamicRangeCompressor(settings.drcLevel)
                    processors.add(compressor)
                    android.util.Log.d("AudioProcessorFactory", "Added DynamicRangeCompressor (${settings.drcLevel}) to chain")
                } catch (e: Exception) {
                    android.util.Log.e("AudioProcessorFactory", "Failed to create DynamicRangeCompressor", e)
                }
            }

            // 4. Speech Enhancer (applied after compression)
            if (settings.speechEnhancer) {
                try {
                    val enhancer = SpeechEnhancer()
                    processors.add(enhancer)
                    android.util.Log.d("AudioProcessorFactory", "Added SpeechEnhancer to chain")
                } catch (e: Exception) {
                    android.util.Log.e("AudioProcessorFactory", "Failed to create SpeechEnhancer", e)
                }
            }

            // 5. Auto Volume Leveling (applied last for final volume control)
            if (settings.autoVolumeLeveling) {
                try {
                    val leveler = AutoVolumeLeveler()
                    processors.add(leveler)
                    android.util.Log.d("AudioProcessorFactory", "Added AutoVolumeLeveler to chain")
                } catch (e: Exception) {
                    android.util.Log.e("AudioProcessorFactory", "Failed to create AutoVolumeLeveler", e)
                }
            }

            android.util.Log.i(
                "AudioProcessorFactory",
                "Created processor chain with ${processors.size} processors: " +
                    processors.joinToString { it.javaClass.simpleName },
            )
        } catch (e: Exception) {
            android.util.Log.e("AudioProcessorFactory", "Error creating processor chain", e)
        }

        return processors
    }
}

/**
 * Audio processing settings for configuring processors.
 *
 * This class holds all settings needed to configure audio processors.
 * Settings can come from global AudioSettingsManager or book-specific BookAudioSettings.
 */
data class AudioProcessingSettings(
    val normalizeVolume: Boolean = true,
    val volumeBoostLevel: VolumeBoostLevel = VolumeBoostLevel.Off,
    val drcLevel: DRCLevel = DRCLevel.Off,
    val speechEnhancer: Boolean = false,
    val autoVolumeLeveling: Boolean = false,
) {
    companion object {
        /**
         * Creates default settings (all features disabled).
         */
        fun defaults(): AudioProcessingSettings =
            AudioProcessingSettings(
                normalizeVolume = true, // Enabled by default for consistent volume
                volumeBoostLevel = VolumeBoostLevel.Off,
                drcLevel = DRCLevel.Off,
                speechEnhancer = false,
                autoVolumeLeveling = false,
            )
    }
}

/**
 * Volume boost level enum.
 */
enum class VolumeBoostLevel {
    Off,
    Boost50, // +50% gain
    Boost100, // +100% gain
    Boost200, // +200% gain
    Auto, // Automatic boost based on RMS analysis
}

/**
 * Dynamic Range Compression level enum.
 */
enum class DRCLevel {
    Off,
    Gentle, // Gentle compression for subtle effect
    Medium, // Medium compression for balanced effect
    Strong, // Strong compression for maximum effect
}
