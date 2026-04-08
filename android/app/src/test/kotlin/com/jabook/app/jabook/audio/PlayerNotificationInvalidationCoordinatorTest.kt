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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerNotificationInvalidationCoordinatorTest {
    @Test
    fun `debounced metadata then immediate playback update produces single invalidate`() =
        runTest {
            val events = mutableListOf<String>()
            val logs = mutableListOf<String>()
            val coordinator =
                PlayerNotificationInvalidationCoordinator(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 100L),
                    log = logs::add,
                    invalidate = { events += "invalidate" },
                )

            coordinator.onDebouncedSignal("onMediaMetadataChanged")
            testScheduler.advanceTimeBy(50L)
            coordinator.onImmediateSignal("onIsPlayingChanged: true")
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), events)
            assertTrue(logs.any { it.contains("onIsPlayingChanged: true: canceled pending debounced invalidate") })
            assertTrue(logs.any { it.contains("onIsPlayingChanged: true: invalidating immediately") })

            testScheduler.advanceTimeBy(200L)
            testScheduler.runCurrent()
            assertEquals(listOf("invalidate"), events)
        }

    @Test
    fun `burst debounced signals are coalesced into one invalidate`() =
        runTest {
            val events = mutableListOf<String>()
            val logs = mutableListOf<String>()
            val coordinator =
                PlayerNotificationInvalidationCoordinator(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 100L),
                    log = logs::add,
                    invalidate = { events += "invalidate" },
                )

            coordinator.onDebouncedSignal("onMediaMetadataChanged")
            coordinator.onDebouncedSignal("onMediaItemTransition")

            testScheduler.runCurrent()
            assertTrue(logs.any { it.contains("onMediaItemTransition: debounce coalesced") })

            testScheduler.advanceTimeBy(100L)
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), events)
            assertTrue(logs.any { it.contains("onMediaMetadataChanged: invalidating notification (debounced)") })
        }

    @Test
    fun `immediate signal without pending debounced still invalidates once`() =
        runTest {
            val events = mutableListOf<String>()
            val logs = mutableListOf<String>()
            val coordinator =
                PlayerNotificationInvalidationCoordinator(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 100L),
                    log = logs::add,
                    invalidate = { events += "invalidate" },
                )

            coordinator.onImmediateSignal("force_initial_state")
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), events)
            assertTrue(logs.any { it.contains("force_initial_state: invalidating immediately") })
        }

    @Test
    fun `ready immediate signal cancels pending metadata debounce and keeps single invalidate`() =
        runTest {
            val events = mutableListOf<String>()
            val logs = mutableListOf<String>()
            val coordinator =
                PlayerNotificationInvalidationCoordinator(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 120L),
                    log = logs::add,
                    invalidate = { events += "invalidate" },
                )

            coordinator.onDebouncedSignal("onMediaMetadataChanged")
            testScheduler.advanceTimeBy(60L)
            coordinator.onImmediateSignal("onPlaybackStateChanged: READY")
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), events)
            assertTrue(logs.any { it.contains("onPlaybackStateChanged: READY: canceled pending debounced invalidate") })
            assertTrue(logs.any { it.contains("onPlaybackStateChanged: READY: invalidating immediately") })

            testScheduler.advanceTimeBy(300L)
            testScheduler.runCurrent()
            assertEquals(listOf("invalidate"), events)
            assertFalse(logs.any { it.contains("onMediaMetadataChanged: invalidating notification (debounced)") })
        }

    @Test
    fun `ready immediate keeps clear log ordering in burst metadata and transition scenario`() =
        runTest {
            val events = mutableListOf<String>()
            val logs = mutableListOf<String>()
            val coordinator =
                PlayerNotificationInvalidationCoordinator(
                    scope = backgroundScope,
                    policy = PlayerNotificationInvalidatePolicy(debounceDelayMs = 150L),
                    log = logs::add,
                    invalidate = { events += "invalidate" },
                )

            coordinator.onDebouncedSignal("onMediaMetadataChanged")
            coordinator.onDebouncedSignal("onMediaItemTransition")
            testScheduler.advanceTimeBy(40L)
            coordinator.onImmediateSignal("onPlaybackStateChanged: READY")
            testScheduler.runCurrent()

            assertEquals(listOf("invalidate"), events)
            val coalescedIndex = logs.indexOfFirst { it.contains("onMediaItemTransition: debounce coalesced") }
            val canceledIndex =
                logs.indexOfFirst {
                    it.contains("onPlaybackStateChanged: READY: canceled pending debounced invalidate")
                }
            val immediateIndex =
                logs.indexOfFirst {
                    it.contains(
                        "onPlaybackStateChanged: READY: invalidating immediately",
                    )
                }
            assertTrue(coalescedIndex >= 0)
            assertTrue(canceledIndex > coalescedIndex)
            assertTrue(immediateIndex > canceledIndex)
        }
}
