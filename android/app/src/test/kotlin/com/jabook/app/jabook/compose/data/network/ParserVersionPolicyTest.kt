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

package com.jabook.app.jabook.compose.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserVersionPolicyTest {
    @Test
    fun `checkBreakage flags possible breakage for non blank query and non empty html with zero results`() {
        val result =
            ParserVersionPolicy.checkBreakage(
                parserName = "search",
                parserVersion = ParserVersionPolicy.SEARCH_PARSER_VERSION,
                resultCount = 0,
                query = "dune",
                responseHtmlLength = 128,
            )

        assertTrue(result.isPossibleBreakage)
        assertTrue(ParserVersionPolicy.formatBreakageLog(result).contains("PARSER_BREAKAGE"))
    }

    @Test
    fun `checkBreakage does not flag blank query`() {
        val result =
            ParserVersionPolicy.checkBreakage(
                parserName = "search",
                parserVersion = ParserVersionPolicy.SEARCH_PARSER_VERSION,
                resultCount = 0,
                query = " ",
                responseHtmlLength = 128,
            )

        assertFalse(result.isPossibleBreakage)
    }

    @Test
    fun `checkBreakage does not flag empty html as parser issue`() {
        val result =
            ParserVersionPolicy.checkBreakage(
                parserName = "search",
                parserVersion = ParserVersionPolicy.SEARCH_PARSER_VERSION,
                resultCount = 0,
                query = "dune",
                responseHtmlLength = 0,
            )

        assertFalse(result.isPossibleBreakage)
    }

    @Test
    fun `checkBreakage treats positive result count as healthy`() {
        val result =
            ParserVersionPolicy.checkBreakage(
                parserName = "search",
                parserVersion = ParserVersionPolicy.SEARCH_PARSER_VERSION,
                resultCount = 10,
                query = "dune",
                responseHtmlLength = 128,
            )

        assertFalse(result.isPossibleBreakage)
        assertTrue(ParserVersionPolicy.formatBreakageLog(result).contains("PARSER_OK"))
    }
}
