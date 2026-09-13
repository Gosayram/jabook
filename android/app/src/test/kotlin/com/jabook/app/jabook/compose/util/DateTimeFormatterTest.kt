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

package com.jabook.app.jabook.compose.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeFormatterTest {
    private val sampleMs = 1_700_000_000_000L // 2023-11-14T22:13:20Z

    @Test
    fun formatGOST_matchesDotSeparatedPattern() {
        val result = DateTimeFormatter.formatGOST(sampleMs)
        assertTrue("Expected dd.MM.yyyy HH:mm but was: $result", result.matches(Regex("""\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}""")))
    }

    @Test
    fun formatGOSTWithSeconds_includesSeconds() {
        val result = DateTimeFormatter.formatGOSTWithSeconds(sampleMs)
        assertTrue("Expected dd.MM.yyyy HH:mm:ss but was: $result", result.matches(Regex("""\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}""")))
        assertTrue(result.length >= 19)
    }

    @Test
    fun formatISO8601_isUtcZulu() {
        val result = DateTimeFormatter.formatISO8601(sampleMs)
        assertEquals("2023-11-14T22:13:20Z", result)
    }

    @Test
    fun formatForFilename_compact() {
        val result = DateTimeFormatter.formatForFilename(sampleMs)
        assertTrue("Expected yyyyMMdd_HHmmss but was: $result", result.matches(Regex("""\d{8}_\d{6}""")))
    }

    @Test
    fun parseISO8601_roundTrips() {
        val iso = DateTimeFormatter.formatISO8601(sampleMs)
        assertEquals(sampleMs, DateTimeFormatter.parseISO8601ToMillis(iso))
    }

    @Test
    fun parseISO8601_acceptsInstantFormat() {
        assertEquals(sampleMs, DateTimeFormatter.parseISO8601ToMillis("2023-11-14T22:13:20Z"))
    }

    @Test
    fun parseISO8601_garbageReturnsZero() {
        assertEquals(0L, DateTimeFormatter.parseISO8601ToMillis("not-a-date"))
        assertEquals(0L, DateTimeFormatter.parseISO8601ToMillis(""))
    }
}
