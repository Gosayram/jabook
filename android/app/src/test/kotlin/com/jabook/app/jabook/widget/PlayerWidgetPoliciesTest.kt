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

package com.jabook.app.jabook.widget

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerWidgetPoliciesTest {
    @Test
    fun `controller snapshot with media item does not fallback`() {
        val shouldFallback =
            WidgetControllerSnapshotPolicy.shouldFallbackToService(
                hasCurrentMediaItem = true,
                playbackState = Player.STATE_READY,
                isPlaying = true,
            )

        assertFalse(shouldFallback)
    }

    @Test
    fun `controller snapshot without media item and ready state falls back`() {
        val shouldFallback =
            WidgetControllerSnapshotPolicy.shouldFallbackToService(
                hasCurrentMediaItem = false,
                playbackState = Player.STATE_READY,
                isPlaying = false,
            )

        assertTrue(shouldFallback)
    }

    @Test
    fun `controller snapshot without media item and idle state does not fallback`() {
        val shouldFallback =
            WidgetControllerSnapshotPolicy.shouldFallbackToService(
                hasCurrentMediaItem = false,
                playbackState = Player.STATE_IDLE,
                isPlaying = false,
            )

        assertFalse(shouldFallback)
    }

    @Test
    fun `routing request code is stable for same widget and action`() {
        val first =
            WidgetActionRoutingPolicy.requestCodeForAction(
                appWidgetId = 42,
                action = PlayerWidgetProvider.ACTION_PLAY_PAUSE,
            )
        val second =
            WidgetActionRoutingPolicy.requestCodeForAction(
                appWidgetId = 42,
                action = PlayerWidgetProvider.ACTION_PLAY_PAUSE,
            )

        assertEquals(first, second)
    }

    @Test
    fun `routing request code differs between widget instances`() {
        val first =
            WidgetActionRoutingPolicy.requestCodeForAction(
                appWidgetId = 1,
                action = PlayerWidgetProvider.ACTION_PLAY_PAUSE,
            )
        val second =
            WidgetActionRoutingPolicy.requestCodeForAction(
                appWidgetId = 2,
                action = PlayerWidgetProvider.ACTION_PLAY_PAUSE,
            )

        assertNotEquals(first, second)
    }

    @Test
    fun `routing request code differs between actions for same widget`() {
        val playPause =
            WidgetActionRoutingPolicy.requestCodeForAction(
                appWidgetId = 7,
                action = PlayerWidgetProvider.ACTION_PLAY_PAUSE,
            )
        val next =
            WidgetActionRoutingPolicy.requestCodeForAction(
                appWidgetId = 7,
                action = PlayerWidgetProvider.ACTION_NEXT,
            )

        assertNotEquals(playPause, next)
    }

    @Test
    fun `deep link includes widget id and book id when provided`() {
        val uri = WidgetDeepLinkPolicy.buildPlayerDeepLink(currentBookId = "book-123", appWidgetId = 10)

        assertEquals("jabook", uri.scheme)
        assertEquals("player", uri.host)
        assertEquals("10", uri.getQueryParameter(WidgetDeepLinkPolicy.QUERY_WIDGET_ID))
        assertEquals("book-123", uri.getQueryParameter(WidgetDeepLinkPolicy.QUERY_BOOK_ID))
    }

    @Test
    fun `deep link omits book id when blank`() {
        val uri = WidgetDeepLinkPolicy.buildPlayerDeepLink(currentBookId = "   ", appWidgetId = 12)

        assertEquals("12", uri.getQueryParameter(WidgetDeepLinkPolicy.QUERY_WIDGET_ID))
        assertNull(uri.getQueryParameter(WidgetDeepLinkPolicy.QUERY_BOOK_ID))
    }

    @Test
    fun `deep link clamps negative widget id to zero`() {
        val uri = WidgetDeepLinkPolicy.buildPlayerDeepLink(currentBookId = null, appWidgetId = -9)

        assertEquals("0", uri.getQueryParameter(WidgetDeepLinkPolicy.QUERY_WIDGET_ID))
    }
}
