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
import android.util.Log
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
) : AnalyticsListener {

    companion object {
        private const val TAG = "AudioUnderrunMonitor"

        /** Number of underruns within the burst window to trigger a report. */
        private const val UNDERRUN_BURST_THRESHOLD = 5

        /** Time window (ms) for burst detection. */
        private const val BURST_WINDOW_MS = 10_000L // 10 seconds

        /** Minimum interval between non-fatal crash reports to avoid spam. */
        private const val REPORT_COOLDOWN_MS = 60_000L // 1 minute
    }

    private val underrunTimestamps = mutableListOf<Long>()
    private var lastReportTime = 0L
    private var totalUnderruns = 0

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
        val now = SystemClock.elapsedRealtime()

        // Track burst detection
        underrunTimestamps.add(now)
        pruneOldTimestamps(now)

        LogUtils.d(TAG, "Audio underrun #$totalUnderruns (buffer=${bufferSize}b/${bufferSizeMs}ms, sinceLast=${elapsedSinceLastUnderrunMs}ms)")

        // Check burst threshold
        if (underrunTimestamps.size >= UNDERRUN_BURST_THRESHOLD) {
            reportBurst(now, bufferSize, bufferSizeMs)
        }

        // Set custom key for CrashDiagnostics context
        CrashDiagnostics.setCustomKey("audio_underrun_total", totalUnderruns.toLong())
        CrashDiagnostics.setCustomKey("audio_underrun_last_buffer_ms", bufferSizeMs)
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
    private fun reportBurst(now: Long, bufferSize: Int, bufferSizeMs: Long) {
        if (now - lastReportTime < REPORT_COOLDOWN_MS) {
            return // Cooldown — avoid spamming reports
        }

        lastReportTime = now
        val burstCount = underrunTimestamps.size
        LogUtils.w(TAG, "Audio underrun burst detected: $burstCount underruns in ${BURST_WINDOW_MS}ms window")

        CrashDiagnostics.reportNonFatal(
            RuntimeException(
                "Audio underrun burst: $burstCount in ${BURST_WINDOW_MS}ms " +
                    "(total=$totalUnderruns, bufferSize=${bufferSize}b/${bufferSizeMs}ms)",
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
        LogUtils.d(TAG, "Unregistered underrun monitor (totalUnderruns=$totalUnderruns)")
    }
}