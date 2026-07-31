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

package com.jabook.app.jabook.compose.data.torrent

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentMemoryPressureGuardTest {
    @Test
    fun `does not pause torrents for non-critical memory pressure`() {
        val session = RecordingTorrentSession()
        val guard = TorrentMemoryPressureGuard(session)

        val handled = guard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertFalse(handled)
        assertEquals(0, session.memoryPressurePauseCount)
    }

    @Test
    fun `pauses and persists session at running critical memory pressure`() {
        val session = RecordingTorrentSession()
        val guard = TorrentMemoryPressureGuard(session)

        val handled = guard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertTrue(handled)
        assertEquals(1, session.memoryPressurePauseCount)
    }

    @Test
    fun `pauses and persists session when app is no longer foreground`() {
        val session = RecordingTorrentSession()
        val guard = TorrentMemoryPressureGuard(session)

        val handled = guard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        assertTrue(handled)
        assertEquals(1, session.memoryPressurePauseCount)
    }

    @Test
    fun `serializes repeated critical callbacks without dropping a state save`() {
        val session = RecordingTorrentSession()
        val guard = TorrentMemoryPressureGuard(session)

        repeat(2) {
            assertTrue(guard.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        }

        assertEquals(2, session.memoryPressurePauseCount)
    }
}

private class RecordingTorrentSession : FakeTorrentSession() {
    var memoryPressurePauseCount: Int = 0
        private set

    override fun pauseForMemoryPressure() {
        memoryPressurePauseCount++
    }
}
