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

package com.jabook.app.jabook.audio

import android.content.Context
import android.media.audiofx.Visualizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioVisualizerManagerTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `initialize with denied permission stays inactive and does not create visualizer`() {
        var factoryCalled = false
        val manager =
            AudioVisualizerManager(
                context = context,
                permissionChecker = { false },
                visualizerFactory = {
                    factoryCalled = true
                    mock()
                },
            )

        manager.initialize(audioSessionId = 11)

        assertFalse(factoryCalled)
        assertFalse(manager.isActive.value)
        assertEquals(256, manager.waveformData.value.size)
        assertEquals(128, manager.fftData.value.size)
    }

    @Test
    fun `initialize with invalid session id releases existing visualizer`() {
        val visualizer = mock<Visualizer>()
        val manager =
            AudioVisualizerManager(
                context = context,
                permissionChecker = { true },
                visualizerFactory = { visualizer },
            )

        manager.initialize(audioSessionId = 42)
        assertTrue(manager.isActive.value)

        manager.initialize(audioSessionId = 0)

        verify(visualizer).release()
        assertFalse(manager.isActive.value)
    }

    @Test
    fun `reinitialize after permission revoked releases current visualizer and skips new one`() {
        var permissionGranted = true
        val visualizer = mock<Visualizer>()
        var factoryCalls = 0

        val manager =
            AudioVisualizerManager(
                context = context,
                permissionChecker = { permissionGranted },
                visualizerFactory = {
                    factoryCalls++
                    visualizer
                },
            )

        manager.initialize(audioSessionId = 7)
        assertTrue(manager.isActive.value)
        assertEquals(1, factoryCalls)

        permissionGranted = false
        manager.initialize(audioSessionId = 8)

        assertEquals(1, factoryCalls)
        verify(visualizer).release()
        assertFalse(manager.isActive.value)
    }

    @Test
    fun `setEnabled without initialized visualizer does not activate manager`() {
        val manager =
            AudioVisualizerManager(
                context = context,
                permissionChecker = { true },
                visualizerFactory = { mock() },
            )

        manager.setEnabled(enabled = true)

        assertFalse(manager.isActive.value)
    }

    @Test
    fun `setEnabled true reinitializes visualizer after permission is granted`() {
        var permissionGranted = false
        var factoryCalls = 0

        val manager =
            AudioVisualizerManager(
                context = context,
                permissionChecker = { permissionGranted },
                visualizerFactory = {
                    factoryCalls++
                    mock()
                },
            )

        manager.initialize(audioSessionId = 25)
        assertEquals(0, factoryCalls)

        permissionGranted = true
        manager.setEnabled(enabled = true)

        assertEquals(1, factoryCalls)
        assertTrue(manager.isActive.value)
    }

    @Test
    fun `release clears remembered session and setEnabled does not reinitialize`() {
        var permissionGranted = false
        var factoryCalls = 0

        val manager =
            AudioVisualizerManager(
                context = context,
                permissionChecker = { permissionGranted },
                visualizerFactory = {
                    factoryCalls++
                    mock()
                },
            )

        manager.initialize(audioSessionId = 30)
        manager.release()

        permissionGranted = true
        manager.setEnabled(enabled = true)

        assertEquals(0, factoryCalls)
        assertFalse(manager.isActive.value)
    }
}
