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
import com.jabook.app.jabook.widget.PlayerWidgetProvider

/**
 * Deduplicates widget actions in a short window to prevent accidental double-dispatch.
 */
internal class WidgetActionDeduplicator(
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val dedupeWindowsMs: Map<String, Long> = DEFAULT_WINDOWS_MS,
) {
    private data class ActionKey(
        val action: String,
        val widgetId: Int,
    )

    private val lastHandledAtMs = LinkedHashMap<ActionKey, Long>()

    internal fun shouldHandle(
        action: String,
        widgetId: Int?,
    ): Boolean {
        if (!isWidgetAction(action)) {
            return true
        }

        val key = ActionKey(action = action, widgetId = widgetId ?: UNKNOWN_WIDGET_ID)
        val nowMs = nowMsProvider()
        val windowMs = dedupeWindowsMs[action] ?: DEFAULT_WINDOW_MS
        val lastHandledMs = lastHandledAtMs[key]

        if (lastHandledMs != null) {
            val delta = nowMs - lastHandledMs
            if (delta >= 0 && delta < windowMs) {
                return false
            }
        }

        lastHandledAtMs[key] = nowMs
        pruneIfNeeded(nowMs)
        return true
    }

    private fun pruneIfNeeded(nowMs: Long) {
        if (lastHandledAtMs.size <= MAX_TRACKED_KEYS) {
            return
        }

        val cutoffMs = nowMs - MAX_RETENTION_MS
        val iterator = lastHandledAtMs.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoffMs) {
                iterator.remove()
            }
        }

        while (lastHandledAtMs.size > MAX_TRACKED_KEYS) {
            val firstKey = lastHandledAtMs.keys.firstOrNull() ?: break
            lastHandledAtMs.remove(firstKey)
        }
    }

    internal companion object {
        internal const val UNKNOWN_WIDGET_ID: Int = -1
        internal const val DEFAULT_WINDOW_MS: Long = 250L
        private const val MAX_TRACKED_KEYS: Int = 128
        private const val MAX_RETENTION_MS: Long = 60_000L

        private val WIDGET_ACTIONS: Set<String> =
            setOf(
                PlayerWidgetProvider.ACTION_PLAY_PAUSE,
                PlayerWidgetProvider.ACTION_NEXT,
                PlayerWidgetProvider.ACTION_PREVIOUS,
                PlayerWidgetProvider.ACTION_REPEAT,
                PlayerWidgetProvider.ACTION_SPEED,
                PlayerWidgetProvider.ACTION_TIMER,
            )

        private val DEFAULT_WINDOWS_MS: Map<String, Long> =
            mapOf(
                PlayerWidgetProvider.ACTION_PLAY_PAUSE to 300L,
                PlayerWidgetProvider.ACTION_NEXT to 150L,
                PlayerWidgetProvider.ACTION_PREVIOUS to 150L,
                PlayerWidgetProvider.ACTION_REPEAT to 300L,
                PlayerWidgetProvider.ACTION_SPEED to 300L,
                PlayerWidgetProvider.ACTION_TIMER to 300L,
            )

        internal fun isWidgetAction(action: String): Boolean = action in WIDGET_ACTIONS
    }
}
