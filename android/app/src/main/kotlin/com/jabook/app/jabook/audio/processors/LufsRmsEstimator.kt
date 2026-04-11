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

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Estimates LUFS from PCM 16-bit sample data using RMS-based approximation.
 *
 * This is a simplified estimator suitable for background analysis (WorkManager).
 * It provides a single integrated loudness value for a chunk of audio samples,
 * which can be averaged across the whole book to produce a per-book LUFS estimate.
 *
 * Note: Full EBU R128 compliance requires K-weighted filtering; this estimator
 * uses raw RMS which is sufficient for relative loudness comparison between books.
 */
public object LufsRmsEstimator {
    /**
     * Estimate LUFS from a block of 16-bit PCM samples.
     *
     * @param samples 16-bit PCM samples normalized to -1.0..1.0
     * @return estimated LUFS value (typically -30..-6 range for speech), or null if insufficient data
     */
    public fun estimateLufs(samples: FloatArray): Double? {
        if (samples.size < MIN_SAMPLES_FOR_ESTIMATE) return null

        var sumSquares = 0.0
        for (sample in samples) {
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sumSquares / samples.size)
        if (rms < RMS_FLOOR) return null

        // Convert RMS to dB: dB = 20 * log10(rms)
        // Approximate LUFS offset: RMS-based LUFS ≈ RMS_dB - 0.691
        // (0.691 is a calibration offset for speech content)
        val rmsDb = 20.0 * log10(rms)
        return rmsDb - LUFS_CALIBRATION_OFFSET
    }

    /**
     * Estimate LUFS from raw 16-bit PCM byte data.
     *
     * @param pcm16Data byte buffer in native byte order (little-endian on most devices)
     * @param channels  number of audio channels
     * @return estimated LUFS value, or null if insufficient data
     */
    public fun estimateLufsFromPcm16(
        pcm16Data: ShortArray,
        channels: Int,
    ): Double? {
        if (channels <= 0 || pcm16Data.size < channels) return null

        val samples = FloatArray(pcm16Data.size / channels)
        for (i in samples.indices) {
            samples[i] = pcm16Data[i * channels].toFloat() / Short.MAX_VALUE
        }
        return estimateLufs(samples)
    }

    /**
     * Combines multiple LUFS estimates into a single integrated value.
     * Uses energy-weighted averaging (louder segments contribute more).
     *
     * @param estimates list of LUFS estimates from different audio chunks
     * @return integrated LUFS value, or null if no valid estimates
     */
    public fun integrateEstimates(estimates: List<Double>): Double? {
        val validEstimates = estimates.filter { it.isFinite() }
        if (validEstimates.isEmpty()) return null

        // Energy-weighted average: convert each LUFS to linear power, average, convert back
        var totalPower = 0.0
        for (lufs in validEstimates) {
            totalPower += 10.0.pow(lufs / 10.0)
        }
        val avgPower = totalPower / validEstimates.size
        return 10.0 * log10(avgPower)
    }

    private const val MIN_SAMPLES_FOR_ESTIMATE = 1024
    private const val RMS_FLOOR = 1e-5
    private const val LUFS_CALIBRATION_OFFSET = 0.691
}
