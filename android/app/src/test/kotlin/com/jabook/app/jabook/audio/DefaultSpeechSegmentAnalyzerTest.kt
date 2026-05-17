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

package com.jabook.app.jabook.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSpeechSegmentAnalyzerTest {
    private val analyzer = DefaultSpeechSegmentAnalyzer()

    @Test
    fun `findLastSentenceStart rewinds by lookback when enough position exists`() {
        val result =
            analyzer.findLastSentenceStart(
                bookId = "book-1",
                positionMs = 120_000L,
                lookbackMs = 30_000L,
            )

        assertEquals(90_000L, result)
    }

    @Test
    fun `findLastSentenceStart clamps to zero near track start`() {
        val result =
            analyzer.findLastSentenceStart(
                bookId = "book-2",
                positionMs = 10_000L,
                lookbackMs = 30_000L,
            )

        assertEquals(0L, result)
    }

    @Test
    fun `findLastSentenceStart with zero position returns zero`() {
        val result =
            analyzer.findLastSentenceStart(
                bookId = "book-zero-pos",
                positionMs = 0L,
                lookbackMs = 30_000L,
            )

        assertEquals(0L, result)
    }

    @Test
    fun `findLastSentenceStart with both zero returns zero`() {
        val result =
            analyzer.findLastSentenceStart(
                bookId = "book-both-zero",
                positionMs = 0L,
                lookbackMs = 0L,
            )

        assertEquals(0L, result)
    }

    @Test
    fun `findLastSentenceStart with negative positionMs clamps to zero`() {
        val result =
            analyzer.findLastSentenceStart(
                bookId = "book-neg-pos",
                positionMs = -5_000L,
                lookbackMs = 10_000L,
            )

        assertEquals(0L, result)
    }

    @Test
    fun `findLastSentenceStart with very large position still computes correctly`() {
        val result =
            analyzer.findLastSentenceStart(
                bookId = "book-large",
                positionMs = 7_200_000L,
                lookbackMs = 30_000L,
            )

        assertEquals(7_170_000L, result)
    }
}
