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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerBasedVisualizerTest {
    private lateinit var visualizer: PlayerBasedVisualizer

    @Before
    fun setUp() {
        visualizer = PlayerBasedVisualizer(fftSize = 256)
    }

    // --- Initial state ---

    @Test
    fun `initially not capturing`() {
        assertFalse(visualizer.isCapturing())
        assertEquals(0L, visualizer.getFrameCount())
    }

    // --- Start/stop ---

    @Test
    fun `start activates capturing`() {
        visualizer.start()
        assertTrue(visualizer.isCapturing())
    }

    @Test
    fun `stop deactivates capturing`() {
        visualizer.start()
        visualizer.stop()
        assertFalse(visualizer.isCapturing())
    }

    // --- Audio samples ---

    @Test
    fun `samples ignored when not active`() {
        val samples = FloatArray(256) { 0.5f }
        visualizer.onAudioSamples(samples)
        assertEquals(0L, visualizer.getFrameCount())
    }

    @Test
    fun `samples processed when active`() {
        visualizer.start()
        val samples = FloatArray(256) { 0.5f }
        visualizer.onAudioSamples(samples)
        assertEquals(1L, visualizer.getFrameCount())
    }

    // --- Waveform data ---

    @Test
    fun `waveform returns non-zero after samples`() {
        visualizer.start()
        val samples = FloatArray(256) { 0.8f }
        visualizer.onAudioSamples(samples)
        val waveform = visualizer.getWaveformData()
        assertTrue(waveform.any { it > 0f })
    }

    // --- FFT data ---

    @Test
    fun `fft returns correct size`() {
        val fft = visualizer.getFftData()
        assertEquals(128, fft.size) // fftSize / 2
    }

    // --- Reset ---

    @Test
    fun `reset clears all data`() {
        visualizer.start()
        visualizer.onAudioSamples(FloatArray(256) { 1f })
        visualizer.reset()
        assertEquals(0L, visualizer.getFrameCount())
        assertTrue(visualizer.getFftData().all { it == 0f })
    }

    // --- Default FFT size ---

    @Test
    fun `default FFT size is 1024`() {
        assertEquals(1024, PlayerBasedVisualizer.DEFAULT_FFT_SIZE)
    }
}
