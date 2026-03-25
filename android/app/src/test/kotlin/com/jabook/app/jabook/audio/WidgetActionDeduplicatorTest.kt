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

import com.jabook.app.jabook.widget.PlayerWidgetProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetActionDeduplicatorTest {
    @Test
    fun `same widget and action inside window is deduplicated`() {
        var nowMs = 1_000L
        val deduplicator =
            WidgetActionDeduplicator(
                nowMsProvider = { nowMs },
                dedupeWindowsMs = mapOf(PlayerWidgetProvider.ACTION_PLAY_PAUSE to 200L),
            )

        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_PLAY_PAUSE, widgetId = 5))
        nowMs += 100L
        assertFalse(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_PLAY_PAUSE, widgetId = 5))
    }

    @Test
    fun `same action on different widgets is handled independently`() {
        var nowMs = 2_000L
        val deduplicator =
            WidgetActionDeduplicator(
                nowMsProvider = { nowMs },
                dedupeWindowsMs = mapOf(PlayerWidgetProvider.ACTION_NEXT to 200L),
            )

        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_NEXT, widgetId = 1))
        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_NEXT, widgetId = 2))
    }

    @Test
    fun `same widget handles different actions`() {
        var nowMs = 3_000L
        val deduplicator =
            WidgetActionDeduplicator(
                nowMsProvider = { nowMs },
                dedupeWindowsMs =
                    mapOf(
                        PlayerWidgetProvider.ACTION_PLAY_PAUSE to 200L,
                        PlayerWidgetProvider.ACTION_NEXT to 200L,
                    ),
            )

        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_PLAY_PAUSE, widgetId = 3))
        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_NEXT, widgetId = 3))
    }

    @Test
    fun `same widget and action is handled again after window`() {
        var nowMs = 4_000L
        val deduplicator =
            WidgetActionDeduplicator(
                nowMsProvider = { nowMs },
                dedupeWindowsMs = mapOf(PlayerWidgetProvider.ACTION_TIMER to 200L),
            )

        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_TIMER, widgetId = 8))
        nowMs += 250L
        assertTrue(deduplicator.shouldHandle(PlayerWidgetProvider.ACTION_TIMER, widgetId = 8))
    }
}
