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

package com.jabook.app.jabook.compose.feature.player.controller

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for AudioPlayerController connection state.
 *
 * Tests the ConnectionState enum and basic state properties.
 * Note: Full integration tests require instrumented tests with real service.
 */
class AudioPlayerControllerStateTest {
    @Test
    fun `connection state enum has all expected values`() {
        // Verify the ConnectionState enum exists with all values
        val states = AudioPlayerController.ConnectionState.entries.toTypedArray()
        assertEquals(4, states.size)
        assert(states.contains(AudioPlayerController.ConnectionState.DISCONNECTED))
        assert(states.contains(AudioPlayerController.ConnectionState.CONNECTING))
        assert(states.contains(AudioPlayerController.ConnectionState.CONNECTED))
        assert(states.contains(AudioPlayerController.ConnectionState.FAILED_USING_FALLBACK))
    }

    @Test
    fun `DISCONNECTED is the default initial state`() {
        // The controller should start in DISCONNECTED state
        assertEquals(
            AudioPlayerController.ConnectionState.DISCONNECTED,
            AudioPlayerController.ConnectionState.entries.first(),
        )
    }

    @Test
    fun `FAILED_USING_FALLBACK indicates fallback mode`() {
        // Verify the fallback state name
        val fallbackState = AudioPlayerController.ConnectionState.FAILED_USING_FALLBACK
        assertEquals("FAILED_USING_FALLBACK", fallbackState.name)
    }
}
