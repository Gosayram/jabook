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

import org.junit.Test
import kotlin.test.assertEquals

class ContextualResumeManagerTest {
    private val analyzer =
        SpeechSegmentAnalyzer { _, positionMs, lookbackMs ->
            (positionMs - lookbackMs).coerceAtLeast(0L)
        }

    @Test
    fun `first-play with no pause returns zero duration and rewind`() {
        val now = 200000000L // Far enough in future to handle large durations
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now,
            )

        assertEquals(0L, result.pauseDurationMs)
        assertEquals(0L, result.rewindMs)
        assertEquals(false, result.shouldShowRecap)
    }

    @Test
    fun `first-play with invalid lastPausedAt returns zero duration`() {
        val now = 200000000L
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = 0L,
            )

        assertEquals(0L, result.pauseDurationMs)
    }

    @Test
    fun `short pause below thirty minutes returns smart rewind`() {
        val now = 200000000L
        val pauseDuration = 29 * 60 * 1000L // 29 minutes (< 30 min)
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now - pauseDuration,
            )

        assertEquals(pauseDuration, result.pauseDurationMs)
        // SMART mode returns 10 seconds for pauses < 30 min
        assertEquals(10_000L, result.rewindMs)
        assertEquals(false, result.shouldShowRecap)
    }

    @Test
    fun `pause between thirty minutes and one hour uses sentence branch`() {
        val now = 200000000L
        val pauseDuration = 45 * 60 * 1000L // 45 minutes
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now - pauseDuration,
            )

        assertEquals(pauseDuration, result.pauseDurationMs)
        assertEquals(false, result.shouldShowRecap)
    }

    @Test
    fun `pause at one hour boundary uses sentence boundary branch`() {
        val oneHour = 60L * 60_000L
        val now = 200000000L
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now - oneHour,
            )

        assertEquals(oneHour, result.pauseDurationMs)
        // At exactly 1 hour, it's in the middle branch (between 1h and 24h)
        assertEquals(false, result.shouldShowRecap)
    }

    @Test
    fun `pause just below one day uses sentence boundary branch`() {
        val oneDay = 24L * 60L * 60_000L
        val now = 200000000L
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now - oneDay + 1, // 1ms less than 1 day
            )

        assertEquals(oneDay - 1, result.pauseDurationMs)
        assertEquals(false, result.shouldShowRecap)
    }

    @Test
    fun `pause at one day boundary shows recap`() {
        val oneDay = 24L * 60L * 60_000L
        val now = 200000000L
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now - oneDay,
            )

        assertEquals(oneDay, result.pauseDurationMs)
        assertEquals(true, result.shouldShowRecap)
    }

    @Test
    fun `pause above one day shows recap with clamped start`() {
        val oneDayPlus = 25L * 60L * 60_000L
        val now = 200000000L
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = 5000L,
                lastPausedAtMs = now - oneDayPlus,
            )

        assertEquals(oneDayPlus, result.pauseDurationMs)
        assertEquals(true, result.shouldShowRecap)
        // recapStartMs = max(0, 5000 - 120000) = 0
        assertEquals(0L, result.recapStartMs)
    }

    @Test
    fun `recap window clamps recapStartMs correctly`() {
        val oneDay = 25L * 60L * 60_000L
        val now = 200000000L
        val positionMs = 180000L // 3 minutes
        val manager = ContextualResumeManager(analyzer) { now }

        val result =
            manager.buildResumeContext(
                bookId = "test",
                currentPositionMs = positionMs,
                lastPausedAtMs = now - oneDay,
            )

        assertEquals(oneDay, result.pauseDurationMs)
        assertEquals(true, result.shouldShowRecap)
        // recapStartMs = max(0, 180000 - 120000) = 60000
        assertEquals(60000L, result.recapStartMs)
    }
}
