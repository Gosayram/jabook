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

/**
 * Manages per-book loudness compensation in the playback pipeline.
 *
 * When the user switches from one book to another, the perceived loudness
 * can differ significantly. This compensator reads each book's measured LUFS
 * value (computed by [LufsAnalysisWorker]) and applies a gain correction
 * via [LufsLoudnessCompensationPolicy] so that the listener perceives
 * consistent volume across books without manual adjustment.
 *
 * Usage:
 * ```
 * val compensator = BookLoudnessCompensator(policy)
 * val gain = compensator.computeBookGain(bookLufs = -20.0, previousBookLufs = -16.0)
 * player.volume = gain.coerceIn(0f, 1f)
 * ```
 *
 * Thread-safety: this class is stateless and safe to call from any thread.
 * The [previousBookLufs] state is managed externally by the caller.
 */
public class BookLoudnessCompensator(
    private val policy: LufsLoudnessCompensationPolicy = LufsLoudnessCompensationPolicy(),
) {
    /**
     * Computes the absolute linear gain to apply for a book based on its
     * measured LUFS value relative to the configured target LUFS.
     *
     * Returns [NO_GAIN] (1.0) if [bookLufs] is null (not yet analyzed),
     * NaN, or non-negative (invalid measurement).
     *
     * @param bookLufs measured LUFS of the book, or null if not yet analyzed
     * @return linear gain multiplier in [GAIN_RANGE], or 1.0 for no compensation
     */
    public fun computeBookGain(bookLufs: Double?): Float {
        if (bookLufs == null || bookLufs.isNaN() || bookLufs >= 0.0) return NO_GAIN
        return policy.compensationGain(bookLufs)
    }

    /**
     * Computes the relative gain change when transitioning between two books.
     *
     * Useful when the player already has a loudness-compensated volume and
     * only needs the delta between books.
     *
     * @param previousBookLufs LUFS of the book that was playing, or null
     * @param newBookLufs       LUFS of the book that will play next, or null
     * @return linear gain multiplier for the transition, or 1.0 if either value is unavailable
     */
    public fun computeTransitionGain(
        previousBookLufs: Double?,
        newBookLufs: Double?,
    ): Float {
        if (previousBookLufs == null || newBookLufs == null) return NO_GAIN
        if (previousBookLufs.isNaN() || newBookLufs.isNaN()) return NO_GAIN
        if (previousBookLufs >= 0.0 || newBookLufs >= 0.0) return NO_GAIN
        return policy.transitionGain(previousBookLufs, newBookLufs)
    }

    public companion object {
        /** No gain adjustment — pass-through. */
        public const val NO_GAIN: Float = 1.0f

        /** Acceptable range for the computed gain multiplier. */
        public val GAIN_RANGE: ClosedRange<Float> = 0.06f..16.0f
    }
}
