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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.VolumeOwner
import com.jabook.app.jabook.audio.VolumeWriteCoordinator
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
    private val getActivePlayer: () -> ExoPlayer,
    private val getChapterLufs: suspend (bookId: String, chapterIndex: Int) -> Double?,
    private val scope: CoroutineScope,
    private val volumeWriteCoordinator: VolumeWriteCoordinator,
) {
    private var transitionJob: Job? = null
    private var currentBookId: String? = null
    private var previousChapterGain: Float? = null
    private var claimedPlayer: ExoPlayer? = null

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
        releaseClaim()
    }

    /**
     * Called when a chapter transition occurs (e.g. from [onMediaItemTransition]).
     *
     * Reads the new chapter's LUFS from the database, computes the target gain,
     * and smoothly adjusts the player volume over [TRANSITION_DURATION_MS].
     *
     * Seek-initiated transitions ([Player.MEDIA_ITEM_TRANSITION_REASON_SEEK]) apply the
     * target volume instantly instead of animating: seek loops (e.g. A-B repeat) would
     * otherwise re-run the fade on every iteration, causing "breathing" volume.
     *
     * @param chapterIndex the index of the newly active chapter
     * @param transitionReason the [Player.MediaItemTransitionReason] that triggered the transition
     */
    public fun onChapterTransition(
        chapterIndex: Int,
        transitionReason: Int,
    ) {
        val bookId = currentBookId ?: return

        transitionJob?.cancel()
        transitionJob =
            scope.launch {
                val lufs = getChapterLufs(bookId, chapterIndex) ?: return@launch

                val newGain = computeGain(lufs)
                val ratio =
                    if (previousChapterGain != null) {
                        (newGain / previousChapterGain!!).coerceIn(GAIN_MIN, GAIN_MAX)
                    } else {
                        1f
                    }
                previousChapterGain = newGain

                if (abs(ratio - 1f) < GAIN_EPSILON) return@launch

                // Resolve the active player at transition start — never a stale
                // injected singleton, so the lerp targets the audible player.
                val player = getActivePlayer()
                claim(player)

                val startVolume = player.volume
                val targetVolume = (startVolume * ratio).coerceIn(0f, 1f)

                LogUtils.d(
                    TAG,
                    "Chapter transition: idx=$chapterIndex, reason=$transitionReason, lufs=$lufs, " +
                        "ratio=${"%.3f".format(ratio)}, " +
                        "volume ${"%.3f".format(startVolume)} → ${"%.3f".format(targetVolume)}",
                )

                if (transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    player.volume = targetVolume
                    releaseClaim()
                } else {
                    animateVolume(player, startVolume, targetVolume)
                }
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
        releaseClaim()
    }

    private fun claim(player: ExoPlayer) {
        claimedPlayer = player
        volumeWriteCoordinator.tryAcquire(player, VolumeOwner.CHAPTER_LOUDNESS) { transitionJob?.cancel() }
    }

    private fun releaseClaim() {
        claimedPlayer?.let { volumeWriteCoordinator.release(it, VolumeOwner.CHAPTER_LOUDNESS) }
        claimedPlayer = null
    }

    private suspend fun animateVolume(
        player: ExoPlayer,
        from: Float,
        to: Float,
    ) {
        try {
            // Progress derived from frame count instead of a wall/monotonic clock:
            // immune to clock jumps and deterministic under coroutine test schedulers.
            val totalFrames = ((TRANSITION_DURATION_MS + FRAME_INTERVAL_MS - 1) / FRAME_INTERVAL_MS).toInt()
            for (frame in 0..totalFrames) {
                val progress = (frame * FRAME_INTERVAL_MS).toFloat() / TRANSITION_DURATION_MS
                player.volume = (from + (to - from) * progress.coerceAtMost(1f)).coerceIn(0f, 1f)
                if (progress >= 1f) return
                delay(FRAME_INTERVAL_MS)
            }
        } finally {
            releaseClaim()
        }
    }

    private fun computeGain(lufs: Double): Float {
        val deltaDb = CHAPTER_TARGET_LUFS - lufs
        return 10.0.pow(deltaDb / 20.0).toFloat().coerceIn(GAIN_MIN, GAIN_MAX)
    }

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
