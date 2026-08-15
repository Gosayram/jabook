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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioProcessorChainTest {
    // --- Processor ordering ---

    @Test
    fun `normalizer comes before volume boost in chain`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = true,
                volumeBoostLevel = VolumeBoostLevel.Boost50,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        val names = result.processors.map { it.javaClass.simpleName }
        val normalizerIndex = names.indexOf("LoudnessNormalizer")
        val boostIndex = names.indexOf("VolumeBoostProcessor")

        assertTrue("Normalizer should be in chain", normalizerIndex >= 0)
        assertTrue("VolumeBoost should be in chain", boostIndex >= 0)
        assertTrue(
            "Normalizer (idx=$normalizerIndex) should come before VolumeBoost (idx=$boostIndex)",
            normalizerIndex < boostIndex,
        )
    }

    @Test
    fun `volume boost comes before DRC in chain`() {
        val settings =
            AudioProcessingSettings(
                volumeBoostLevel = VolumeBoostLevel.Boost50,
                drcLevel = DRCLevel.Medium,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        val names = result.processors.map { it.javaClass.simpleName }
        val boostIndex = names.indexOf("VolumeBoostProcessor")
        val drcIndex = names.indexOf("DynamicRangeCompressor")

        assertTrue("VolumeBoost should be in chain", boostIndex >= 0)
        assertTrue("DRC should be in chain", drcIndex >= 0)
        assertTrue(
            "VolumeBoost (idx=$boostIndex) should come before DRC (idx=$drcIndex)",
            boostIndex < drcIndex,
        )
    }

    @Test
    fun `DRC comes before speech enhancer in chain`() {
        val settings =
            AudioProcessingSettings(
                drcLevel = DRCLevel.Gentle,
                speechEnhancer = true,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        val names = result.processors.map { it.javaClass.simpleName }
        val drcIndex = names.indexOf("DynamicRangeCompressor")
        val enhancerIndex = names.indexOf("SpeechEnhancer")

        assertTrue("DRC should be in chain", drcIndex >= 0)
        assertTrue("SpeechEnhancer should be in chain", enhancerIndex >= 0)
        assertTrue(
            "DRC (idx=$drcIndex) should come before SpeechEnhancer (idx=$enhancerIndex)",
            drcIndex < enhancerIndex,
        )
    }

    @Test
    fun `skip silence comes last in chain`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = true,
                skipSilence = true,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        val names = result.processors.map { it.javaClass.simpleName }
        val skipSilenceIndex = names.indexOf("SkipSilenceAudioProcessor")
        val lastIndex = names.size - 1

        assertTrue("SkipSilence should be in chain", skipSilenceIndex >= 0)
        assertEquals("SkipSilence should be last processor", lastIndex, skipSilenceIndex)
    }

    // --- Empty chain ---

    @Test
    fun `all disabled produces empty chain`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = false,
                volumeBoostLevel = VolumeBoostLevel.Off,
                drcLevel = DRCLevel.Off,
                speechEnhancer = false,
                autoVolumeLeveling = false,
                skipSilence = false,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertTrue("Chain should be empty when all disabled", result.processors.isEmpty())
        assertNull("No loudnessNormalizer when disabled", result.loudnessNormalizer)
    }

    // --- LoudnessNormalizer reference ---

    @Test
    fun `loudnessNormalizer reference provided when enabled`() {
        val settings = AudioProcessingSettings(normalizeVolume = true)
        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertNotNull("loudnessNormalizer should be provided", result.loudnessNormalizer)
        assertTrue(
            "First processor should be LoudnessNormalizer",
            result.processors.first() is LoudnessNormalizer,
        )
        assertSame(result.processors.first(), result.loudnessNormalizer)
    }

    @Test
    fun `loudnessNormalizer is null when disabled`() {
        val settings = AudioProcessingSettings(normalizeVolume = false)
        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertNull(result.loudnessNormalizer)
    }

    // --- Full chain order ---

    @Test
    fun `full chain follows documented order`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = true,
                volumeBoostLevel = VolumeBoostLevel.Boost100,
                drcLevel = DRCLevel.Medium,
                speechEnhancer = true,
                autoVolumeLeveling = true,
                skipSilence = true,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        val names = result.processors.map { it.javaClass.simpleName }
        assertEquals("Should have 6 processors", 6, names.size)
        assertEquals("LoudnessNormalizer", names[0])
        assertEquals("VolumeBoostProcessor", names[1])
        assertEquals("DynamicRangeCompressor", names[2])
        assertEquals("SpeechEnhancer", names[3])
        assertEquals("AutoVolumeLeveler", names[4])
        assertEquals("SkipSilenceAudioProcessor", names[5])
    }

    // --- Individual processor enablement ---

    @Test
    fun `only normalizeVolume enabled creates single processor`() {
        val settings = AudioProcessingSettings(normalizeVolume = true)
        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertEquals(1, result.processors.size)
        assertTrue(result.processors[0] is LoudnessNormalizer)
    }

    @Test
    fun `only skipSilence enabled creates single processor`() {
        val settings =
            AudioProcessingSettings(
                normalizeVolume = false,
                skipSilence = true,
            )
        val result = AudioProcessorFactory.createProcessorChain(settings)

        assertEquals(1, result.processors.size)
        assertTrue(result.processors[0] is SkipSilenceAudioProcessor)
    }
}
