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

import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for MediaButtonHandler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaButtonHandlerTest {
    private lateinit var handler: MediaButtonHandler

    @Before
    fun setup() {
        handler = MediaButtonHandler()
    }

    @Test
    fun `onMediaButtonEvent returns true for HEADSETHOOK`() {
        val result = handler.onMediaButtonEvent(KeyEvent.KEYCODE_HEADSETHOOK, {}, {}, {})
        assertTrue(result)
    }

    @Test
    fun `onMediaButtonEvent returns true for PLAY_PAUSE`() {
        val result = handler.onMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, {}, {}, {})
        assertTrue(result)
    }

    @Test
    fun `onMediaButtonEvent returns false for VOLUME_UP`() {
        val result = handler.onMediaButtonEvent(KeyEvent.KEYCODE_VOLUME_UP, {}, {}, {})
        assertFalse(result)
    }
}
