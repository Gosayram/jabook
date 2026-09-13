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

package com.jabook.app.jabook.compose.data.remote.parser

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Proof that the re2j migration holds: crafted ~50k-char adversarial inputs
 * (nested-quantifier / lazy-scan bait) against patterns that run on raw
 * RuTracker HTML text must complete in linear time (< 2s each), never hang.
 */
class ReDoSGuardTest {
    private val loggerFactory: LoggerFactory = mock()

    init {
        whenever(loggerFactory.get(any<String>())).thenReturn(NoOpLogger)
    }

    private inline fun <T> timeMs(block: () -> T): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `adversarial html text does not trigger regex backtracking`() {
        val digits = "9".repeat(50_000)
        val spaces = " ".repeat(50_000)
        val extractor = DefensiveFieldExtractor(loggerFactory)
        // Bait for SIZE_REGEX `(\d+\.?\d*\s*[KMGT]B)` (nested digit quantifiers)
        // and SEEDERS/LEECHERS fallbacks (`[:\s]*` + digits, never satisfied).
        val row = Jsoup.parse("<table><tr><td>$digits Сиды:$spaces Личи:$spaces</td></tr></table>").selectFirst("tr")!!

        val sizeMs = timeMs { extractor.extractSize(row, "t") }
        val seedMs = timeMs { extractor.extractSeeders(row, "t") }
        val leechMs = timeMs { extractor.extractLeechers(row, "t") }

        // Bait for MediaInfoParser RESOLUTION_DIRECT `(\d+)\s*[xх×]\s*(\d+)`
        // and extractField lazy-then-line-scan patterns.
        val mediaMs = timeMs { MediaInfoParser(loggerFactory).parse("Video\n$digits\nFormat :$spaces") }

        assertTrue("extractSize took ${sizeMs}ms — ReDoS regression", sizeMs < 2_000)
        assertTrue("extractSeeders took ${seedMs}ms — ReDoS regression", seedMs < 2_000)
        assertTrue("extractLeechers took ${leechMs}ms — ReDoS regression", leechMs < 2_000)
        assertTrue("MediaInfoParser.parse took ${mediaMs}ms — ReDoS regression", mediaMs < 2_000)
    }
}
