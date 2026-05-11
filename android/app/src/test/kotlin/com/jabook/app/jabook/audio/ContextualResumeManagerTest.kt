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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualResumeManagerTest {
    private val nowMs: Long = 1_700_000_000_000L
    private val noopAnalyzer = SpeechSegmentAnalyzer { _, positionMs, _ -> positionMs }

    @Test
    fun `short pause uses smart rewind without recap`() {
        val manager = ContextualResumeManager(speechAnalyzer = noopAnalyzer, nowMsProvider = { nowMs })
        val lastPausedAtMs = nowMs - 15L * 60_000L

        val context =
            manager.buildResumeContext(
                bookId = "book-1",
                currentPositionMs = 300_000L,
                lastPausedAtMs = lastPausedAtMs,
            )

        assertFalse(context.shouldShowRecap)
        assertEquals(0L, context.recapStartMs)
        assertEquals(10_000L, context.rewindMs)
    }

    @Test
    fun `medium pause rewinds to sentence boundary`() {
        val manager =
            ContextualResumeManager(
                speechAnalyzer = SpeechSegmentAnalyzer { _, _, _ -> 250_000L },
                nowMsProvider = { nowMs },
            )
        val lastPausedAtMs = nowMs - 3L * 60L * 60_000L

        val context =
            manager.buildResumeContext(
                bookId = "book-2",
                currentPositionMs = 300_000L,
                lastPausedAtMs = lastPausedAtMs,
            )

        assertFalse(context.shouldShowRecap)
        assertEquals(0L, context.recapStartMs)
        assertEquals(50_000L, context.rewindMs)
    }

    @Test
    fun `long pause enables recap window`() {
        val manager = ContextualResumeManager(speechAnalyzer = noopAnalyzer, nowMsProvider = { nowMs })
        val lastPausedAtMs = nowMs - 48L * 60L * 60_000L

        val context =
            manager.buildResumeContext(
                bookId = "book-3",
                currentPositionMs = 90_000L,
                lastPausedAtMs = lastPausedAtMs,
            )

        assertTrue(context.shouldShowRecap)
        assertEquals(0L, context.rewindMs)
        assertEquals(0L, context.recapStartMs)
    }

    @Test
    fun `uninitialized pause timestamp disables smart resume`() {
        val manager = ContextualResumeManager(speechAnalyzer = noopAnalyzer, nowMsProvider = { nowMs })

        val context =
            manager.buildResumeContext(
                bookId = "book-init",
                currentPositionMs = 120_000L,
                lastPausedAtMs = 0L,
            )

        assertEquals(0L, context.pauseDurationMs)
        assertEquals(0L, context.rewindMs)
        assertFalse(context.shouldShowRecap)
        assertEquals(0L, context.recapStartMs)
    }

    @Test
    fun `one hour boundary moves from short rewind to sentence boundary path`() {
        val manager =
            ContextualResumeManager(
                speechAnalyzer = SpeechSegmentAnalyzer { _, _, _ -> 275_000L },
                nowMsProvider = { nowMs },
            )

        val justBelow =
            manager.buildResumeContext(
                bookId = "book-hour-below",
                currentPositionMs = 300_000L,
                lastPausedAtMs = nowMs - (60L * 60_000L - 1L),
            )
        val exactHour =
            manager.buildResumeContext(
                bookId = "book-hour-eq",
                currentPositionMs = 300_000L,
                lastPausedAtMs = nowMs - 60L * 60_000L,
            )

        assertEquals(20_000L, justBelow.rewindMs)
        assertEquals(25_000L, exactHour.rewindMs)
        assertFalse(justBelow.shouldShowRecap)
        assertFalse(exactHour.shouldShowRecap)
    }

    @Test
    fun `one day boundary enables recap flow`() {
        val manager = ContextualResumeManager(speechAnalyzer = noopAnalyzer, nowMsProvider = { nowMs })

        val justBelow =
            manager.buildResumeContext(
                bookId = "book-day-below",
                currentPositionMs = 300_000L,
                lastPausedAtMs = nowMs - (24L * 60L * 60_000L - 1L),
            )
        val exactDay =
            manager.buildResumeContext(
                bookId = "book-day-eq",
                currentPositionMs = 300_000L,
                lastPausedAtMs = nowMs - 24L * 60L * 60_000L,
            )

        assertFalse(justBelow.shouldShowRecap)
        assertTrue(exactDay.shouldShowRecap)
        assertEquals(180_000L, exactDay.recapStartMs)
    }

    @Test
    fun `sentence boundary result is clamped into valid range`() {
        val managerNegative =
            ContextualResumeManager(
                speechAnalyzer = SpeechSegmentAnalyzer { _, _, _ -> -10_000L },
                nowMsProvider = { nowMs },
            )
        val managerTooFar =
            ContextualResumeManager(
                speechAnalyzer = SpeechSegmentAnalyzer { _, _, _ -> 999_999L },
                nowMsProvider = { nowMs },
            )
        val pausedAt = nowMs - 3L * 60L * 60_000L

        val clampedToZero =
            managerNegative.buildResumeContext(
                bookId = "book-clamp-neg",
                currentPositionMs = 300_000L,
                lastPausedAtMs = pausedAt,
            )
        val clampedToCurrent =
            managerTooFar.buildResumeContext(
                bookId = "book-clamp-max",
                currentPositionMs = 300_000L,
                lastPausedAtMs = pausedAt,
            )

        assertEquals(300_000L, clampedToZero.rewindMs)
        assertEquals(0L, clampedToCurrent.rewindMs)
    }

    @Test
    fun `recap window is clamped to zero near track start`() {
        val manager = ContextualResumeManager(speechAnalyzer = noopAnalyzer, nowMsProvider = { nowMs })

        val context =
            manager.buildResumeContext(
                bookId = "book-short-pos",
                currentPositionMs = 30_000L,
                lastPausedAtMs = nowMs - 48L * 60L * 60_000L,
            )

        assertTrue(context.shouldShowRecap)
        assertEquals(0L, context.recapStartMs)
    }
}
