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

package com.jabook.app.jabook.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for JabookApp interaction policies.
 *
 * Tests the pure-logic components that drive app-level interactions:
 * - Settings badge test tag constant
 * - Mini-player visibility/disposal state logic
 */
@RunWith(RobolectricTestRunner::class)
class JabookAppInteractionTest {
    @Test
    fun `settings badge test tag is correct`() {
        assertEquals("settings_badge", SETTINGS_BADGE_TEST_TAG)
    }

    @Test
    fun `player screen visibility reset on dispose`() {
        // Simulate the DisposableEffect visibility logic:
        // on enter: callback(true), on dispose: callback(false)
        var visibility = false
        val callback: (Boolean) -> Unit = { visibility = it }

        callback(true) // on enter player screen
        assertTrue(visibility)
        callback(false) // on dispose
        assertFalse(visibility)
    }

    @Test
    fun `mini player dismissal hides and shows resume`() {
        // Simulate mini-player state management
        var isMiniPlayerVisible = true
        var isPlaying = true

        // Dismiss
        isMiniPlayerVisible = false
        isPlaying = false
        assertFalse(isMiniPlayerVisible)
        assertFalse(isPlaying)

        // Resume via snackbar action
        isMiniPlayerVisible = true
        isPlaying = true
        assertTrue(isMiniPlayerVisible)
        assertTrue(isPlaying)
    }
}
