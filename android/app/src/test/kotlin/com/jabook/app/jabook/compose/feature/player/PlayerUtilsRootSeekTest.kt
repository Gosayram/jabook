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

package com.jabook.app.jabook.compose.feature.player

import androidx.compose.ui.input.key.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.view.KeyEvent as AndroidKeyEvent

@RunWith(RobolectricTestRunner::class)
class PlayerUtilsRootSeekTest {
    private fun keyEventUp(
        keyCode: Int,
        metaState: Int = 0,
    ): KeyEvent = KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_UP, keyCode, 0, metaState))

    @Test
    fun `bare left arrow maps to rewind delta`() {
        assertEquals(
            -ROOT_SEEK_REWIND_SECONDS * 1000L,
            mapBareArrowKeyUpToSeekDeltaMs(keyEventUp(AndroidKeyEvent.KEYCODE_DPAD_LEFT)),
        )
    }

    @Test
    fun `bare right arrow maps to forward delta`() {
        assertEquals(
            ROOT_SEEK_FORWARD_SECONDS * 1000L,
            mapBareArrowKeyUpToSeekDeltaMs(keyEventUp(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)),
        )
    }

    @Test
    fun `shift left arrow returns null`() {
        assertNull(
            mapBareArrowKeyUpToSeekDeltaMs(
                keyEventUp(AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.META_SHIFT_ON),
            ),
        )
    }

    @Test
    fun `ctrl right arrow returns null`() {
        assertNull(
            mapBareArrowKeyUpToSeekDeltaMs(
                keyEventUp(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.META_CTRL_ON),
            ),
        )
    }

    @Test
    fun `alt left arrow returns null`() {
        assertNull(
            mapBareArrowKeyUpToSeekDeltaMs(
                keyEventUp(AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.META_ALT_ON),
            ),
        )
    }

    @Test
    fun `meta right arrow returns null`() {
        assertNull(
            mapBareArrowKeyUpToSeekDeltaMs(
                keyEventUp(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.META_META_ON),
            ),
        )
    }

    @Test
    fun `non arrow key returns null`() {
        assertNull(mapBareArrowKeyUpToSeekDeltaMs(keyEventUp(AndroidKeyEvent.KEYCODE_SPACE)))
    }
}
