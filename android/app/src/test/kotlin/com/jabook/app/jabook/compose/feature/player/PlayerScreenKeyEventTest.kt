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

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

public class PlayerScreenKeyEventTest {
    @Test
    public fun `mapKeyEventToPlayerIntent maps spacebar to toggle play pause`() {
        val event = ComposeKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
        assertEquals(PlayerIntent.TogglePlayPause, mapKeyEventToPlayerIntent(event))
    }

    @Test
    public fun `mapKeyEventToPlayerIntent maps arrows with shift modifiers`() {
        val left = ComposeKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        val shiftLeft =
            ComposeKeyEvent(
                KeyEvent(
                    0L,
                    0L,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    0,
                    KeyEvent.META_SHIFT_ON,
                ),
            )
        val right = ComposeKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        val shiftRight =
            ComposeKeyEvent(
                KeyEvent(
                    0L,
                    0L,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    0,
                    KeyEvent.META_SHIFT_ON,
                ),
            )

        assertEquals(PlayerIntent.SeekBackward, mapKeyEventToPlayerIntent(left))
        assertEquals(PlayerIntent.SkipPrevious, mapKeyEventToPlayerIntent(shiftLeft))
        assertEquals(PlayerIntent.SeekForward, mapKeyEventToPlayerIntent(right))
        assertEquals(PlayerIntent.SkipNext, mapKeyEventToPlayerIntent(shiftRight))
    }

    @Test
    public fun `mapKeyEventToPlayerIntent returns null for unsupported key`() {
        val event = ComposeKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        assertNull(mapKeyEventToPlayerIntent(event))
    }
}
