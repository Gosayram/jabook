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

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
class ExoPlayerMainThreadGuardTest {
    private lateinit var player: Player
    private lateinit var mainHandler: Handler

    @Before
    fun setUp() {
        player = mock()
        mainHandler = Handler(Looper.getMainLooper())
    }

    // --- runOnMain (on main thread) ---

    @Test
    fun `runOnMain executes immediately when already on main thread`() {
        val guard = ExoPlayerMainThreadGuard(player, mainHandler)
        whenever(player.currentPosition).thenReturn(42_000L)

        val result = guard.runOnMain { currentPosition }
        assertEquals(42_000L, result)
    }

    @Test
    fun `runOnMain returns null when player throws`() {
        val guard = ExoPlayerMainThreadGuard(player, mainHandler)
        whenever(player.currentPosition).thenThrow(RuntimeException("test"))

        val result = guard.runOnMain { currentPosition }
        assertNull(result)
    }

    // --- postToMain ---

    @Test
    fun `postToMain executes immediately on main thread`() {
        val guard = ExoPlayerMainThreadGuard(player, mainHandler)
        var executed = false

        guard.postToMain { executed = true }
        assertEquals(true, executed)
    }

    @Test
    fun `postToMain does not throw when player throws`() {
        val guard = ExoPlayerMainThreadGuard(player, mainHandler)
        whenever(player.isPlaying).thenThrow(RuntimeException("boom"))

        guard.postToMain { isPlaying }
    }

    // --- runOnMain with short timeout ---

    @Test
    fun `runOnMain with short timeout still executes on main thread`() {
        val guard = ExoPlayerMainThreadGuard(player, mainHandler)
        var executed = false
        val result =
            guard.runOnMain(timeoutMs = 0L) {
                executed = true
                "value"
            }
        assertEquals("value", result)
        assertTrue(executed)
    }

    @Test
    fun `runOnMain captures exception from posted block`() {
        val guard = ExoPlayerMainThreadGuard(player, mainHandler)
        whenever(player.duration).thenThrow(RuntimeException("fail"))

        val result = guard.runOnMain { duration }
        assertNull(result)
    }
}
