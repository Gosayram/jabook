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

import com.jabook.app.jabook.util.LogUtils

/**
 * Policy that calculates an adaptive DRC threshold based on the measured
 * loudness (LUFS) of the current audiobook.
 *
 * Different audiobooks have very different dynamic ranges. A quiet recording
 * (LUFS < -23) needs a higher threshold so the compressor does not over-compress.
 * A loud recording (LUFS > -16) benefits from a lower threshold for more
 * aggressive compression.
 *
 * When no LUFS data is available, the policy falls back to the default threshold
 * for the selected [DRCLevel].
 *
 * P-04: DynamicRangeCompressor — automatic threshold calibration by LUFS.
 */
public object AdaptiveDrcThresholdPolicy {

    private const val TAG = "AdaptiveDrcPolicy"

    /** Target LUFS level for audiobook playback. */
    private const val TARGET_LUFS = -16.0f

    /** Quiet book threshold — LUFS below this needs gentler compression. */
    private const val QUIET_LUFS_THRESHOLD = -23.0f

    /** Loud book threshold — LUFS above this needs more aggressive compression. */
    private const val LOUD_LUFS_THRESHOLD = -16.0f

    /**
     * Calculates an adaptive threshold in dB for the given DRC level and
     * the measured LUFS of the current audio file/book.
     *
     * @param drcLevel       The selected compression level.
     * @param measuredLufs   The measured integrated loudness in LUFS, or `null`
     *                       if no analysis is available.
     * @return Threshold in dB to apply for compression.
     */
    public fun resolveThresholdDb(drcLevel: DRCLevel, measuredLufs: Float?): Float {
        if (measuredLufs == null || drcLevel == DRCLevel.Off) {
            return defaultThresholdDb(drcLevel)
        }

        val adaptiveThreshold = when {
            // Quiet recording: raise threshold above measured LUFS so
            // the compressor doesn't crush the already-quiet content.
            measuredLufs < QUIET_LUFS_THRESHOLD -> measuredLufs + 6.0f

            // Loud recording: lower threshold slightly below the peak
            // so the compressor catches peaks more aggressively.
            measuredLufs > LOUD_LUFS_THRESHOLD -> measuredLufs - 3.0f

            // Normal recording: use default for the selected level.
            else -> defaultThresholdDb(drcLevel)
        }

        LogUtils.d(TAG) {
            "Adaptive threshold=$adaptiveThreshold dB " +
                "(level=$drcLevel, measured=$measuredLufs LUFS)"
        }

        return adaptiveThreshold
    }

    /**
     * Default thresholds per [DRCLevel], used when LUFS data is unavailable.
     */
    public fun defaultThresholdDb(drcLevel: DRCLevel): Float = when (drcLevel) {
        DRCLevel.Off -> 0.0f
        DRCLevel.Gentle -> -32.0f
        DRCLevel.Medium -> -24.0f
        DRCLevel.Strong -> -18.0f
    }

    /**
     * Resolves the target LUFS level. This is useful for the
     * [LoudnessNormalizer] to know the desired loudness target.
     */
    public fun targetLufs(): Float = TARGET_LUFS
}