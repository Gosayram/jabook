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

package com.jabook.app.jabook.compose.feature.indexing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumSelectionTest {
    private val all = listOf("574", "1036", "400", "2388")

    @Test
    fun `blank stored string means every forum checked`() {
        val checked = ForumSelection.checkedIds("", all)

        assertEquals(all.toSet(), checked)
    }

    @Test
    fun `stored string maps to checked set ignoring whitespace`() {
        val checked = ForumSelection.checkedIds(" 574 , 400 ", all)

        assertEquals(setOf("574", "400"), checked)
    }

    @Test
    fun `unchecking from implicit all materializes explicit complement`() {
        val checked = all.toSet()

        val next = ForumSelection.toggle(checked, "1036")
        val stored = ForumSelection.toStored(next, all)

        assertEquals("574,400,2388", stored)
    }

    @Test
    fun `checking every checkbox normalizes back to blank`() {
        val stored = ForumSelection.toStored(all.toSet(), all)

        assertEquals("", stored)
    }

    @Test
    fun `stored string preserves canonical forum ordering`() {
        val stored = ForumSelection.toStored(setOf("2388", "574"), all)

        assertEquals("574,2388", stored)
    }

    @Test
    fun `unchecking everything yields blank which worker maps back to all`() {
        var checked = ForumSelection.checkedIds("574", all)
        checked = ForumSelection.toggle(checked, "574")

        val stored = ForumSelection.toStored(checked, all)

        // Persistence quirk: empty = all forums. Safer than "index nothing".
        assertEquals("", stored)
        assertTrue(ForumSelection.checkedIds(stored, all).size == all.size)
    }
}
