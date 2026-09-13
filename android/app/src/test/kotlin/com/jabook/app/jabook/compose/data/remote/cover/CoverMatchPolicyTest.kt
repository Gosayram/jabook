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

package com.jabook.app.jabook.compose.data.remote.cover

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMatchPolicyTest {
    @Test
    fun `positive match on title and author`() {
        assertTrue(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Война и мир",
                candidateAuthors = listOf("Лев Толстой"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            ),
        )
    }

    @Test
    fun `title mismatch is rejected`() {
        assertFalse(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Анна Каренина",
                candidateAuthors = listOf("Лев Толстой"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            ),
        )
    }

    @Test
    fun `author surname match is sufficient`() {
        assertTrue(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Война и мир",
                candidateAuthors = listOf("Толстой Лев Николаевич"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            ),
        )
    }

    @Test
    fun `wrong author surname is rejected`() {
        assertFalse(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Война и мир",
                candidateAuthors = listOf("Достоевский Федор"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            ),
        )
    }

    @Test
    fun `empty candidate authors falls back to title-only check`() {
        assertTrue(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Мастер и Маргарита",
                candidateAuthors = emptyList(),
                queryTitle = "Мастер и Маргарита",
                queryAuthor = "Булгаков",
            ),
        )
    }

    @Test
    fun `punctuation and case are normalized`() {
        assertTrue(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "война и мир!",
                candidateAuthors = listOf("Толстой, Лев"),
                queryTitle = "Война и мир",
                queryAuthor = "  толстой   лев ",
            ),
        )
    }

    @Test
    fun `audiokniga suffix is stripped`() {
        assertTrue(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Война и мир аудиокнига",
                candidateAuthors = listOf("Лев Толстой"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            ),
        )
        assertTrue(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "Crime and Punishment audiobook",
                candidateAuthors = listOf("Fyodor Dostoevsky"),
                queryTitle = "Crime and Punishment",
                queryAuthor = "Fyodor Dostoevsky",
            ),
        )
    }

    @Test
    fun `short or blank titles are rejected as ambiguous`() {
        assertFalse(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = "It",
                candidateAuthors = listOf("Stephen King"),
                queryTitle = "Он",
                queryAuthor = "Кинг",
            ),
        )
        assertFalse(
            CoverMatchPolicy.isAcceptable(
                candidateTitle = null,
                candidateAuthors = listOf("Кинг"),
                queryTitle = "Мёртвая зона",
                queryAuthor = "Кинг",
            ),
        )
    }
}
