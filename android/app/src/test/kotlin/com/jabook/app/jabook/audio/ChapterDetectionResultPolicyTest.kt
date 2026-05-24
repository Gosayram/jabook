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
    @Test
    fun `normalizeCandidates drops low confidence candidates`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(10_000L, 0.6f),
                ChapterDetectionPolicy.CandidateBoundary(80_000L, 0.9f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minChapterDurationMs = 0L,
            )

        assertEquals(1, result.size)
        assertEquals(80_000L, result.first().startMs)
    }

    @Test
    fun `normalizeCandidates keeps stronger candidate when boundaries are too close`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.8f),
                ChapterDetectionPolicy.CandidateBoundary(120_000L, 0.95f),
                ChapterDetectionPolicy.CandidateBoundary(210_000L, 0.85f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 60_000L,
                minChapterDurationMs = 0L,
            )

        assertEquals(2, result.size)
        assertEquals(120_000L, result[0].startMs)
        assertEquals(210_000L, result[1].startMs)
    }

    @Test
    fun `normalizeCandidates drops too-early boundary by minimum chapter duration`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(40_000L, 0.95f),
                ChapterDetectionPolicy.CandidateBoundary(130_000L, 0.8f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minChapterDurationMs = 90_000L,
            )

        assertEquals(1, result.size)
        assertEquals(130_000L, result.first().startMs)
    }

    @Test
    fun `normalizeCandidates returns empty list for empty input`() {
        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(emptyList())

        assertEquals(0, result.size)
    }

    @Test
    fun `normalizeCandidates filters by custom minConfidence`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.85f),
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.80f),
                ChapterDetectionPolicy.CandidateBoundary(300_000L, 0.70f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minConfidence = 0.78f,
                minChapterDurationMs = 0L,
            )

        assertEquals(2, result.size)
        assertEquals(100_000L, result[0].startMs)
        assertEquals(200_000L, result[1].startMs)
    }

    @Test
    fun `normalizeCandidates filters candidates below default minConfidence`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.74f),
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.75f),
                ChapterDetectionPolicy.CandidateBoundary(300_000L, 0.76f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minChapterDurationMs = 0L,
            )

        assertEquals(2, result.size)
        assertEquals(200_000L, result[0].startMs)
        assertEquals(300_000L, result[1].startMs)
    }

    @Test
    fun `normalizeCandidates returns empty when all candidates below confidence`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.3f),
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.5f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minConfidence = 0.9f,
                minChapterDurationMs = 0L,
            )

        assertEquals(0, result.size)
    }

    @Test
    fun `normalizeCandidates clamps minGapMs to minChapterDurationMs when chapter duration is larger`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.9f),
                ChapterDetectionPolicy.CandidateBoundary(260_000L, 0.95f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 30_000L,
                minChapterDurationMs = 90_000L,
            )

        assertEquals(1, result.size)
        assertEquals(260_000L, result[0].startMs)
    }

    @Test
    fun `normalizeCandidates uses minGapMs directly when it exceeds minChapterDurationMs`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.9f),
                ChapterDetectionPolicy.CandidateBoundary(250_000L, 0.95f),
                ChapterDetectionPolicy.CandidateBoundary(400_000L, 0.85f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 120_000L,
                minChapterDurationMs = 90_000L,
            )

        assertEquals(2, result.size)
        assertEquals(250_000L, result[0].startMs)
        assertEquals(400_000L, result[1].startMs)
    }

    @Test
    fun `normalizeCandidates replaces lower confidence with higher when gap is smaller than effectiveMinGapMs`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.95f),
                ChapterDetectionPolicy.CandidateBoundary(140_000L, 0.80f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 60_000L,
                minChapterDurationMs = 0L,
            )

        assertEquals(1, result.size)
        assertEquals(100_000L, result[0].startMs)
        assertEquals(0.95f, result[0].confidence, 0.001f)
    }

    @Test
    fun `normalizeCandidates keeps earlier candidate when confidence is equal and gap is small`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.9f),
                ChapterDetectionPolicy.CandidateBoundary(130_000L, 0.9f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 60_000L,
                minChapterDurationMs = 0L,
            )

        assertEquals(1, result.size)
        assertEquals(100_000L, result[0].startMs)
    }

    @Test
    fun `normalizeCandidates sorts unsorted input by startMs`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(300_000L, 0.9f),
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.85f),
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.95f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 60_000L,
                minChapterDurationMs = 0L,
            )

        assertEquals(3, result.size)
        assertEquals(100_000L, result[0].startMs)
        assertEquals(200_000L, result[1].startMs)
        assertEquals(300_000L, result[2].startMs)
    }

    @Test
    fun `normalizeCandidates chains multiple collapses correctly`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.80f),
                ChapterDetectionPolicy.CandidateBoundary(120_000L, 0.85f),
                ChapterDetectionPolicy.CandidateBoundary(140_000L, 0.90f),
                ChapterDetectionPolicy.CandidateBoundary(250_000L, 0.95f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 60_000L,
                minChapterDurationMs = 0L,
            )

        assertEquals(2, result.size)
        assertEquals(140_000L, result[0].startMs)
        assertEquals(250_000L, result[1].startMs)
    }

    @Test
    fun `normalizeCandidates filters by minChapterDurationMs and then collapses`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(50_000L, 0.95f),
                ChapterDetectionPolicy.CandidateBoundary(80_000L, 0.9f),
                ChapterDetectionPolicy.CandidateBoundary(100_000L, 0.85f),
                ChapterDetectionPolicy.CandidateBoundary(250_000L, 0.9f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(
                input,
                minGapMs = 30_000L,
                minChapterDurationMs = 90_000L,
            )

        assertEquals(2, result.size)
        assertEquals(100_000L, result[0].startMs)
        assertEquals(250_000L, result[1].startMs)
    }

    @Test
    fun `normalizeCandidates with all defaults`() {
        val input =
            listOf(
                ChapterDetectionPolicy.CandidateBoundary(90_000L, 0.80f),
                ChapterDetectionPolicy.CandidateBoundary(200_000L, 0.90f),
                ChapterDetectionPolicy.CandidateBoundary(350_000L, 0.85f),
            )

        val result =
            ChapterDetectionResultPolicy.normalizeCandidates(input)

        assertEquals(3, result.size)
        assertEquals(90_000L, result[0].startMs)
        assertEquals(200_000L, result[1].startMs)
        assertEquals(350_000L, result[2].startMs)
    }
}
