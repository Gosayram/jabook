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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistLoadCoordinatorTest {
    @Test
    fun `beginOrSkip returns null when loading already in progress`() {
        var loading = true
        var cancelCalls = 0
        var generation = 41L

        val coordinator =
            PlaylistLoadCoordinator(
                isLoading = { loading },
                setLoading = { loading = it },
                setCurrentLoadingPlaylist = {},
                setLastLoadTimestampMs = {},
                cancelAndClearActiveLoadingJob = { cancelCalls++ },
                nextGeneration = { ++generation },
            )

        val result = coordinator.beginOrSkip(listOf("a.mp3"))

        assertNull(result)
        assertEquals(0, cancelCalls)
        assertTrue(loading)
    }

    @Test
    fun `beginOrSkip starts load and prepares generation`() {
        var loading = false
        var currentPlaylist: List<String>? = null
        var lastTimestamp = 0L
        var cancelCalls = 0
        var generation = 10L

        val coordinator =
            PlaylistLoadCoordinator(
                isLoading = { loading },
                setLoading = { loading = it },
                setCurrentLoadingPlaylist = { currentPlaylist = it },
                setLastLoadTimestampMs = { lastTimestamp = it },
                cancelAndClearActiveLoadingJob = { cancelCalls++ },
                nextGeneration = { ++generation },
            )

        val result = coordinator.beginOrSkip(listOf("a.mp3", "b.mp3"))

        assertEquals(11L, result)
        assertEquals(1, cancelCalls)
        assertTrue(loading)
        assertEquals(listOf("a.mp3", "b.mp3"), currentPlaylist)
        assertTrue(lastTimestamp > 0L)
    }

    @Test
    fun `finish and fail clear loading state`() {
        var loading = true
        var currentPlaylist: List<String>? = listOf("x.mp3")
        var cancelCalls = 0

        val coordinator =
            PlaylistLoadCoordinator(
                isLoading = { loading },
                setLoading = { loading = it },
                setCurrentLoadingPlaylist = { currentPlaylist = it },
                setLastLoadTimestampMs = {},
                cancelAndClearActiveLoadingJob = { cancelCalls++ },
                nextGeneration = { 1L },
            )

        coordinator.finish()
        assertTrue(!loading)
        assertNull(currentPlaylist)
        assertEquals(1, cancelCalls)

        loading = true
        currentPlaylist = listOf("z.mp3")
        coordinator.fail()
        assertTrue(!loading)
        assertNull(currentPlaylist)
        assertEquals(2, cancelCalls)
    }
}
