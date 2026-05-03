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

import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * compensator.applyCompensation(bookId, booksDao, scope) { getActivePlayer() }
 * ```
 *
 * Thread-safety: [previousBookLufs] is accessed from the main thread only.
 * The computation methods are stateless and safe to call from any thread.
 */
public class BookLoudnessCompensator(
    private val policy: LufsLoudnessCompensationPolicy = LufsLoudnessCompensationPolicy(),
) {
    /** Tracks the LUFS value of the previously playing book for transition gain. */
    public var previousBookLufs: Double? = null
        private set

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

    /**
     * Applies loudness compensation when switching books.
     *
     * Reads the new book's measured LUFS value from the database and computes
     * the gain needed to maintain consistent perceived volume. The gain is
     * applied to the player volume after the book switch.
     *
     * This is a fire-and-forget operation — if the LUFS value is not yet
     * available, the volume remains unchanged (gain = 1.0).
     *
     * @param newBookId the bookId of the book being switched to
     * @param booksDao DAO for reading LUFS values
     * @param scope Coroutine scope for async work
     * @param getActivePlayer Returns the current ExoPlayer instance
     */
    public fun applyCompensation(
        newBookId: String,
        booksDao: BooksDao,
        scope: CoroutineScope,
        getActivePlayer: () -> androidx.media3.exoplayer.ExoPlayer,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val bookEntity = booksDao.getBookById(newBookId)
                val newLufs = bookEntity?.lufsValue
                val prevLufs = previousBookLufs

                val gain =
                    if (prevLufs != null && newLufs != null) {
                        computeTransitionGain(prevLufs, newLufs)
                    } else {
                        computeBookGain(newLufs)
                    }

                previousBookLufs = newLufs

                if (gain != NO_GAIN) {
                    withContext(Dispatchers.Main) {
                        val player = getActivePlayer()
                        player.volume = (player.volume * gain).coerceIn(0f, 1f)
                        LogUtils.i(
                            "BookLoudnessCompensator",
                            "Compensation applied: gain=$gain, lufs=$newLufs, book=$newBookId",
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LogUtils.w(
                    "BookLoudnessCompensator",
                    "Failed to apply compensation for book=$newBookId: ${e.message}",
                )
                CrashDiagnostics.reportNonFatal("book_loudness_compensation", e)
            }
        }
    }

    public companion object {
        /** No gain adjustment — pass-through. */
        public const val NO_GAIN: Float = 1.0f

        /** Acceptable range for the computed gain multiplier. */
        public val GAIN_RANGE: ClosedRange<Float> = 0.06f..16.0f
    }
}
