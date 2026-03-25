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

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerNotificationInvalidationPipelineTest {
    @Test
    fun `pipeline uses unified log tag and preserves burst invalidate behavior`() =
        runTest {
            val player = mock<Player>()
            val invalidates = mutableListOf<String>()
            val logs = mutableListOf<Pair<String, String>>()
            val pipeline =
                PlayerNotificationInvalidationPipeline(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 120L),
                    logDebug = { tag, message ->
                        logs += tag to message
                    },
                    invalidate = { invalidates += "invalidate" },
                )

            pipeline.register(player = player)

            val listenerCaptor = argumentCaptor<Player.Listener>()
            verify(player).addListener(listenerCaptor.capture())
            val listener = listenerCaptor.firstValue

            listener.onMediaMetadataChanged(MediaMetadata.Builder().build())
            listener.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            testScheduler.advanceTimeBy(40L)
            listener.onPlaybackStateChanged(Player.STATE_READY)
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), invalidates)
            assertTrue(logs.isNotEmpty())
            assertTrue(
                logs.all { (tag, _) ->
                    tag == PlayerNotificationInvalidationPipeline.LOG_TAG
                },
            )
            assertTrue(
                logs.any { (_, message) ->
                    message.contains("onMediaItemTransition: debounce coalesced")
                },
            )
            assertTrue(
                logs.any { (_, message) ->
                    message.contains("onPlaybackStateChanged: READY: canceled pending debounced invalidate")
                },
            )
            assertTrue(
                logs.any { (_, message) ->
                    message.contains("onPlaybackStateChanged: READY: invalidating immediately")
                },
            )

            testScheduler.advanceTimeBy(250L)
            testScheduler.runCurrent()
            assertEquals(listOf("invalidate"), invalidates)
        }

    @Test
    fun `force initial invalidate emits immediate signal with unified tag`() =
        runTest {
            val logs = mutableListOf<Pair<String, String>>()
            val invalidates = mutableListOf<String>()
            val pipeline =
                PlayerNotificationInvalidationPipeline(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 100L),
                    logDebug = { tag, message ->
                        logs += tag to message
                    },
                    invalidate = { invalidates += "invalidate" },
                )

            pipeline.forceInitialStateInvalidate()
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), invalidates)
            assertEquals(
                listOf(
                    PlayerNotificationInvalidationPipeline.LOG_TAG to
                        "force_initial_state: invalidating immediately",
                ),
                logs,
            )
        }
}
