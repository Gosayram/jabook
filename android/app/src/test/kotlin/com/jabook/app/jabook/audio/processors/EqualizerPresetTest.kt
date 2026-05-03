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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EqualizerPreset] contract and [mapPresetName] mapping.
 *
 * Verifies:
 * - Each preset has exactly [EqualizerPreset.BAND_COUNT] band gains.
 * - Preset display names are non-empty and unique.
 * - [mapPresetName] maps known strings correctly and falls back for unknowns.
 * - Voice Clarity and Night presets boost the speech frequency range.
 */
class EqualizerPresetTest {
    @Test
    fun `all presets have exactly BAND_COUNT band gains`() {
        for (preset in EqualizerPreset.entries) {
            assertEquals(
                "Preset ${preset.name} should have ${EqualizerPreset.BAND_COUNT} bands",
                EqualizerPreset.BAND_COUNT,
                preset.bandGainsMb.size,
            )
        }
    }

    @Test
    fun `all display names are non-empty and unique`() {
        val names = mutableSetOf<String>()
        for (preset in EqualizerPreset.entries) {
            assertTrue("Display name for ${preset.name} should be non-empty", preset.displayName.isNotEmpty())
            assertTrue("Display name '${preset.displayName}' should be unique", names.add(preset.displayName))
        }
    }

    @Test
    fun `FLAT preset has all zero gains`() {
        val gains = EqualizerPreset.FLAT.bandGainsMb
        for (i in gains.indices) {
            assertEquals("FLAT band $i should be 0 mB", 0, gains[i])
        }
    }

    @Test
    fun `VOICE_CLARITY boosts speech range (bands 3-7)`() {
        val gains = EqualizerPreset.VOICE_CLARITY.bandGainsMb
        // Bands 3..7 (250Hz..4kHz) should have positive gain
        for (i in 3..7) {
            assertTrue("VOICE_CLARITY band $i should boost (>${0}mB), got ${gains[i]}", gains[i] > 0)
        }
    }

    @Test
    fun `VOICE_CLARITY cuts sub-bass (bands 0-1)`() {
        val gains = EqualizerPreset.VOICE_CLARITY.bandGainsMb
        assertTrue("Band 0 should be cut", gains[0] < 0)
        assertTrue("Band 1 should be cut", gains[1] < 0)
    }

    @Test
    fun `NIGHT preset cuts bass (bands 0-2)`() {
        val gains = EqualizerPreset.NIGHT.bandGainsMb
        for (i in 0..2) {
            assertTrue("NIGHT band $i should be cut (<0mB), got ${gains[i]}", gains[i] < 0)
        }
    }

    @Test
    fun `NIGHT preset boosts mids (bands 4-5)`() {
        val gains = EqualizerPreset.NIGHT.bandGainsMb
        assertTrue("NIGHT band 4 (500Hz) should be boosted", gains[4] > 0)
        assertTrue("NIGHT band 5 (1kHz) should be boosted", gains[5] > 0)
    }

    @Test
    fun `DEFAULT constant is FLAT`() {
        assertEquals(EqualizerPreset.DEFAULT, EqualizerPreset.FLAT)
    }

    @Test
    fun `mapPresetName maps known strings correctly`() {
        assertEquals(EqualizerPreset.FLAT, mapPresetName("FLAT"))
        assertEquals(EqualizerPreset.VOICE_CLARITY, mapPresetName("VOICE_CLARITY"))
        assertEquals(EqualizerPreset.NIGHT, mapPresetName("NIGHT"))
    }

    @Test
    fun `mapPresetName falls back to DEFAULT for unknown strings`() {
        assertEquals(EqualizerPreset.DEFAULT, mapPresetName("UNKNOWN"))
        assertEquals(EqualizerPreset.DEFAULT, mapPresetName(""))
        assertEquals(EqualizerPreset.DEFAULT, mapPresetName("flat"))
    }

