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

import kotlin.math.pow

/**
 * Calculates the linear gain multiplier needed to compensate loudness
 * between books based on their measured LUFS values relative to a target.
 *
 * Usage: when switching books, compute the compensation gain from the
 * previous book's LUFS to the new book's LUFS, so the listener
 * perceives consistent volume without manual adjustment.
 *
 * The target LUFS defaults to [AutoVolumeLeveler.AUDIOBOOK_TARGET_LUFS] (-16 dB)
 * but can be tuned via [targetLufs].
 */
public class LufsLoudnessCompensationPolicy(
    private val targetLufs: Double = AutoVolumeLeveler.AUDIOBOOK_TARGET_LUFS,
) {
    init {
        require(targetLufs in TARGET_LUFS_RANGE) {
            "targetLufs must be in ${TARGET_LUFS_RANGE.start}..${TARGET_LUFS_RANGE.endInclusive}, got $targetLufs"
        }
    }

    /**
     * Computes a linear gain multiplier that brings [bookLufs] closer to [targetLufs].
     *
     * @param bookLufs measured LUFS of the book (negative, e.g. -20.0)
     * @return linear gain multiplier (e.g. 1.58 for a -20 LUFS book targeting -16)
     */
    public fun compensationGain(bookLufs: Double): Float {
        if (bookLufs.isNaN() || bookLufs >= 0.0) return NO_COMPENSATION
        val deltaDb = targetLufs - bookLufs
        val gain = 10.0.pow(deltaDb / 20.0).toFloat()
        return gain.coerceIn(GAIN_MIN, GAIN_MAX)
    }

    /**
     * Computes the relative gain change when switching from one book to another.
     *
     * @param previousBookLufs LUFS of the book that was playing
     * @param newBookLufs       LUFS of the book that will play next
     * @return linear gain multiplier to apply on switch
     */
    public fun transitionGain(
        previousBookLufs: Double,
        newBookLufs: Double,
    ): Float {
        val previousGain = compensationGain(previousBookLufs)
        val newGain = compensationGain(newBookLufs)
        return (newGain / previousGain).coerceIn(GAIN_MIN, GAIN_MAX)
    }

    public companion object {
        /** Acceptable LUFS range for target setting. */
        public val TARGET_LUFS_RANGE: ClosedRange<Double> = -23.0..-14.0

        internal const val NO_COMPENSATION: Float = 1.0f

        /** Minimum linear gain (≈ -24 dB). */
        internal const val GAIN_MIN: Float = 0.06f

        /** Maximum linear gain (≈ +24 dB). */
        internal const val GAIN_MAX: Float = 16.0f
    }
}
