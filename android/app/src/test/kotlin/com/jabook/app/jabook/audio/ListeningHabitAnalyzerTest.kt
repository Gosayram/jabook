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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ListeningHabitAnalyzerTest {
    private lateinit var analyzer: ListeningHabitAnalyzer

    @Before
    fun setUp() {
        analyzer = ListeningHabitAnalyzer()
    }

    // --- Not enough data returns null ---

    @Test
    fun `suggestSpeed returns null with fewer than 10 sessions`() {
        repeat(9) { i ->
            analyzer.recordSession(
                ListeningHabitAnalyzer.SessionRecord(
                    hourOfDay = 20,
                    dayOfWeek = 1,
                    outputType = "speaker",
                    playbackSpeed = 1.5f,
                ),
            )
        }
        assertNull(analyzer.suggestSpeed(context(20, "speaker")))
    }

    // --- With enough similar sessions returns suggestion ---

    @Test
    fun `suggestSpeed returns average for similar context`() {
        repeat(10) {
            analyzer.recordSession(
                ListeningHabitAnalyzer.SessionRecord(
                    hourOfDay = 22,
                    dayOfWeek = 1,
                    outputType = "headphones",
                    playbackSpeed = 2.0f,
                ),
            )
        }
        val result = analyzer.suggestSpeed(context(22, "headphones"))
        assertEquals(2.0f, result!!, 0.01f)
    }

    // --- Different output type returns null ---

    @Test
    fun `suggestSpeed returns null when no similar output type`() {
        repeat(15) {
            analyzer.recordSession(
                ListeningHabitAnalyzer.SessionRecord(
                    hourOfDay = 20,
                    dayOfWeek = 1,
                    outputType = "speaker",
                    playbackSpeed = 1.5f,
                ),
            )
        }
        assertNull(analyzer.suggestSpeed(context(20, "bluetooth")))
    }

    // --- Speed clamped to valid range ---

    @Test
    fun `suggestSpeed clamps to valid range`() {
        repeat(10) {
            analyzer.recordSession(
                ListeningHabitAnalyzer.SessionRecord(
                    hourOfDay = 20,
                    dayOfWeek = 1,
                    outputType = "speaker",
                    playbackSpeed = 10.0f,
                ),
            )
        }
        val result = analyzer.suggestSpeed(context(20, "speaker"))
        assertEquals(ListeningHabitAnalyzer.MAX_SPEED, result!!, 0.01f)
    }

    // --- Most common hour ---

    @Test
    fun `getMostCommonListeningHour returns most frequent hour`() {
        repeat(5) {
            analyzer.recordSession(
                ListeningHabitAnalyzer.SessionRecord(
                    hourOfDay = 22,
                    dayOfWeek = 1,
                    outputType = "speaker",
                    playbackSpeed = 1.5f,
                ),
            )
        }
        analyzer.recordSession(
            ListeningHabitAnalyzer.SessionRecord(
                hourOfDay = 10,
                dayOfWeek = 1,
                outputType = "speaker",
                playbackSpeed = 1.0f,
            ),
        )
        assertEquals(22, analyzer.getMostCommonListeningHour())
    }

    // --- Empty analyzer ---

    @Test
    fun `getMostCommonListeningHour returns -1 when empty`() {
        assertEquals(-1, analyzer.getMostCommonListeningHour())
    }

    // --- Session count ---

    @Test
    fun `session count tracks recorded sessions`() {
        assertEquals(0, analyzer.getSessionCount())
        analyzer.recordSession(session(20, "speaker", 1.0f))
        assertEquals(1, analyzer.getSessionCount())
    }

    // --- Clear resets ---

    @Test
    fun `clear removes all sessions`() {
        repeat(5) { analyzer.recordSession(session(20, "speaker", 1.0f)) }
        analyzer.clear()
        assertEquals(0, analyzer.getSessionCount())
    }

    // --- Max sessions evicts oldest ---

    @Test
    fun `max sessions evicts oldest`() {
        repeat(510) { i ->
            analyzer.recordSession(session(i % 24, "speaker", 1.0f))
        }
        assertEquals(500, analyzer.getSessionCount())
    }

    private fun context(
        hour: Int,
        output: String,
    ) = ListeningHabitAnalyzer.ListeningContext(hourOfDay = hour, dayOfWeek = 1, outputType = output)

    private fun session(
        hour: Int,
        output: String,
        speed: Float,
    ) = ListeningHabitAnalyzer.SessionRecord(hourOfDay = hour, dayOfWeek = 1, outputType = output, playbackSpeed = speed)
}
