package com.jabook.app.jabook.audio.processors

import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * Smooths volume transitions between chapters of the same book.
 *
 * Different chapters may have been recorded at different loudness levels.
 * This policy reads each chapter's measured LUFS value (computed by [LufsAnalysisWorker])
 * and smoothly adjusts the player volume to maintain consistent perceived loudness.
 *
 * ## How it works
 * 1. When a chapter transition occurs ([onChapterTransition]), the policy reads the
 *    chapter's pre-computed LUFS value from the database.
 * 2. It computes a gain multiplier relative to the EBU R128 speech reference (-23 LUFS).
 * 3. The volume is smoothly interpolated from the current level to the target level
 *    over [TRANSITION_DURATION_MS] milliseconds, preventing abrupt jumps.
 *
 * If a chapter has no LUFS value (null, e.g. not yet analyzed), no adjustment is applied.
 */
internal class ChapterLoudnessTransitionPolicy(
    private val player: ExoPlayer,
    private val chaptersDao: ChaptersDao,
    private val scope: CoroutineScope,
) {
    private var transitionJob: Job? = null
    private var currentBookId: String? = null
    private var previousChapterGain: Float? = null

    /**
     * Notifies the policy that the active book has changed.
     * Resets the per-chapter gain tracking so the first chapter of
     * the new book starts fresh rather than transitioning from the old book's gain.
     */
    public fun onBookChanged(bookId: String?) {
        currentBookId = bookId
        previousChapterGain = null
        transitionJob?.cancel()
        transitionJob = null
    }

    /**
     * Called when a chapter transition occurs (e.g. from [onMediaItemTransition]).
     *
     * Reads the new chapter's LUFS from the database, computes the target gain,
     * and smoothly adjusts the player volume over [TRANSITION_DURATION_MS].
     *
     * @param chapterIndex the index of the newly active chapter
     */
    public fun onChapterTransition(chapterIndex: Int) {
        val bookId = currentBookId ?: return

        transitionJob?.cancel()
        transitionJob =
            scope.launch {
                val chapter = chaptersDao.getChapterByIndex(bookId, chapterIndex) ?: return@launch
                val lufs = chapter.lufsValue ?: return@launch

                val newGain = computeGain(lufs)
                val ratio =
                    if (previousChapterGain != null) {
                        (newGain / previousChapterGain!!).coerceIn(GAIN_MIN, GAIN_MAX)
                    } else {
                        1f
                    }
                previousChapterGain = newGain

                if (abs(ratio - 1f) < GAIN_EPSILON) return@launch

                val startVolume = player.volume
                val targetVolume = (startVolume * ratio).coerceIn(0f, 1f)

                LogUtils.d(
                    TAG,
                    "Chapter transition: idx=$chapterIndex, lufs=$lufs, " +
                        "ratio=${"%.3f".format(ratio)}, " +
                        "volume ${"%.3f".format(startVolume)} → ${"%.3f".format(targetVolume)}",
                )

                animateVolume(startVolume, targetVolume)
            }
    }

    /**
     * Cancels any in-progress volume transition and releases resources.
     */
    public fun release() {
        transitionJob?.cancel()
        transitionJob = null
        currentBookId = null
        previousChapterGain = null
    }

    private suspend fun animateVolume(
        from: Float,
        to: Float,
    ) {
        val startMs = currentTimeMs()
        while (true) {
            val elapsed = currentTimeMs() - startMs
            val progress = (elapsed.toFloat() / TRANSITION_DURATION_MS).coerceAtMost(1f)
            val volume = from + (to - from) * progress
            player.volume = volume.coerceIn(0f, 1f)
            if (progress >= 1f) break
            delay(FRAME_INTERVAL_MS)
        }
    }

    private fun computeGain(lufs: Double): Float {
        val deltaDb = CHAPTER_TARGET_LUFS - lufs
        return 10.0.pow(deltaDb / 20.0).toFloat().coerceIn(GAIN_MIN, GAIN_MAX)
    }

    private fun currentTimeMs(): Long = System.currentTimeMillis()

    private companion object {
        private const val TAG = "ChapterLoudnessPolicy"

        /** EBU R128 speech reference: -23 LUFS. */
        private const val CHAPTER_TARGET_LUFS: Double = -23.0

        /** Duration of the volume fade transition in milliseconds. */
        private const val TRANSITION_DURATION_MS: Long = 500

        /** Interval between volume updates during transition (~60 fps). */
        private const val FRAME_INTERVAL_MS: Long = 16

        /** Minimum linear gain (≈ -24 dB). */
        private const val GAIN_MIN: Float = 0.06f

        /** Maximum linear gain (≈ +24 dB). */
        private const val GAIN_MAX: Float = 16.0f

        /** Gain ratio below which adjustment is skipped as imperceptible. */
        private const val GAIN_EPSILON: Float = 0.01f
    }
}
