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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils

/**
 * Monitors audio track underruns via [AnalyticsListener] and reports diagnostics.
 *
 * BP-13.1: Underrun detection with burst tracking and CrashDiagnostics integration.
 * When underrun bursts exceed [UNDERRUN_BURST_THRESHOLD] within [BURST_WINDOW_MS],
 * a non-fatal crash report is filed for monitoring and debugging.
 */
internal class AudioUnderrunMonitor(
    private val player: ExoPlayer,
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
) : AnalyticsListener {
    companion object {
        private const val TAG = "AudioUnderrunMonitor"

        /** Number of underruns within the burst window to trigger a report. */
        private const val UNDERRUN_BURST_THRESHOLD = 5

        /** Time window (ms) for burst detection. */
        private const val BURST_WINDOW_MS = 10_000L // 10 seconds

        /** Minimum interval between non-fatal crash reports to avoid spam. */
        private const val REPORT_COOLDOWN_MS = 60_000L // 1 minute

        /** Buffering duration threshold to treat a rebuffer as a stall event. */
        private const val STALL_DURATION_THRESHOLD_MS = 1_500L
    }

    private val underrunTimestamps = mutableListOf<Long>()
    private var lastReportTime = 0L
    private var totalUnderruns = 0
    private var totalRebuffers = 0
    private var totalStalls = 0
    private var totalRebufferDurationMs = 0L
    private var bufferingStartedAtMs: Long? = null
    private var lastStallReportTime = 0L

    /**
     * Called by ExoPlayer when an audio underrun occurs on the AudioTrack.
     *
     * @param eventTime Timing information for the event.
     * @param bufferSize The buffer size of the AudioTrack in bytes.
     * @param bufferSizeMs The buffer size in milliseconds.
     * @param elapsedSinceLastUnderrunMs Time since the last underrun in milliseconds.
     */
    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastUnderrunMs: Long,
    ) {
        totalUnderruns++
        val now = nowMsProvider()

        // Track burst detection
        underrunTimestamps.add(now)
        pruneOldTimestamps(now)

        LogUtils.d(
            TAG,
            "Audio underrun #$totalUnderruns (buffer=${bufferSize}b/${bufferSizeMs}ms, sinceLast=${elapsedSinceLastUnderrunMs}ms)",
        )

        // Check burst threshold
        if (underrunTimestamps.size >= UNDERRUN_BURST_THRESHOLD) {
            reportBurst(now, bufferSize, bufferSizeMs)
        }

        // Set custom key for CrashDiagnostics context
        CrashDiagnostics.setCustomKey("audio_underrun_total", totalUnderruns.toLong())
        CrashDiagnostics.setCustomKey("audio_underrun_last_buffer_ms", bufferSizeMs)
    }

    /**
     * Tracks buffering stalls to complement underrun-only diagnostics.
     */
    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        @Player.State playbackState: Int,
    ) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                if (bufferingStartedAtMs == null) {
                    bufferingStartedAtMs = nowMsProvider()
                }
            }

            Player.STATE_READY,
            Player.STATE_ENDED,
            Player.STATE_IDLE,
            -> {
                val start = bufferingStartedAtMs ?: return
                bufferingStartedAtMs = null

                val durationMs = (nowMsProvider() - start).coerceAtLeast(0L)
                if (durationMs == 0L) return

                totalRebuffers++
                totalRebufferDurationMs += durationMs

                CrashDiagnostics.setCustomKey("audio_rebuffer_total", totalRebuffers.toLong())
                CrashDiagnostics.setCustomKey("audio_rebuffer_total_duration_ms", totalRebufferDurationMs)

                if (durationMs >= STALL_DURATION_THRESHOLD_MS) {
                    totalStalls++
                    CrashDiagnostics.setCustomKey("audio_stall_total", totalStalls.toLong())
                    maybeReportStall(
                        now = nowMsProvider(),
                        durationMs = durationMs,
                    )
                }

                LogUtils.d(
                    TAG,
                    "Audio rebuffer #$totalRebuffers duration=${durationMs}ms totalDuration=${totalRebufferDurationMs}ms",
                )
            }
        }
    }

    /**
     * Prune timestamps outside the burst window.
     */
    private fun pruneOldTimestamps(now: Long) {
        val cutoff = now - BURST_WINDOW_MS
        underrunTimestamps.removeAll { it < cutoff }
    }

    /**
     * Report an underrun burst to CrashDiagnostics for monitoring.
     */
    private fun reportBurst(
        now: Long,
        bufferSize: Int,
        bufferSizeMs: Long,
    ) {
        if (now - lastReportTime < REPORT_COOLDOWN_MS) {
            return // Cooldown — avoid spamming reports
        }

        lastReportTime = now
        val burstCount = underrunTimestamps.size
        LogUtils.w(TAG, "Audio underrun burst detected: $burstCount underruns in ${BURST_WINDOW_MS}ms window")

        CrashDiagnostics.reportNonFatal(
            TAG,
            RuntimeException(
                "Audio underrun burst: $burstCount in ${BURST_WINDOW_MS}ms " +
                    "(total=$totalUnderruns, bufferSize=${bufferSize}b/${bufferSizeMs}ms)",
            ),
        )
    }

    private fun maybeReportStall(
        now: Long,
        durationMs: Long,
    ) {
        if (now - lastStallReportTime < REPORT_COOLDOWN_MS) {
            return
        }
        lastStallReportTime = now
        LogUtils.w(TAG, "Audio stall detected: duration=${durationMs}ms, totalStalls=$totalStalls")
        CrashDiagnostics.reportNonFatal(
            TAG,
            RuntimeException(
                "Audio stall: duration=${durationMs}ms " +
                    "(totalStalls=$totalStalls, totalRebuffers=$totalRebuffers, totalRebufferDurationMs=$totalRebufferDurationMs)",
            ),
        )
    }

    /**
     * Register this monitor with the player.
     */
    fun register() {
        player.addAnalyticsListener(this)
        LogUtils.d(TAG, "Registered underrun monitor")
    }

    /**
     * Unregister this monitor from the player.
     */
    fun unregister() {
        player.removeAnalyticsListener(this)
        LogUtils.d(
            TAG,
            "Unregistered underrun monitor (totalUnderruns=$totalUnderruns, totalRebuffers=$totalRebuffers, totalRebufferDurationMs=$totalRebufferDurationMs)",
        )
    }

    internal data class MetricsSnapshot(
        val totalUnderruns: Int,
        val totalRebuffers: Int,
        val totalStalls: Int,
        val totalRebufferDurationMs: Long,
    )

    internal fun metricsSnapshotForTests(): MetricsSnapshot =
        MetricsSnapshot(
            totalUnderruns = totalUnderruns,
            totalRebuffers = totalRebuffers,
            totalStalls = totalStalls,
            totalRebufferDurationMs = totalRebufferDurationMs,
        )
}
