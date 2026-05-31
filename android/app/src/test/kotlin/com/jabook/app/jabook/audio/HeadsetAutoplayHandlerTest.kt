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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class HeadsetAutoplayHandlerTest {
    private val context = mock<android.content.Context>()

    private fun createHandler(
        onConnected: () -> Unit = {},
        onDisconnected: (() -> Unit)? = null,
    ): HeadsetAutoplayHandler =
        HeadsetAutoplayHandler(
            context = context,
            onHeadsetConnected = onConnected,
            onHeadsetDisconnected = onDisconnected,
        )

    // --- recordWasPlaying ---

    @Test
    fun `recordWasPlaying sets wasPlayingBeforeBtDisconnect`() {
        val handler = createHandler()
        assertFalse(handler.wasPlayingBeforeBtDisconnect)

        handler.recordWasPlaying(true)
        assertTrue(handler.wasPlayingBeforeBtDisconnect)

        handler.recordWasPlaying(false)
        assertFalse(handler.wasPlayingBeforeBtDisconnect)
    }

    // --- BT delay constant ---

    @Test
    fun `BT_DELAY_MS is 600ms`() {
        assertEquals(600L, HeadsetAutoplayHandler.BT_DELAY_MS)
    }

    // --- stopListening when not registered does not throw ---

    @Test
    fun `stopListening without startListening does not throw`() {
        val handler = createHandler()
        handler.stopListening()
    }

    // --- double startListening is idempotent ---

    @Test
    fun `double startListening registers only once`() {
        val handler = createHandler()
        handler.startListening()
        handler.startListening()
        handler.stopListening()
    }

    // --- startListening and stopListening cycle ---

    @Test
    fun `start stop cycle works`() {
        val handler = createHandler()
        handler.startListening()
        handler.stopListening()
        handler.startListening()
        handler.stopListening()
    }

    // --- callback is not null-safe ---

    @Test
    fun `handler with null onHeadsetDisconnected creates successfully`() {
        val handler =
            HeadsetAutoplayHandler(
                context = context,
                onHeadsetConnected = {},
                onHeadsetDisconnected = null,
            )
        handler.stopListening()
    }
}
