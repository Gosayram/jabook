// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.

package com.jabook.app.jabook.compose.data.local.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDuplicateDetectionPolicyTest {
    @Test
    fun `areLikelyDuplicate returns true for same title and same path`() {
        val existing =
            BookDuplicateDetectionPolicy.Candidate(
                title = "Dune",
                author = "Frank Herbert",
                durationMs = 100_000L,
                sourcePath = "/books/dune.m4b",
            )
        val incoming =
            BookDuplicateDetectionPolicy.Candidate(
                title = "  dune  ",
                author = "frank   herbert",
                durationMs = 120_000L,
                sourcePath = "/books/dune.m4b",
            )

        assertTrue(BookDuplicateDetectionPolicy.areLikelyDuplicate(existing, incoming))
    }

    @Test
    fun `areLikelyDuplicate returns false for different normalized title`() {
        val existing = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 100_000L, null)
        val incoming = BookDuplicateDetectionPolicy.Candidate("Foundation", "Frank Herbert", 100_000L, null)

        assertFalse(BookDuplicateDetectionPolicy.areLikelyDuplicate(existing, incoming))
    }

    @Test
    fun `areLikelyDuplicate returns false when authors differ and both known`() {
        val existing = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 100_000L, null)
        val incoming = BookDuplicateDetectionPolicy.Candidate("Dune", "Isaac Asimov", 100_000L, null)

        assertFalse(BookDuplicateDetectionPolicy.areLikelyDuplicate(existing, incoming))
    }

    @Test
    fun `areLikelyDuplicate returns true when duration delta within tolerance`() {
        val existing = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 100_000L, null)
        val incoming = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 102_000L, null)

        assertTrue(BookDuplicateDetectionPolicy.areLikelyDuplicate(existing, incoming))
    }

    @Test
    fun `areLikelyDuplicate returns false when duration delta exceeds tolerance`() {
        val existing = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 100_000L, null)
        val incoming = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 110_000L, null)

        assertFalse(BookDuplicateDetectionPolicy.areLikelyDuplicate(existing, incoming))
    }

    @Test
    fun `findDuplicate returns first matching candidate`() {
        val existing =
            listOf(
                BookDuplicateDetectionPolicy.Candidate("Foundation", "Isaac Asimov", 90_000L, null),
                BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 100_000L, null),
            )
        val incoming = BookDuplicateDetectionPolicy.Candidate("dune", "frank herbert", 101_000L, null)

        val duplicate = BookDuplicateDetectionPolicy.findDuplicate(existing, incoming)
        assertNotNull(duplicate)
        assertEquals("Dune", duplicate?.title)
    }

    @Test
    fun `findDuplicate returns null when no matches`() {
        val existing =
            listOf(
                BookDuplicateDetectionPolicy.Candidate("Foundation", "Isaac Asimov", 90_000L, null),
                BookDuplicateDetectionPolicy.Candidate("Hyperion", "Dan Simmons", 100_000L, null),
            )
        val incoming = BookDuplicateDetectionPolicy.Candidate("Dune", "Frank Herbert", 100_000L, null)

        assertNull(BookDuplicateDetectionPolicy.findDuplicate(existing, incoming))
    }
}
