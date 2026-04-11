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

package com.jabook.app.jabook.diagnostics

import android.os.Handler
import android.os.Looper
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight ANR (Application Not Responding) watchdog for debug/beta builds.
 *
 * Periodically posts a task to the main thread and checks if it completes within
 * [thresholdMs]. If the main thread is blocked for longer than the threshold,
 * the watchdog logs the main thread stack trace as a non-fatal via [CrashDiagnostics].
 *
 * ## Usage
 *
 * ```kotlin
 * val watchdog = AnrWatchdog(thresholdMs = 5000L)
 * watchdog.start()
 * // ... later
 * watchdog.stop()
 * ```
 *
 * ## Thread safety
 *
 * All public methods are safe to call from any thread. The watchdog uses
 * [Handler] for main thread scheduling and a coroutine for the check loop.
 *
 * @param thresholdMs Block duration in milliseconds before reporting an ANR (default 5000).
 * @param scope Coroutine scope for the check loop. Defaults to a supervised scope.
 */
public class AnrWatchdog(
    private val thresholdMs: Long = DEFAULT_THRESHOLD_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false

    /**
     * Starts the ANR watchdog check loop.
     * No-op if already running.
     */
    public fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isActive) {
                checkMainThread()
                delay(CHECK_INTERVAL_MS)
            }
        }
        LogUtils.d(TAG, "ANR watchdog started (threshold=${thresholdMs}ms)")
    }

    /**
     * Stops the ANR watchdog.
     * No-op if not running.
     */
    public fun stop() {
        if (!isRunning) return
        isRunning = false
        LogUtils.d(TAG, "ANR watchdog stopped")
    }

    /**
     * Returns whether the watchdog is currently active.
     */
    public fun isRunning(): Boolean = isRunning

    private fun checkMainThread() {
        val startTime = System.currentTimeMillis()
        val completed =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        mainHandler.post {
            completed.set(true)
        }

        // Wait for the main thread to respond
        val waitUntil = startTime + thresholdMs
        while (!completed.get() && System.currentTimeMillis() < waitUntil && isRunning) {
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }

        if (!completed.get() && isRunning) {
            val blockedDuration = System.currentTimeMillis() - startTime
            val stackTrace = getMainThreadStackTrace()

            val message = "ANR detected: main thread blocked for ${blockedDuration}ms (threshold=${thresholdMs}ms)"
            LogUtils.e(TAG, message)
            LogUtils.e(TAG, "Main thread stack trace:\n$stackTrace")

            CrashDiagnostics.reportNonFatal(
                TAG,
                AnrDetectedException(message, stackTrace),
            )
        }
    }

    /**
     * Captures the current main thread stack trace.
     */
    private fun getMainThreadStackTrace(): String {
        val mainThread = Looper.getMainLooper().thread
        return mainThread.stackTrace.joinToString("\n") { "    at $it" }
    }

    private companion object {
        private const val TAG = "AnrWatchdog"
        private const val DEFAULT_THRESHOLD_MS = 5000L
        private const val CHECK_INTERVAL_MS = 1000L
        private const val SAMPLE_INTERVAL_MS = 50L
    }
}

/**
 * Exception reported when the ANR watchdog detects a main thread block.
 */
public class AnrDetectedException(
    message: String,
    private val stackTraceSummary: String,
) : Exception(message) {
    override fun toString(): String = "AnrDetectedException: $message\n$stackTraceSummary"
}
