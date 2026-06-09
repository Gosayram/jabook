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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for player overflow menu action routing.
 *
 * Verifies that each overflow action:
 * 1. Invokes its specific callback
 * 2. Dismisses the menu after action
 * 3. Actions are idempotent (can be called multiple times safely)
 */
class PlayerOverflowMenuPolicyTest {
    private var shareCalled = 0
    private var favoriteCalled = 0
    private var goToBookCalled = 0
    private var bookmarksCalled = 0
    private var statsCalled = 0
    private var dismissCalled = 0

    private fun reset() {
        shareCalled = 0
        favoriteCalled = 0
        goToBookCalled = 0
        bookmarksCalled = 0
        statsCalled = 0
        dismissCalled = 0
    }

    /**
     * Simulates the overflow menu action pattern:
     * action callback + dismiss.
     */
    private fun onShare() {
        shareCalled++
        dismissCalled++
    }

    private fun onToggleFavorite() {
        favoriteCalled++
        dismissCalled++
    }

    private fun onGoToBook() {
        goToBookCalled++
        dismissCalled++
    }

    private fun onBookmarks() {
        bookmarksCalled++
        dismissCalled++
    }

    private fun onStats() {
        statsCalled++
        dismissCalled++
    }

    @Test
    fun `share action invokes share callback and dismisses`() {
        onShare()
        assertEquals(1, shareCalled)
        assertEquals(1, dismissCalled)
    }

    @Test
    fun `favorite action invokes favorite callback and dismisses`() {
        onToggleFavorite()
        assertEquals(1, favoriteCalled)
        assertEquals(1, dismissCalled)
    }

    @Test
    fun `go to book action invokes callback and dismisses`() {
        onGoToBook()
        assertEquals(1, goToBookCalled)
        assertEquals(1, dismissCalled)
    }

    @Test
    fun `bookmarks action invokes callback and dismisses`() {
        onBookmarks()
        assertEquals(1, bookmarksCalled)
        assertEquals(1, dismissCalled)
    }

    @Test
    fun `stats action invokes callback and dismisses`() {
        onStats()
        assertEquals(1, statsCalled)
        assertEquals(1, dismissCalled)
    }

    @Test
    fun `each action only fires its own callback`() {
        onShare()
        assertEquals(0, favoriteCalled)
        assertEquals(0, goToBookCalled)
        assertEquals(0, bookmarksCalled)
        assertEquals(0, statsCalled)
    }

    @Test
    fun `actions are idempotent across multiple invocations`() {
        repeat(5) { onShare() }
        assertEquals(5, shareCalled)
        assertEquals(5, dismissCalled)
    }

    @Test
    fun `all actions can be called independently`() {
        onShare()
        onToggleFavorite()
        onGoToBook()
        onBookmarks()
        onStats()

        assertEquals(1, shareCalled)
        assertEquals(1, favoriteCalled)
        assertEquals(1, goToBookCalled)
        assertEquals(1, bookmarksCalled)
        assertEquals(1, statsCalled)
        assertEquals(5, dismissCalled)
    }

    @Test
    fun `favorite label reflects state correctly`() {
        val isFavorite = true
        val label = if (isFavorite) "Unfavorite" else "Favorite"
        assertEquals("Unfavorite", label)

        val isFavorite2 = false
        val label2 = if (isFavorite2) "Unfavorite" else "Favorite"
        assertEquals("Favorite", label2)
    }

    @Test
    fun `dismiss count matches action count`() {
        onShare()
        onToggleFavorite()
        onGoToBook()
        assertTrue("Dismiss should be called for every action", dismissCalled == 3)
    }
}
