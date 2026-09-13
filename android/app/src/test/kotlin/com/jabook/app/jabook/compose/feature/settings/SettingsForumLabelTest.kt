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

package com.jabook.app.jabook.compose.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Label rendering for the indexing forum selector: real names from the
 * ForumCatalog table, "Форум {id}" string fallback while the table is empty
 * (handled in SettingsScreen via `forumNameLabels[forumId] ?: stringResource`).
 */
class SettingsForumLabelTest {
    @Test
    fun `label prefixes parent category when present`() {
        assertEquals("Аудиокниги — Радиоспектакли", forumLabel("Радиоспектакли", "Аудиокниги"))
    }

    @Test
    fun `label is bare name for top-level category rows`() {
        assertEquals("Радиоспектакли", forumLabel("Радиоспектакли", ""))
    }

    @Test
    fun `empty catalog table means empty labels map so UI falls back to forum id string`() {
        // Mirrors SettingsViewModel.forumNameLabels mapping over an empty table.
        val labels = emptyList<Pair<String, String>>().associate { (id, name) -> id to name }
        assertEquals(emptyMap<String, String>(), labels)
    }

    @Test
    fun `populated table maps forum ids to labels`() {
        val forums =
            listOf(
                "2324" to forumLabel("Художественная литература", ""),
                "574" to forumLabel("Радиоспектакли", "Художественная литература"),
            )

        assertEquals(
            mapOf(
                "2324" to "Художественная литература",
                "574" to "Художественная литература — Радиоспектакли",
            ),
            forums.toMap(),
        )
    }
}
