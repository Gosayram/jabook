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

/**
 * Unit tests for [AudioOffloadCompatibilityPolicy].
 *
 * P-05: Verifies audio offload compatibility with processing settings.
 */
class AudioOffloadCompatibilityPolicyTest {
    @Test
    fun `default settings are offload compatible`() {
        val settings = AudioProcessingSettings.defaults()
        assertTrue(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `normalizeVolume disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(normalizeVolume = true)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `speechEnhancer disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(speechEnhancer = true)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `autoVolumeLeveling disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(autoVolumeLeveling = true)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `DRC Gentle disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(drcLevel = DRCLevel.Gentle)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `DRC Medium disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(drcLevel = DRCLevel.Medium)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `DRC Strong disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(drcLevel = DRCLevel.Strong)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `DRC Off keeps offload enabled`() {
        val settings = AudioProcessingSettings.defaults().copy(drcLevel = DRCLevel.Off)
        assertTrue(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `volume boost Plus6dB disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(volumeBoostLevel = VolumeBoostLevel.Plus6dB)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `skipSilence disables offload`() {
        val settings = AudioProcessingSettings.defaults().copy(skipSilence = true)
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    @Test
    fun `multiple processors active disables offload`() {
        val settings =
            AudioProcessingSettings.defaults().copy(
                normalizeVolume = true,
                speechEnhancer = true,
            )
        assertFalse(AudioOffloadCompatibilityPolicy.isOffloadCompatible(settings))
    }

    // --- Gapless ---

    @Test
    fun `gapless possible with default settings`() {
        val settings = AudioProcessingSettings.defaults()
        assertTrue(AudioOffloadCompatibilityPolicy.isGaplessPossible(settings))
    }

    @Test
    fun `gapless not possible with crossfade enabled`() {
        val settings =
            AudioProcessingSettings.defaults().copy(
                isCrossfadeEnabled = true,
                crossfadeDurationMs = 3000L,
            )
        assertFalse(AudioOffloadCompatibilityPolicy.isGaplessPossible(settings))
    }

    @Test
    fun `gapless possible with crossfade disabled`() {
        val settings =
            AudioProcessingSettings.defaults().copy(
                isCrossfadeEnabled = false,
            )
        assertTrue(AudioOffloadCompatibilityPolicy.isGaplessPossible(settings))
    }
}
