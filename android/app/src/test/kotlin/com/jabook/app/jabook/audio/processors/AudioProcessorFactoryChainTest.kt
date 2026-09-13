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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the chain assembly contract of [AudioProcessorFactory]:
 * the float→int16 chain-head converter precedes all DSP processors, and no
 * converter-only chain is built when every processor is disabled.
 */
class AudioProcessorFactoryChainTest {
    @Test
    fun `chain with processors starts with FloatToInt16PcmProcessor`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = true,
                volumeBoostLevel = VolumeBoostLevel.Boost100,
                skipSilence = true,
            )

        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertTrue(result.processors.isNotEmpty())
        assertEquals(FloatToInt16PcmProcessor::class, result.processors.first()::class)
        assertTrue(result.processors.size > 1)
        // DSP processors come after the converter.
        assertTrue(result.processors[1] is LoudnessNormalizer)
    }

    @Test
    fun `chain is empty when every processor is disabled`() {
        val settings = AudioProcessingSettings.processorsDisabled(AudioProcessingSettings.defaults())

        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertTrue(result.processors.isEmpty())
        assertEquals(null, result.loudnessNormalizer)
    }

    @Test
    fun `processorsDisabled disables every custom processor`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = true,
                speechCompressorLevel = SpeechCompressorLevel.Aggressive,
                volumeBoostLevel = VolumeBoostLevel.Boost200,
                drcLevel = DRCLevel.Strong,
                speechEnhancer = true,
                autoVolumeLeveling = true,
                skipSilence = true,
                noiseGateLevel = NoiseGateLevel.Strong,
            )

        val disabled = AudioProcessingSettings.processorsDisabled(settings)

        assertFalse(AudioProcessingSettings.hasAnyProcessorEnabled(disabled))
    }
}
