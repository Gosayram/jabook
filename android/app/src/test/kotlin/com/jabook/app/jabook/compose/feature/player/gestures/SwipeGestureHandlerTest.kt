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

package com.jabook.app.jabook.compose.feature.player.gestures

import android.content.Context
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeGestureHandlerTest {
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var window: Window
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var handler: SwipeGestureHandler

    @Before
    fun setup() {
        context = mock()
        audioManager = mock()
        window = mock()
        layoutParams = WindowManager.LayoutParams()

        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)
        whenever(window.attributes).thenReturn(layoutParams)

        // Mock max volume
        whenever(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(100)
        whenever(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(50)

        // Reset fields
        layoutParams.screenBrightness = 0.5f

        handler = SwipeGestureHandler(context)
    }

    @Test
    fun `adjustBrightness clamps value between 0_01 and 1_0`() {
        // Swipe up (negative delta) increases brightness
        val newBrightness = handler.adjustBrightness(-0.6f, window, 0.5f)

        // 0.5 - (-0.6) = 1.1 -> clamped to 1.0
        assertEquals(1.0f, newBrightness, 0.01f)
        assertEquals(1.0f, layoutParams.screenBrightness, 0.01f)

        // Swipe down (positive delta) decreases brightness
        val dimBrightness = handler.adjustBrightness(0.6f, window, 0.5f)

        // 0.5 - 0.6 = -0.1 -> clamped to 0.01
        assertEquals(0.01f, dimBrightness, 0.01f)
    }

    @Test
    fun `adjustVolume clamps value between 0 and max`() {
        // Current volume 50, max 100

        // Swipe up (negative delta) increases volume
        // Volume change: -(-0.2) * 100 * 0.5 = 10
        val newVolume = handler.adjustVolume(-0.2f)
        assertEquals(60, newVolume)
        verify(audioManager).setStreamVolume(eq(AudioManager.STREAM_MUSIC), eq(60), any())

        // Swipe down (positive delta) decreases volume
        // Volume change: -(0.2) * 100 * 0.5 = -10
        whenever(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(50)
        val lowVolume = handler.adjustVolume(0.2f)
        assertEquals(40, lowVolume)
    }

    @Test
    fun `calculateSeekDelta returns proportional value`() {
        // Screen width 1000, drag 100 (10%)
        // Normalized 0.1
        // Curved 0.1 * 0.1 = 0.01
        // Max seek 120,000ms
        // Result: 0.01 * 120,000 = 1200ms

        val delta = handler.calculateSeekDelta(100f, 1000f)
        assertEquals(1200L, delta)
    }

    @Test
    fun `calculateSeekDelta is symmetric`() {
        val forward = handler.calculateSeekDelta(100f, 1000f)
        val backward = handler.calculateSeekDelta(-100f, 1000f)

        assertEquals(forward, -backward)
    }

    @Test
    fun `formatSeekDelta formats correctly`() {
        assertEquals("+10s", handler.formatSeekDelta(10000))
        assertEquals("-5s", handler.formatSeekDelta(-5000))
        assertEquals("+1:00", handler.formatSeekDelta(60000))
        assertEquals("-1:30", handler.formatSeekDelta(-90000))
    }
}
