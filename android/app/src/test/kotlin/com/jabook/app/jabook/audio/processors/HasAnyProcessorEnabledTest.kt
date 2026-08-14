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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HasAnyProcessorEnabledTest {
    @Test
    fun `defaults returns true because normalizeVolume is enabled`() {
        assertTrue(AudioProcessingSettings.hasAnyProcessorEnabled(AudioProcessingSettings.defaults()))
    }

    @Test
    fun `all disabled returns false`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = false,
                speechCompressorLevel = SpeechCompressorLevel.Off,
                volumeBoostLevel = VolumeBoostLevel.Off,
                drcLevel = DRCLevel.Off,
                speechEnhancer = false,
                autoVolumeLeveling = false,
                skipSilence = false,
                noiseGateLevel = NoiseGateLevel.Off,
            )
        assertFalse(AudioProcessingSettings.hasAnyProcessorEnabled(settings))
    }

    @Test
    fun `normalizeVolume true returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(normalizeVolume = true),
            ),
        )
    }

    @Test
    fun `speechCompressor enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    speechCompressorLevel = SpeechCompressorLevel.Gentle,
                ),
            ),
        )
    }

    @Test
    fun `volumeBoost enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    volumeBoostLevel = VolumeBoostLevel.Boost100,
                ),
            ),
        )
    }

    @Test
    fun `drc enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    drcLevel = DRCLevel.Strong,
                ),
            ),
        )
    }

    @Test
    fun `speechEnhancer enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    speechEnhancer = true,
                ),
            ),
        )
    }

    @Test
    fun `autoVolumeLeveling enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    autoVolumeLeveling = true,
                ),
            ),
        )
    }

    @Test
    fun `skipSilence enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    skipSilence = true,
                ),
            ),
        )
    }

    @Test
    fun `noiseGate enabled returns true`() {
        assertTrue(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    noiseGateLevel = NoiseGateLevel.Light,
                ),
            ),
        )
    }

    @Test
    fun `crossfade does NOT count as processor (handled separately)`() {
        // crossfade is not a processor — it's handled via CrossFadePlayer, not ProcessorChain
        assertFalse(
            AudioProcessingSettings.hasAnyProcessorEnabled(
                AudioProcessingSettings(
                    normalizeVolume = false,
                    isCrossfadeEnabled = true,
                ),
            ),
        )
    }
}
