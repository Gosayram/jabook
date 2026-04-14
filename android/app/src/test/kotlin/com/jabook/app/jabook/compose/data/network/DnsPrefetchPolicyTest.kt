// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.jabook.app.jabook.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class DnsPrefetchPolicyTest {
    @Before
    fun setUp() {
        DnsPrefetchPolicy.resetForTests()
    }

    @Test
    fun `shouldPrefetch returns true for unseen host`() {
        assertTrue(DnsPrefetchPolicy.shouldPrefetch("rutracker.org", emptyMap(), 1000L))
    }

    @Test
    fun `shouldPrefetch returns false for blank host`() {
        assertFalse(DnsPrefetchPolicy.shouldPrefetch("  ", emptyMap(), 1000L))
    }

    @Test
    fun `shouldPrefetch returns false within cooldown`() {
        val timestamps = mapOf("rutracker.org" to 1000L)
        assertFalse(DnsPrefetchPolicy.shouldPrefetch("rutracker.org", timestamps, 30_000L))
    }

    @Test
    fun `shouldPrefetch returns true after cooldown`() {
        val timestamps = mapOf("rutracker.org" to 1000L)
        assertTrue(DnsPrefetchPolicy.shouldPrefetch("rutracker.org", timestamps, 61_000L))
    }

    @Test
    fun `pruneTimestamps adds new host`() {
        val result = DnsPrefetchPolicy.pruneTimestamps(emptyMap(), "host1", 1000L)
        assertEquals(1, result.size)
        assertEquals(1000L, result["host1"])
    }

    @Test
    fun `pruneTimestamps prunes oldest when over limit`() {
        val timestamps =
            (1..DnsPrefetchPolicy.MAX_TRACKED_HOSTS).associate {
                "host-$it" to it.toLong()
            }
        val result = DnsPrefetchPolicy.pruneTimestamps(timestamps, "extra", 999L)
        assertEquals(DnsPrefetchPolicy.MAX_TRACKED_HOSTS, result.size)
        assertFalse(result.containsKey("host-1"))
        assertTrue(result.containsKey("extra"))
    }

    @Test
    fun `extractHost parses URL and strips port`() {
        assertEquals("localhost", DnsPrefetchPolicy.extractHost("http://localhost:8080/api"))
    }

    @Test
    fun `extractHost returns null when scheme missing`() {
        assertNull(DnsPrefetchPolicy.extractHost("just-a-string"))
    }

    @Test
    fun `prefetch resolves host and returns success`() {
        val result =
            DnsPrefetchPolicy.prefetch(
                host = "rutracker.org",
                currentTimeMs = 1_000L,
                resolver = {
                    arrayOf(
                        InetAddress.getByAddress("fake-host", byteArrayOf(1, 2, 3, 4)),
                    )
                },
            )

        assertTrue(result.success)
        assertEquals(1, result.addresses.size)
        assertEquals("1.2.3.4", result.addresses.first())
    }

    @Test
    fun `prefetch enforces cooldown for repeated host`() {
        val resolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress("fake-host", byteArrayOf(1, 2, 3, 4)))
        }

        val first =
            DnsPrefetchPolicy.prefetch(
                host = "rutracker.org",
                currentTimeMs = 1_000L,
                resolver = resolver,
            )
        val second =
            DnsPrefetchPolicy.prefetch(
                host = "rutracker.org",
                currentTimeMs = 1_100L,
                resolver = resolver,
            )

        assertTrue(first.success)
        assertFalse(second.success)
        assertEquals("Prefetch skipped due to cooldown", second.error)
    }
}
