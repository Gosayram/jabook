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

import org.junit.Assert.assertEquals
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

    @Test
    fun `score identical title and author is near one`() {
        val score =
            CoverMatchPolicy.score(
                candidateTitle = "Война и мир",
                candidateAuthors = listOf("Лев Толстой"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            )
        assertEquals(1.0f, score, 0.01f)
    }

    @Test
    fun `score wrong author surname rejects outright`() {
        assertEquals(
            0f,
            CoverMatchPolicy.score(
                candidateTitle = "Война и мир",
                candidateAuthors = listOf("Достоевский Федор"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            ),
            0.0001f,
        )
    }

    @Test
    fun `score partial title containment with partial surname is mid range`() {
        val score =
            CoverMatchPolicy.score(
                candidateTitle = "Война и мир (том 1)",
                candidateAuthors = listOf("Толстой Лев Николаевич"),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            )
        // title 11/17 = 0.647, surname partial 0.5 -> 0.5*0.647 + 0.5*0.5
        assertEquals(0.573f, score, 0.02f)
    }

    @Test
    fun `score falls back to title weight when candidate has no authors`() {
        val score =
            CoverMatchPolicy.score(
                candidateTitle = "Война и мир",
                candidateAuthors = emptyList(),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            )
        assertEquals(0.5f, score, 0.01f)
    }

    @Test
    fun `threshold boundary accepts confident fallback and rejects weak one`() {
        // 17/23 = 0.739 title-only -> 0.37 > 0.35: acceptable
        val strong =
            CoverMatchPolicy.score(
                candidateTitle = "Мастер и Маргарита роман",
                candidateAuthors = emptyList(),
                queryTitle = "Мастер и Маргарита",
                queryAuthor = "Булгаков",
            )
        assertEquals(0.37f, strong, 0.01f)
        assertTrue(strong > 0.35f)
        assertTrue(CoverMatchPolicy.isAcceptable("Мастер и Маргарита роман", emptyList(), "Мастер и Маргарита", "Булгаков"))

        // 11/17 = 0.647 title-only -> 0.323 < 0.35: rejected
        val weak =
            CoverMatchPolicy.score(
                candidateTitle = "Война и мир том 1",
                candidateAuthors = emptyList(),
                queryTitle = "Война и мир",
                queryAuthor = "Лев Толстой",
            )
        assertTrue(weak < 0.35f)
        assertFalse(CoverMatchPolicy.isAcceptable("Война и мир том 1", emptyList(), "Война и мир", "Лев Толстой"))
    }
}
