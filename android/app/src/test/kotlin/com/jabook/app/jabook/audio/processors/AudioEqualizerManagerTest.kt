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
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AudioEqualizerManagerTest {
    @Test
    fun `initialize attaches equalizer and release unregisters listener`() {
        val player: ExoPlayer = mock()
        whenever(player.audioSessionId).thenReturn(42)

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.userPreferences)
            .thenReturn(flowOf(UserPreferences.newBuilder().setEqualizerPreset("NIGHT").build()))
        whenever(settingsRepository.customEqBands)
            .thenReturn(flowOf(List(10) { 0 }))

        val equalizer = mock<android.media.audiofx.Equalizer>()
        whenever(equalizer.numberOfBands).thenReturn(2.toShort())
        whenever(equalizer.bandLevelRange).thenReturn(shortArrayOf(-1000, 1000))

        val manager =
            object : AudioEqualizerManager(
                player = player,
                settingsRepository = settingsRepository,
            ) {
                override fun createEqualizer(sessionId: Int): android.media.audiofx.Equalizer = equalizer
            }

        manager.initialize()
        manager.release()

        verify(player).addListener(any<Player.Listener>())
        verify(player).removeListener(any<Player.Listener>())
        verify(equalizer, atLeastOnce()).setBandLevel(any(), any())
        verify(equalizer, atLeastOnce()).release()
    }

    @Test
    fun `initialize skips attach when audio session id is unset`() {
        val player: ExoPlayer = mock()
        whenever(player.audioSessionId).thenReturn(C.AUDIO_SESSION_ID_UNSET)

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.userPreferences)
            .thenReturn(flowOf(UserPreferences.newBuilder().setEqualizerPreset("FLAT").build()))
        whenever(settingsRepository.customEqBands)
            .thenReturn(flowOf(List(10) { 0 }))

        var factoryCalls = 0
        val manager =
            object : AudioEqualizerManager(
                player = player,
                settingsRepository = settingsRepository,
            ) {
                override fun createEqualizer(sessionId: Int): android.media.audiofx.Equalizer {
                    factoryCalls += 1
                    return mock()
                }
            }

        manager.initialize()
        manager.release()

        assertEquals(0, factoryCalls)
        assertEquals(0, manager.getBandCount())
        assertEquals(0, manager.getCenterFreq(0))
        assertEquals(0, manager.getBandLevel(0))
    }

    @Test
    fun `map preset falls back to default for unknown value`() {
        assertTrue(mapPresetName("UNKNOWN") == EqualizerPreset.DEFAULT)
    }

    @Test
    fun `custom preset with stored bands applies band gains to equalizer`() {
        val equalizer = mock<Equalizer>()
        whenever(equalizer.numberOfBands).thenReturn(10.toShort())
        whenever(equalizer.bandLevelRange).thenReturn(shortArrayOf(-1500, 1500))

        val manager = createManager(presetName = "CUSTOM", customBands = customBands, equalizer = equalizer)
        manager.initialize()
        shadowOf(Looper.getMainLooper()).idle()
        manager.release()

        // maxPositiveGain = 500 → safe preamp = -500 (calculateSafePreamp)
        assertEquals(expectedLevels(customBands, preamp = -500), lastLevelsPerBand(equalizer))
    }

    @Test
    fun `custom preset with empty bands falls back to flat`() {
        val equalizer = mock<Equalizer>()
        whenever(equalizer.numberOfBands).thenReturn(10.toShort())
        whenever(equalizer.bandLevelRange).thenReturn(shortArrayOf(-1500, 1500))

        val manager = createManager(presetName = "CUSTOM", customBands = emptyList(), equalizer = equalizer)
        manager.initialize()
        shadowOf(Looper.getMainLooper()).idle()
        manager.release()

        // Empty stored bands → CUSTOM's own (all-zero) gains with 0 preamp
        assertEquals(
            List(10) { it to 0.toShort() }.toMap(),
            lastLevelsPerBand(equalizer),
        )
    }

    @Test
    fun `non-custom preset ignores customEqBands`() {
        val equalizer = mock<Equalizer>()
        whenever(equalizer.numberOfBands).thenReturn(10.toShort())
        whenever(equalizer.bandLevelRange).thenReturn(shortArrayOf(-1500, 1500))

        val junkBands = List(10) { 999 }
        val manager = createManager(presetName = "NIGHT", customBands = junkBands, equalizer = equalizer)
        manager.initialize()
        shadowOf(Looper.getMainLooper()).idle()
        manager.release()

        // NIGHT uses PREAMP_AUTO: preamp = -maxPositive(NIGHT) = -350
        assertEquals(
            expectedLevels(EqualizerPreset.NIGHT.bandGainsMb.toList(), preamp = EqualizerPreset.NIGHT.effectivePreamp()),
            lastLevelsPerBand(equalizer),
        )
    }

    @Test
    fun `custom band beyond device range is clamped`() {
        val equalizer = mock<Equalizer>()
        whenever(equalizer.numberOfBands).thenReturn(10.toShort())
        whenever(equalizer.bandLevelRange).thenReturn(shortArrayOf(-300, 300))

        val manager = createManager(presetName = "CUSTOM", customBands = customBands, equalizer = equalizer)
        manager.initialize()
        shadowOf(Looper.getMainLooper()).idle()
        manager.release()

        assertEquals(expectedLevels(customBands, preamp = -500, min = -300, max = 300), lastLevelsPerBand(equalizer))
    }

    // ---- Helpers ----

    private val customBands = listOf(200, 400, 100, 300, 500, 0, 200, 100, 300, 400)

    private fun createManager(
        presetName: String,
        customBands: List<Int>,
        equalizer: Equalizer,
    ): AudioEqualizerManager {
        val player: ExoPlayer = mock()
        whenever(player.audioSessionId).thenReturn(42)

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.userPreferences)
            .thenReturn(flowOf(UserPreferences.newBuilder().setEqualizerPreset(presetName).build()))
        whenever(settingsRepository.customEqBands)
            .thenReturn(flowOf(customBands))

        return object : AudioEqualizerManager(
            player = player,
            settingsRepository = settingsRepository,
        ) {
            override fun createEqualizer(sessionId: Int): Equalizer = equalizer
        }
    }

    /** Last setBandLevel value per band index (initial DEFAULT attach runs before the collector). */
    private fun lastLevelsPerBand(equalizer: Equalizer): Map<Int, Short> {
        val bands = argumentCaptor<Short>()
        val levels = argumentCaptor<Short>()
        verify(equalizer, atLeastOnce()).setBandLevel(bands.capture(), levels.capture())
        val result = mutableMapOf<Int, Short>()
        bands.allValues.forEachIndexed { idx, band -> result[band.toInt()] = levels.allValues[idx] }
        return result
    }

    private fun expectedLevels(
        gains: List<Int>,
        preamp: Int,
        min: Int = -1500,
        max: Int = 1500,
    ): Map<Int, Short> = gains.indices.associateWith { i -> (gains[i] + preamp).coerceIn(min, max).toShort() }
}
