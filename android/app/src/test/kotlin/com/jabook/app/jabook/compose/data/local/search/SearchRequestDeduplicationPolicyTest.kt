// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.jabook.app.jabook.compose.data.local.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRequestDeduplicationPolicyTest {
    @Test
    fun `normalizeQuery trims collapses spaces and lowercases`() {
        val normalized = SearchRequestDeduplicationPolicy.normalizeQuery("   HeLLo    WoRLD  ")
        assertEquals("hello world", normalized)
    }

    @Test
    fun `check rejects blank query`() {
        val result =
            SearchRequestDeduplicationPolicy.check(
                query = "   ",
                activeRequests = emptyList(),
                currentTimeMs = 1_000L,
            )

        assertFalse(result.shouldExecute)
        assertEquals("Blank query rejected", result.reason)
        assertNull(result.cancelledQuery)
    }

    @Test
    fun `check dedups exact active query within window`() {
        val active =
            listOf(
                SearchRequestDeduplicationPolicy.ActiveRequest(
                    normalizedQuery = "dune",
                    timestampMs = 1_000L,
                    requestId = "req-1",
                ),
            )

        val result = SearchRequestDeduplicationPolicy.check("Dune", active, 1_200L)
        assertFalse(result.shouldExecute)
        assertNull(result.cancelledQuery)
    }

    @Test
    fun `check allows query and cancels stale request outside window`() {
        val active =
            listOf(
                SearchRequestDeduplicationPolicy.ActiveRequest(
                    normalizedQuery = "dune",
                    timestampMs = 1_000L,
                    requestId = "req-1",
                ),
            )

        val result = SearchRequestDeduplicationPolicy.check("dune", active, 2_000L)
        assertTrue(result.shouldExecute)
        assertEquals("req-1", result.cancelledQuery)
    }

    @Test
    fun `getRequestsToCancel returns all requests with different normalized query`() {
        val active =
            listOf(
                SearchRequestDeduplicationPolicy.ActiveRequest("dune", 1_000L, "req-1"),
                SearchRequestDeduplicationPolicy.ActiveRequest("foundation", 1_100L, "req-2"),
                SearchRequestDeduplicationPolicy.ActiveRequest("hyperion", 1_200L, "req-3"),
            )

        val toCancel = SearchRequestDeduplicationPolicy.getRequestsToCancel("Dune", active)
        assertEquals(listOf("req-2", "req-3"), toCancel)
    }

    @Test
    fun `createActiveRequest stores normalized query and metadata`() {
        val request =
            SearchRequestDeduplicationPolicy.createActiveRequest(
                query = "  Dune  Messiah  ",
                requestId = "req-42",
                currentTimeMs = 5_000L,
            )

        assertEquals("dune messiah", request.normalizedQuery)
        assertEquals("req-42", request.requestId)
        assertEquals(5_000L, request.timestampMs)
    }
}
