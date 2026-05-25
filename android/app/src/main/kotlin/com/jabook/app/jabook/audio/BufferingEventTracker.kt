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

import android.os.SystemClock
import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils

/**
 * Tracks buffering events and reports rebuffering statistics.
 *
 * P-46: Rebuffering (STATE_BUFFERING after STATE_READY) degrades UX. This tracker
 * monitors frequency, duration, and position of buffering events, and can trigger
 * alerts for long rebuffering periods (> 3 seconds).
 *
 * Complements [AudioUnderrunMonitor] which tracks underruns at the AudioTrack level.
 * This class operates at the Player state level.
 *
 * @param player ExoPlayer instance to monitor
 * @param onLongRebuffer Callback when a rebuffer exceeds [LONG_REBUFFER_THRESHOLD_MS]
 * @param nowMsProvider Injectable clock for testing
 */
internal class BufferingEventTracker(
    private val player: Player,
    private val onLongRebuffer: (durationMs: Long, positionMs: Long) -> Unit = { _, _ -> },
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
) : Player.Listener {
    private var bufferingStartTimeMs: Long? = null
    private var totalRebuffers = 0
    private var totalRebufferDurationMs = 0L
    private var longestRebufferMs = 0L

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                if (bufferingStartTimeMs == null) {
                    bufferingStartTimeMs = nowMsProvider()
                }
            }
            Player.STATE_READY,
            Player.STATE_ENDED,
            -> {
                val start = bufferingStartTimeMs ?: return
                bufferingStartTimeMs = null

                val durationMs = (nowMsProvider() - start).coerceAtLeast(0L)
                if (durationMs == 0L) return

                totalRebuffers++
                totalRebufferDurationMs += durationMs

                if (durationMs > longestRebufferMs) {
                    longestRebufferMs = durationMs
                }

                if (durationMs > LONG_REBUFFER_THRESHOLD_MS) {
                    val positionMs =
                        try {
                            player.currentPosition
                        } catch (_: Exception) {
                            -1L
                        }
                    LogUtils.w(TAG, "Long rebuffer: ${durationMs}ms at position=${positionMs}ms")
                    onLongRebuffer(durationMs, positionMs)
                }

                LogUtils.d(TAG, "Rebuffer #$totalRebuffers: ${durationMs}ms (total=${totalRebufferDurationMs}ms)")
            }
        }
    }

    /**
     * Registers this tracker as a listener on the player.
     */
    fun register() {
        player.addListener(this)
    }

    /**
     * Unregisters this tracker from the player.
     */
    fun unregister() {
        player.removeListener(this)
    }

    internal data class BufferingStats(
        val totalRebuffers: Int,
        val totalRebufferDurationMs: Long,
        val longestRebufferMs: Long,
    )

    internal fun statsForTest(): BufferingStats = BufferingStats(totalRebuffers, totalRebufferDurationMs, longestRebufferMs)

    companion object {
        private const val TAG = "BufferingTracker"
        internal const val LONG_REBUFFER_THRESHOLD_MS = 3_000L
    }
}
