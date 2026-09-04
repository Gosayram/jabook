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

package com.jabook.app.jabook.crash

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.jabook.app.jabook.util.LogUtils

/**
 * Posts a liveness token to the main thread. Abstracted so the watchdog can be
 * unit-tested on the JVM without a real Android Looper.
 */
public fun interface MainThreadPoster {
    /** Posts [token] to run on the main thread. Returns `false` if posting failed. */
    public fun post(token: Runnable): Boolean
}

/**
 * Debug-build watchdog that detects main-thread stalls (ANRs) and dumps the
 * main-thread stack trace to logcat. Diagnostics only — it never crashes the
 * app and never reports to crash analytics.
 *
 * Each cycle posts a token to the main thread and waits [timeoutMs]; if the
 * token is not consumed the main thread is considered blocked. A stall is
 * logged once per episode (repeated logging would spam logcat during one long
 * freeze); the episode resets as soon as a token is consumed again.
 *
 * A [gracePeriodMs] startup grace period suppresses false positives while the
 * app cold-starts (the main thread is legitimately busy then).
 *
 * @param timeoutMs Main-thread block duration before a stall is reported.
 * @param gracePeriodMs Time after [start] during which cycles are skipped.
 * @param poster Posts the liveness token; defaults to the real main Handler.
 * @param mainThreadStackTrace Captures the main-thread stack for the report;
 *   injectable for JVM tests (default touches `android.os.Looper`).
 */
public class AnrWatchdog(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
    private val poster: MainThreadPoster = HandlerMainThreadPoster,
    private val mainThreadStackTrace: () -> String = ::defaultMainThreadStackTrace,
) {
    private var watchdogThread: Thread? = null

    @Volatile
    private var running = false

    private var startedAtMs = 0L
    private var tokenConsumed = false
    private var stallReported = false

    /** Starts the watchdog loop. No-op if already running. */
    public fun start() {
        if (running) return
        running = true
        startedAtMs = SystemClock.elapsedRealtime()
        stallReported = false
        watchdogThread =
            Thread({ loop() }, THREAD_NAME).apply {
                // Daemon: watchdog must never block process shutdown.
                isDaemon = true
                start()
            }
    }

    /** Stops the watchdog. No-op if not running. Idempotent. */
    public fun stop() {
        if (!running) return
        running = false
        watchdogThread?.interrupt()
        watchdogThread = null
    }

    /** Returns whether the watchdog is currently active. */
    public fun isRunning(): Boolean = running

    /**
     * Runs a single detection cycle synchronously and returns whether a stall
     * was detected (and logged). Exposed for manual triggering and tests;
     * the background loop calls this on every cycle after the grace period.
     */
    public fun checkOnce(): Boolean {
        tokenConsumed = false
        if (!poster.post { tokenConsumed = true }) return false
        Thread.sleep(timeoutMs)
        return if (tokenConsumed) {
            stallReported = false
            false
        } else if (stallReported) {
            // Same episode — already logged, don't spam logcat.
            false
        } else {
            stallReported = true
            LogUtils.e(TAG, "ANR detected: main thread blocked >=${timeoutMs}ms\n${mainThreadStackTrace()}")
            true
        }
    }

    private fun loop() {
        LogUtils.d(TAG) { "ANR watchdog started (timeout=${timeoutMs}ms, grace=${gracePeriodMs}ms)" }
        while (running) {
            try {
                // Skip cycles during cold start — main thread is legitimately busy.
                if (SystemClock.elapsedRealtime() - startedAtMs < gracePeriodMs) {
                    Thread.sleep(GRACE_POLL_MS)
                    continue
                }
                checkOnce()
            } catch (_: InterruptedException) {
                break // stop() interrupts us
            } catch (e: Exception) {
                // Diagnostics must never take the app down — log and die quietly.
                LogUtils.e(TAG, "ANR watchdog crashed, stopping", e)
                break
            }
        }
        LogUtils.d(TAG, "ANR watchdog stopped")
    }

    private companion object {
        private const val TAG = "AnrWatchdog"
        private const val THREAD_NAME = "AnrWatchdog"
        private const val DEFAULT_TIMEOUT_MS = 5_000L
        private const val DEFAULT_GRACE_PERIOD_MS = 10_000L
        private const val GRACE_POLL_MS = 1_000L
    }
}

/** Real poster backed by the main-thread [Handler]. */
private object HandlerMainThreadPoster : MainThreadPoster {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(token: Runnable): Boolean = handler.post(token)
}

/** Default stack capture: the thread owned by the main Looper. */
private fun defaultMainThreadStackTrace(): String =
    Looper
        .getMainLooper()
        .thread.stackTrace
        .joinToString("\n") { "    at $it" }
