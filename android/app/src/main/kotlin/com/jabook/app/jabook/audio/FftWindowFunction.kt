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

import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow

/**
 * FFT windowing functions for reducing spectral leakage in audio visualization.
 *
 * P-23: Without a window function, FFT analysis suffers from spectral leakage —
 * frequency peaks "smear" across adjacent bins, making the visualizer look blurry.
 * The Hann window reduces this by tapering the edges of each FFT frame.
 *
 * Also provides logarithmic frequency band mapping for human-perception-aligned
 * visualization (bass frequencies get more detail, treble less).
 */
public object FftWindowFunction {
    /**
     * Applies a Hann window to the given sample buffer (in-place).
     *
     * The Hann window: `w(n) = 0.5 * (1 - cos(2π * n / (N-1)))`
     *
     * @param samples PCM samples (will be modified in-place)
     * @return The same array, windowed
     */
    public fun applyHann(samples: FloatArray): FloatArray {
        val n = samples.size
        if (n < 2) return samples
        for (i in samples.indices) {
            val window = 0.5f * (1f - cos(2.0 * PI * i / (n - 1)).toFloat())
            samples[i] *= window
        }
        return samples
    }

    /**
     * Converts linear FFT bins to logarithmic frequency bands.
     *
     * Human hearing is logarithmic — we perceive frequency ratios, not absolute
     * differences. This mapping gives more visual detail to bass/mid frequencies
     * and less to treble, matching human perception.
     *
     * @param fft Magnitude spectrum (first half of FFT output)
     * @param numBands Number of output bands (e.g., 32 for a 32-bar visualizer)
     * @param sampleRateHz Audio sample rate (default 44100)
     * @return Array of [numBands] magnitude values in log frequency scale
     */
    public fun toLogBands(
        fft: FloatArray,
        numBands: Int,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE,
    ): FloatArray {
        val result = FloatArray(numBands)
        val logMin = log10(MIN_FREQ_HZ)
        val logMax = log10(MAX_FREQ_HZ)
        val logStep = (logMax - logMin) / numBands

        for (i in 0 until numBands) {
            val freqLow = 10f.pow(logMin + logStep * i)
            val freqHigh = 10f.pow(logMin + logStep * (i + 1))
            val binLow = (freqLow * fft.size / sampleRateHz).toInt().coerceAtLeast(0)
            val binHigh = (freqHigh * fft.size / sampleRateHz).toInt().coerceAtMost(fft.size - 1)

            result[i] =
                if (binLow <= binHigh) {
                    var max = 0f
                    for (b in binLow..binHigh) {
                        if (fft[b] > max) max = fft[b]
                    }
                    max
                } else {
                    0f
                }
        }
        return result
    }

    private const val PI = 3.14159265f
    internal const val DEFAULT_SAMPLE_RATE = 44100
    internal const val MIN_FREQ_HZ = 20f
    internal const val MAX_FREQ_HZ = 20_000f
}
