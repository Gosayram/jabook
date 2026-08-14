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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredCommandCoalescingPolicyTest {
    @Test
    fun `playback toggle replaces previous playback toggle`() {
        assertTrue(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.PLAYBACK_TOGGLE,
                incoming = DeferredCommandType.PLAYBACK_TOGGLE,
            ),
        )
    }

    @Test
    fun `seek replaces only seek`() {
        assertTrue(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SEEK,
                incoming = DeferredCommandType.SEEK,
            ),
        )
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SKIP,
                incoming = DeferredCommandType.SEEK,
            ),
        )
    }

    @Test
    fun `skip replaces only skip`() {
        assertTrue(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SKIP,
                incoming = DeferredCommandType.SKIP,
            ),
        )
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SPEED,
                incoming = DeferredCommandType.SKIP,
            ),
        )
    }

    @Test
    fun `repeat mode replaces only repeat mode`() {
        assertTrue(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.REPEAT_MODE,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SPEED,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }

    @Test
    fun `visualizer initialize does not remove existing commands`() {
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.VISUALIZER_ENABLED,
                incoming = DeferredCommandType.VISUALIZER_INITIALIZE,
            ),
        )
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.VISUALIZER_INITIALIZE,
                incoming = DeferredCommandType.VISUALIZER_INITIALIZE,
            ),
        )
    }

    @Test
    fun `repeat mode removes existing repeat mode`() {
        assertTrue(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.REPEAT_MODE,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }

    @Test
    fun `repeat mode does not remove playback toggle`() {
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.PLAYBACK_TOGGLE,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }

    @Test
    fun `repeat mode does not remove seek`() {
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SEEK,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }

    @Test
    fun `repeat mode does not remove skip`() {
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SKIP,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }

    @Test
    fun `repeat mode does not remove speed`() {
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.SPEED,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }

    @Test
    fun `repeat mode does not remove visualizer commands`() {
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.VISUALIZER_ENABLED,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
        assertFalse(
            DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                existing = DeferredCommandType.VISUALIZER_INITIALIZE,
                incoming = DeferredCommandType.REPEAT_MODE,
            ),
        )
    }
}
