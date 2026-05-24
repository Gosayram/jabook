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

class ChapterDetectionResultPolicyTest {
    private fun candidate(
        startMs: Long,
        confidence: Float,
    ) = ChapterDetectionPolicy.CandidateBoundary(startMs, confidence)

    @Test
    fun `normalizeCandidates returns empty for empty input`() {
        val result = ChapterDetectionResultPolicy.normalizeCandidates(emptyList())
        assertEquals(emptyList<ChapterDetectionPolicy.CandidateBoundary>(), result)
    }

    @Test
    fun `normalizeCandidates filters low confidence candidates`() {
        val candidates =
            listOf(
                candidate(100_000L, 0.5f),
                candidate(200_000L, 0.8f),
                candidate(300_000L, 0.3f),
            )
        val result = ChapterDetectionResultPolicy.normalizeCandidates(candidates, minConfidence = 0.75f)
        assertEquals(1, result.size)
        assertEquals(200_000L, result[0].startMs)
    }

    @Test
    fun `normalizeCandidates filters candidates starting before minChapterDurationMs`() {
        val candidates =
            listOf(
                candidate(30_000L, 0.8f),
                candidate(200_000L, 0.9f),
            )
        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                candidates,
                minChapterDurationMs = 60_000L,
            )
        assertEquals(1, result.size)
        assertEquals(200_000L, result[0].startMs)
    }

    @Test
    fun `normalizeCandidates collapses close candidates keeping higher confidence`() {
        val candidates =
            listOf(
                candidate(100_000L, 0.7f),
                candidate(120_000L, 0.9f),
                candidate(140_000L, 0.8f),
            )
        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                candidates,
                minGapMs = 60_000L,
                minChapterDurationMs = 60_000L,
            )
        assertEquals(1, result.size)
        assertEquals(120_000L, result[0].startMs)
    }

    @Test
    fun `normalizeCandidates keeps candidates spaced beyond effectiveMinGapMs`() {
        val candidates =
            listOf(
                candidate(100_000L, 0.8f),
                candidate(200_000L, 0.8f),
                candidate(300_000L, 0.8f),
            )
        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                candidates,
                minGapMs = 60_000L,
                minChapterDurationMs = 60_000L,
            )
        assertEquals(3, result.size)
    }

    @Test
    fun `normalizeCandidates effectiveMinGapMs is max of minGapMs and minChapterDurationMs`() {
        val candidates =
            listOf(
                candidate(100_000L, 0.8f),
                candidate(140_000L, 0.8f),
            )
        val resultSmallGap =
            ChapterDetectionResultPolicy.normalizeCandidates(
                candidates,
                minGapMs = 30_000L,
                minChapterDurationMs = 60_000L,
            )
        assertEquals(1, resultSmallGap.size)

        val candidatesFar =
            listOf(
                candidate(100_000L, 0.8f),
                candidate(200_000L, 0.8f),
            )
        val resultBigGap =
            ChapterDetectionResultPolicy.normalizeCandidates(
                candidatesFar,
                minGapMs = 100_000L,
                minChapterDurationMs = 60_000L,
            )
        assertEquals(2, resultBigGap.size)
    }
}
