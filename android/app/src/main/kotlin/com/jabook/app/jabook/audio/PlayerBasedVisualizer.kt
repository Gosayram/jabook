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

import com.jabook.app.jabook.util.LogUtils

/**
 * P-22: Player-based audio visualizer that doesn't require RECORD_AUDIO permission.
 *
 * Instead of using `android.media.audiofx.Visualizer` (which requires RECORD_AUDIO),
 * this class provides FFT and waveform data by analyzing audio samples from the
 * ExoPlayer audio pipeline via an AudioProcessor.
 *
 * This avoids the permission requirement that concerns audiobook users.
 *
 * @param fftSize FFT window size (must be power of 2, typically 256, 512, 1024)
 */
public class PlayerBasedVisualizer(
    private val fftSize: Int = DEFAULT_FFT_SIZE,
) {
    private var latestFft = FloatArray(fftSize / 2)
    private var latestWaveform = FloatArray(fftSize)
    private var isActive = false
    private var frameCount = 0L

    /**
     * Called by the audio processor with new PCM samples.
     * This is the entry point for audio data from ExoPlayer.
     *
     * @param samples PCM audio samples (mono, float)
     */
    public fun onAudioSamples(samples: FloatArray) {
        if (!isActive) return

        frameCount++

        // Update waveform (raw samples)
        val copyLen = minOf(samples.size, latestWaveform.size)
        samples.copyInto(latestWaveform, 0, 0, copyLen)

        // Compute simplified FFT (magnitude spectrum)
        computeFft(samples)
    }

    /**
     * Returns the latest FFT magnitude data.
     * Each value represents the magnitude of a frequency bin (0.0–1.0).
     */
    public fun getFftData(): FloatArray = latestFft.copyOf()

    /**
     * Returns the latest waveform data.
     */
    public fun getWaveformData(): FloatArray = latestWaveform.copyOf()

    /**
     * Starts capturing audio data.
     */
    public fun start() {
        isActive = true
        frameCount = 0
        LogUtils.d(TAG, "Visualizer started (fftSize=$fftSize)")
    }

    /**
     * Stops capturing audio data.
     */
    public fun stop() {
        isActive = false
        LogUtils.d(TAG, "Visualizer stopped (frames=$frameCount)")
    }

    /**
     * Whether the visualizer is currently active.
     */
    public fun isCapturing(): Boolean = isActive

    /**
     * Returns the number of audio frames processed.
     */
    public fun getFrameCount(): Long = frameCount

    /**
     * Resets all buffers to zero.
     */
    public fun reset() {
        latestFft.fill(0f)
        latestWaveform.fill(0f)
        frameCount = 0
    }

    private fun computeFft(samples: FloatArray) {
        // Simplified DFT for magnitude spectrum
        // For production, use a proper FFT algorithm (e.g., Apache Commons Math)
        val n = minOf(samples.size, fftSize)
        val halfN = n / 2

        for (k in 0 until halfN) {
            var real = 0.0f
            var imag = 0.0f
            for (i in 0 until n) {
                val angle = (2.0 * Math.PI * k * i / n).toFloat()
                real += samples[i] * kotlin.math.cos(angle)
                imag -= samples[i] * kotlin.math.sin(angle)
            }
            val magnitude = kotlin.math.sqrt(real * real + imag * imag) / n
            latestFft[k] = magnitude.coerceIn(0f, 1f)
        }
    }

    public companion object {
        private const val TAG = "PlayerBasedVisualizer"
        internal const val DEFAULT_FFT_SIZE = 1024
    }
}