    @Test
    fun `BAND_COUNT is 10`() {
        assertEquals(10, EqualizerPreset.BAND_COUNT)
    }

    @Test
    fun `presets entries count is at least 3`() {
        assertTrue("Should have at least 3 presets", EqualizerPreset.entries.size >= 3)
    }

    @Test
    fun `all preset gains are within reasonable dB range`() {
        // +/- 15 dB = +/- 1500 mB — reasonable range for audiobook listening
        val maxGainMb = 1500
        for (preset in EqualizerPreset.entries) {
            for (i in preset.bandGainsMb.indices) {
                val gain = preset.bandGainsMb[i]
                assertTrue(
                    "${preset.name} band $i gain $gain mB should be in [-$maxGainMb, $maxGainMb]",
                    gain in -maxGainMb..maxGainMb,
                )
            }
        }
    }

    // --- TASK-VERM-09: Preamp protection tests ---

    @Test
    fun `FLAT preset has zero preamp`() {
        assertEquals(0, EqualizerPreset.FLAT.preampMillibels)
        assertEquals(0, EqualizerPreset.FLAT.effectivePreamp())
    }

    @Test
    fun `VOICE_CLARITY uses auto preamp`() {
        assertEquals(EqualizerPreset.PREAMP_AUTO, EqualizerPreset.VOICE_CLARITY.preampMillibels)
        // Max positive gain is 400mB, so effective preamp should be -400mB
        assertEquals(-400, EqualizerPreset.VOICE_CLARITY.effectivePreamp())
    }

    @Test
    fun `NIGHT uses auto preamp`() {
        assertEquals(EqualizerPreset.PREAMP_AUTO, EqualizerPreset.NIGHT.preampMillibels)
        // Max positive gain is 300mB, so effective preamp should be -300mB
        assertEquals(-300, EqualizerPreset.NIGHT.effectivePreamp())
    }

    @Test
    fun `calculateSafePreamp returns negative of max positive gain`() {
        val gains = intArrayOf(-200, 0, 300, 500, 200)
        assertEquals(-500, EqualizerPreset.calculateSafePreamp(gains))
    }

    @Test
    fun `calculateSafePreamp returns zero when all gains are negative`() {
        val gains = intArrayOf(-300, -200, -100)
        assertEquals(0, EqualizerPreset.calculateSafePreamp(gains))
    }

    @Test
    fun `calculateSafePreamp returns zero when all gains are zero`() {
        val gains = intArrayOf(0, 0, 0, 0)
        assertEquals(0, EqualizerPreset.calculateSafePreamp(gains))
    }

    @Test
    fun `effectivePreamp with auto preamp never causes clipping`() {
        for (preset in EqualizerPreset.entries) {
            val preamp = preset.effectivePreamp()
            val totalGains = preset.bandGainsMb.map { it + preamp }
            val maxTotal = totalGains.maxOrNull() ?: 0
            assertTrue(
                "Preset ${preset.name} with preamp $preamp should not clip (max total gain: $maxTotal mB)",
                maxTotal <= 0,
            )
        }
    }

    @Test
    fun `calculateHeadroomDb is non-negative with auto preamp`() {
        for (preset in EqualizerPreset.entries) {
            val preamp = preset.effectivePreamp()
            val headroom = EqualizerPreset.calculateHeadroomDb(preset.bandGainsMb, preamp)
            assertTrue(
                "Preset ${preset.name} should have non-negative headroom, got $headroom dB",
                headroom >= -0.01, // Allow tiny floating-point error
            )
        }
    }

    @Test
    fun `calculateHeadroomDb detects clipping risk`() {
        // No preamp with positive gains = negative headroom (clipping risk)
        val gains = intArrayOf(0, 0, 0, 400, 0)
        val headroom = EqualizerPreset.calculateHeadroomDb(gains, 0)
        assertTrue("Should detect clipping risk", headroom < 0)
    }
}
