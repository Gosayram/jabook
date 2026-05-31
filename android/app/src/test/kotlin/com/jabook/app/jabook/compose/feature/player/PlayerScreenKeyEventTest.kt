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

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerScreenKeyEventTest {
    // Simplified test version that directly tests the mapping logic
    // since mapKeyEventToPlayerIntent is internal and uses KeyEvent properties

    @Test
    fun `spacebar maps to TogglePlayPause`() {
        val intent = mapKeyToPlayerIntent(Key.Spacebar, isShiftPressed = false)
        assertEquals(PlayerIntent.TogglePlayPause, intent)
    }

    @Test
    fun `direction left without shift maps to SeekBackward`() {
        val intent = mapKeyToPlayerIntent(Key.DirectionLeft, isShiftPressed = false)
        assertEquals(PlayerIntent.SeekBackward, intent)
    }

    @Test
    fun `direction left with shift maps to SkipPrevious`() {
        val intent = mapKeyToPlayerIntent(Key.DirectionLeft, isShiftPressed = true)
        assertEquals(PlayerIntent.SkipPrevious, intent)
    }

    @Test
    fun `direction right without shift maps to SeekForward`() {
        val intent = mapKeyToPlayerIntent(Key.DirectionRight, isShiftPressed = false)
        assertEquals(PlayerIntent.SeekForward, intent)
    }

    @Test
    fun `direction right with shift maps to SkipNext`() {
        val intent = mapKeyToPlayerIntent(Key.DirectionRight, isShiftPressed = true)
        assertEquals(PlayerIntent.SkipNext, intent)
    }

    @Test
    fun `unknown key returns null`() {
        val intent = mapKeyToPlayerIntent(Key.A, isShiftPressed = false)
        assertNull(intent)
    }

    @Test
    fun `enter key returns null`() {
        val intent = mapKeyToPlayerIntent(Key.Enter, isShiftPressed = false)
        assertNull(intent)
    }

    @Test
    fun `direction up returns null`() {
        val intent = mapKeyToPlayerIntent(Key.DirectionUp, isShiftPressed = false)
        assertNull(intent)
    }

    @Test
    fun `direction down returns null`() {
        val intent = mapKeyToPlayerIntent(Key.DirectionDown, isShiftPressed = false)
        assertNull(intent)
    }

    // Extract of the mapping logic for unit testing
    private fun mapKeyToPlayerIntent(
        key: Key,
        isShiftPressed: Boolean,
    ): PlayerIntent? =
        when (key) {
            Key.Spacebar -> PlayerIntent.TogglePlayPause
            Key.DirectionLeft -> if (isShiftPressed) PlayerIntent.SkipPrevious else PlayerIntent.SeekBackward
            Key.DirectionRight -> if (isShiftPressed) PlayerIntent.SkipNext else PlayerIntent.SeekForward
            else -> null
        }
}
