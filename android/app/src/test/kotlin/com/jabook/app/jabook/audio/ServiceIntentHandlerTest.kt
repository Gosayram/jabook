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

import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jabook.app.jabook.widget.PlayerWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class ServiceIntentHandlerTest {
    private lateinit var service: AudioPlayerService
    private lateinit var player: ExoPlayer
    private lateinit var handler: ServiceIntentHandler

    private var nowMs: Long = 1_000L

    @Before
    fun setUp() {
        ShadowLog.clear()
        service = mock()
        player = mock()

        whenever(service.getActivePlayer()).thenReturn(player)
        whenever(player.isPlaying).thenReturn(false)
        whenever(service.packageName).thenReturn("com.jabook.app.jabook")

        handler =
            ServiceIntentHandler(
                service = service,
                widgetActionDeduplicator =
                    WidgetActionDeduplicator(
                        nowMsProvider = { nowMs },
                        dedupeWindowsMs = mapOf(PlayerWidgetProvider.ACTION_PLAY_PAUSE to 200L),
                    ),
            )
    }

    @Test
    fun `duplicate widget play pause action is handled once inside dedupe window`() {
        val intent =
            Intent(PlayerWidgetProvider.ACTION_PLAY_PAUSE).apply {
                putExtra(PlayerWidgetProvider.EXTRA_APP_WIDGET_ID, 11)
            }

        handler.handleStartCommand(intent, flags = 0, startId = 1)
        nowMs += 50L
        handler.handleStartCommand(intent, flags = 0, startId = 2)

        verify(service, times(1)).play()
    }

    @Test
    fun `same widget action from different widget ids is not deduplicated`() {
        val firstIntent =
            Intent(PlayerWidgetProvider.ACTION_PLAY_PAUSE).apply {
                putExtra(PlayerWidgetProvider.EXTRA_APP_WIDGET_ID, 21)
            }
        val secondIntent =
            Intent(PlayerWidgetProvider.ACTION_PLAY_PAUSE).apply {
                putExtra(PlayerWidgetProvider.EXTRA_APP_WIDGET_ID, 22)
            }

        handler.handleStartCommand(firstIntent, flags = 0, startId = 1)
        nowMs += 50L
        handler.handleStartCommand(secondIntent, flags = 0, startId = 2)

        verify(service, times(2)).play()
    }

    @Test
    fun `accepted widget action emits accepted then update telemetry`() {
        val intent =
            Intent(PlayerWidgetProvider.ACTION_NEXT).apply {
                putExtra(PlayerWidgetProvider.EXTRA_APP_WIDGET_ID, 31)
            }

        handler.handleStartCommand(intent, flags = 0, startId = 10)

        val telemetryMessages =
            ShadowLog
                .getLogsForTag("AudioPlayerService")
                .map { it.msg }
                .filter { it.contains("widget_service_event=") }

        val acceptedIndex = telemetryMessages.indexOfFirst { it.contains("widget_service_event=action_accepted") }
        val updateIndex = telemetryMessages.indexOfFirst { it.contains("widget_service_event=request_update_sent") }

        assertTrue(acceptedIndex >= 0)
        assertTrue(updateIndex > acceptedIndex)
        assertTrue(telemetryMessages[acceptedIndex].contains("widgetId=31"))
        assertTrue(telemetryMessages[updateIndex].contains("widgetId=31"))

        verify(service, times(1)).next()
        val updateBroadcast = argumentCaptor<Intent>()
        verify(service, times(1)).sendBroadcast(updateBroadcast.capture())
        assertEquals(PlayerWidgetProvider.ACTION_UPDATE_WIDGET, updateBroadcast.firstValue.action)
    }

    @Test
    fun `deduplicated widget action logs only deduplicated telemetry without update request`() {
        val intent =
            Intent(PlayerWidgetProvider.ACTION_PLAY_PAUSE).apply {
                putExtra(PlayerWidgetProvider.EXTRA_APP_WIDGET_ID, 41)
            }

        handler.handleStartCommand(intent, flags = 0, startId = 20)
        ShadowLog.clear()
        nowMs += 50L
        handler.handleStartCommand(intent, flags = 0, startId = 21)

        val allLogs =
            ShadowLog
                .getLogsForTag("AudioPlayerService")
                .map { it.msg }
        val telemetryMessages = allLogs.filter { it.contains("widget_service_event=") }

        assertEquals(1, allLogs.size)
        assertEquals(1, telemetryMessages.size)
        assertTrue(telemetryMessages.single().contains("widget_service_event=action_deduplicated"))
        assertTrue(telemetryMessages.single().contains("widgetId=41"))
        assertTrue(telemetryMessages.single().contains("deduplicated=true"))

        verify(service, times(1)).play()
    }

    @Test
    fun `exit app action performs foreground stop and service shutdown when initialized`() {
        val mediaSession: MediaSession = mock()
        whenever(service.isFullyInitializedFlag).thenReturn(true)
        whenever(service.mediaSession).thenReturn(mediaSession)

        handler.handleStartCommand(Intent(AudioPlayerService.ACTION_EXIT_APP), flags = 0, startId = 30)

        verify(service, times(1)).stopAndCleanup()
        verify(service, times(1)).stopSelf()
        verify(service, times(1)).sendBroadcast(
            org.mockito.kotlin.check {
                assertEquals("com.jabook.app.jabook.EXIT_APP", it.action)
                assertEquals("com.jabook.app.jabook", it.`package`)
            },
        )
    }

    @Test
    fun `exit app action is ignored when service is not initialized`() {
        whenever(service.isFullyInitializedFlag).thenReturn(false)
        whenever(service.mediaSession).thenReturn(null)
        whenever(service.mediaLibrarySession).thenReturn(null)

        handler.handleStartCommand(Intent(AudioPlayerService.ACTION_EXIT_APP), flags = 0, startId = 31)

        verify(service, never()).stopAndCleanup()
        verify(service, never()).stopSelf()
    }
}
