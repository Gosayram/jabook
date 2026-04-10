package com.jabook.app.jabook.audio.processors

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
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioEqualizerManagerTest {
    @Test
    fun `initialize attaches equalizer and release unregisters listener`() {
        val player: ExoPlayer = mock()
        whenever(player.audioSessionId).thenReturn(42)

        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.userPreferences)
            .thenReturn(flowOf(UserPreferences.newBuilder().setEqualizerPreset("NIGHT").build()))

        val equalizer = mock<android.media.audiofx.Equalizer>()
        whenever(equalizer.numberOfBands).thenReturn(2.toShort())
        whenever(equalizer.bandLevelRange).thenReturn(shortArrayOf(-1000, 1000))

        val manager =
            AudioEqualizerManager(
                player = player,
                settingsRepository = settingsRepository,
                eqFactory = { equalizer },
            )

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

        var factoryCalls = 0
        val manager =
            AudioEqualizerManager(
                player = player,
                settingsRepository = settingsRepository,
                eqFactory = {
                    factoryCalls += 1
                    mock()
                },
            )

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
}
