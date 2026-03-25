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
import com.jabook.app.jabook.widget.PlayerWidgetProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceIntentHandlerTest {
    private lateinit var service: AudioPlayerService
    private lateinit var player: ExoPlayer
    private lateinit var handler: ServiceIntentHandler

    private var nowMs: Long = 1_000L

    @Before
    fun setUp() {
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
}
