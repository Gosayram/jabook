// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

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
