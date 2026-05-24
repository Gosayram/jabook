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

import com.jabook.app.jabook.util.LogUtils

/**
 * Performance logger for tracking player initialization timing.
 *
 * Helps identify bottlenecks in player startup by logging timestamps
 * for all critical events.
 *
 * Usage:
 * ```
 * PlayerPerformanceLogger.start("cold_start")
 * PlayerPerformanceLogger.log("Service", "onCreate started")
 * // ... initialization code ...
 * PlayerPerformanceLogger.log("Service", "onCreate completed")
 * PlayerPerformanceLogger.summary()
 * ```
 */
public object PlayerPerformanceLogger {
    private const val TAG = "PlayerPerf"
    private var sessionStartTime = 0L
    private var sessionName = ""
    private var enabled = true
    private val events = mutableListOf<TimingEvent>()

    public data class TimingEvent(
        val timestamp: Long,
        val elapsed: Long,
        val component: String,
        val event: String,
    )

    /**
     * Start a new performance measurement session.
     *
     * @param name Session identifier (e.g., "cold_start", "warm_start")
     */
    public fun start(name: String) {
        sessionName = name
        sessionStartTime = System.currentTimeMillis()
        events.clear()
        LogUtils.d(TAG, "========== Performance Session: $name ==========")
    }

    /**
     * Log a timing event.
     *
     * @param component Component name (e.g., "Service", "Player", "UI")
     * @param event Event description (e.g., "onCreate started")
     */
    public fun log(
        component: String,
        event: String,
    ) {
        if (!enabled) return

        val now = System.currentTimeMillis()
        val elapsed = if (sessionStartTime > 0) now - sessionStartTime else 0L

        val timingEvent =
            TimingEvent(
                timestamp = now,
                elapsed = elapsed,
                component = component,
                event = event,
            )

        events.add(timingEvent)

        LogUtils.d(TAG, "[$component +${elapsed}ms] $event")
    }

    /**
     * Log with automatic delta from previous event.
     */
    public fun logDelta(
        component: String,
        event: String,
    ) {
        val lastEvent = events.lastOrNull()
        val delta =
            if (lastEvent != null) {
                System.currentTimeMillis() - lastEvent.timestamp
            } else {
                0L
            }

        log(component, "$event (Δ${delta}ms)")
    }

    /**
     * Print summary of all events with timing analysis.
     */
    public fun summary() {
        if (events.isEmpty()) {
            LogUtils.w(TAG, "No events logged")
            return
        }

        val totalTime = events.lastOrNull()?.elapsed ?: 0L

        LogUtils.d(TAG, "")
        LogUtils.d(TAG, "========== Performance Summary: $sessionName ==========")
        LogUtils.d(TAG, "Total time: ${totalTime}ms")
        LogUtils.d(TAG, "")

        // Group by component
        val byComponent = events.groupBy { it.component }
        byComponent.forEach { (component, componentEvents) ->
            LogUtils.d(TAG, "[$component]")
            componentEvents.forEach { event ->
                LogUtils.d(TAG, "  +${event.elapsed}ms: ${event.event}")
            }

            // Component timing
            val componentStart = componentEvents.firstOrNull()?.elapsed ?: 0L
            val componentEnd = componentEvents.lastOrNull()?.elapsed ?: 0L
            val componentDuration = componentEnd - componentStart
            if (componentDuration > 0) {
                LogUtils.d(TAG, "  Duration: ${componentDuration}ms")
            }
            LogUtils.d(TAG, "")
        }

        // Find bottlenecks (gaps > 100ms)
        LogUtils.d(TAG, "⚠️ Bottlenecks (gaps > 100ms):")
        for (i in 1 until events.size) {
            val prev = events[i - 1]
            val curr = events[i]
            val gap = curr.timestamp - prev.timestamp
            if (gap > 100) {
                LogUtils.d(TAG, "  ${gap}ms gap between:")
                LogUtils.d(TAG, "    [${prev.component}] ${prev.event}")
                LogUtils.d(TAG, "    [${curr.component}] ${curr.event}")
            }
        }

        LogUtils.d(TAG, "===================================================")
    }

    /**
     * Enable/disable logging (for production builds).
     */
    public fun setEnabled() {
        this.enabled = enabled
    }

    /**
     * Get all recorded events (for testing/analysis).
     */
    public fun getEvents(): List<TimingEvent> = events.toList()

    /**
     * Clear session data.
     */
    public fun reset() {
        events.clear()
        sessionStartTime = 0L
        sessionName = ""
    }
}
