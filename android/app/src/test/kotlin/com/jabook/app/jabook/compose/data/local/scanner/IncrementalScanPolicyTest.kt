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

package com.jabook.app.jabook.compose.data.local.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [IncrementalScanPolicy].
 *
 * Verifies that the incremental scan filter correctly identifies changed files,
 * falls back to full scan when no timestamp is available, and respects the grace window.
 */
public class IncrementalScanPolicyTest {
    private lateinit var policy: IncrementalScanPolicy

    @Before
    public fun setUp() {
        policy = IncrementalScanPolicy()
    }

    // region filterChangedFiles — Full scan fallback

    @Test
    public fun `null timestamp triggers full scan`() {
        val files =
            listOf(
                makeFileScanInfo("a.mp3", lastModified = 100L),
                makeFileScanInfo("b.mp3", lastModified = 200L),
            )

        val result = policy.filterChangedFiles(files, lastScanTimestampMs = null)

        assertTrue(result.isFullScan)
        assertEquals(2, result.filesToScan.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    public fun `zero timestamp triggers full scan`() {
        val files = listOf(makeFileScanInfo("a.mp3", lastModified = 1000L))

        val result = policy.filterChangedFiles(files, lastScanTimestampMs = 0L)

        assertTrue(result.isFullScan)
        assertEquals(1, result.filesToScan.size)
    }

    @Test
    public fun `negative timestamp triggers full scan`() {
        val files = listOf(makeFileScanInfo("a.mp3", lastModified = 1000L))

        val result = policy.filterChangedFiles(files, lastScanTimestampMs = -1L)

        assertTrue(result.isFullScan)
    }

    // endregion

    // region filterChangedFiles — Incremental filtering

    @Test
    public fun `unchanged files are skipped`() {
        val scanTime = 10_000L

        val files =
            listOf(
                // Both files older than scan time -> both skipped
                makeFileScanInfo("old1.mp3", lastModified = 5_000L),
                makeFileScanInfo("old2.mp3", lastModified = 8_000L),
            )

        val result = policy.filterChangedFiles(files, lastScanTimestampMs = scanTime, graceWindowMs = 0L)

        assertFalse(result.isFullScan)
        assertEquals(0, result.filesToScan.size)
        assertEquals(2, result.skippedCount)
    }

    @Test
    public fun `newer files are included`() {
        val scanTime = 10_000L

        val files =
            listOf(
                makeFileScanInfo("old.mp3", lastModified = 5_000L),
                makeFileScanInfo("new.mp3", lastModified = 15_000L),
            )

        val result = policy.filterChangedFiles(files, lastScanTimestampMs = scanTime, graceWindowMs = 0L)

        assertFalse(result.isFullScan)
        assertEquals(1, result.filesToScan.size)
        assertEquals("new.mp3", result.filesToScan.first().displayName)
        assertEquals(1, result.skippedCount)
    }

    @Test
    public fun `file exactly at scan timestamp is included`() {
        val scanTime = 10_000L

        val files =
            listOf(
                makeFileScanInfo("exact.mp3", lastModified = 10_000L),
            )

        val result = policy.filterChangedFiles(files, lastScanTimestampMs = scanTime, graceWindowMs = 0L)

        assertEquals(1, result.filesToScan.size)
        assertEquals(0, result.skippedCount)
    }

    // endregion

    // region Grace window

    @Test
    public fun `grace window includes files slightly older than scan time`() {
        val scanTime = 10_000L
        val graceWindow = 2_000L

        // File modified 1 second before scan (within 2s grace window)
        val files =
            listOf(
                makeFileScanInfo("borderline.mp3", lastModified = 9_000L),
            )

        val result =
            policy.filterChangedFiles(
                files,
                lastScanTimestampMs = scanTime,
                graceWindowMs = graceWindow,
            )

        assertEquals(1, result.filesToScan.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    public fun `grace window excludes files well outside the window`() {
        val scanTime = 10_000L
        val graceWindow = 2_000L

        // File modified 5 seconds before scan (well outside 2s grace window)
        val files =
            listOf(
                makeFileScanInfo("old.mp3", lastModified = 5_000L),
            )

        val result =
            policy.filterChangedFiles(
                files,
                lastScanTimestampMs = scanTime,
                graceWindowMs = graceWindow,
            )

        assertEquals(0, result.filesToScan.size)
        assertEquals(1, result.skippedCount)
    }

    @Test
    public fun `file exactly at grace window boundary is included`() {
        val scanTime = 10_000L
        val graceWindow = 2_000L

        // Effective cutoff = 10_000 - 2_000 = 8_000; file at 8_000 >= 8_000 → included
        val files =
            listOf(
                makeFileScanInfo("boundary.mp3", lastModified = 8_000L),
            )

        val result =
            policy.filterChangedFiles(
                files,
                lastScanTimestampMs = scanTime,
                graceWindowMs = graceWindow,
            )

        assertEquals(1, result.filesToScan.size)
    }

    // endregion

    // region Mixed scenarios

    @Test
    public fun `mixed old and new files with grace window`() {
        val scanTime = 10_000L
        val graceWindow = 2_000L

        val files =
            listOf(
                makeFileScanInfo("old.mp3", lastModified = 3_000L), // skipped (3k < 8k cutoff)
                makeFileScanInfo("borderline.mp3", lastModified = 9_000L), // included (9k >= 8k)
                makeFileScanInfo("new.mp3", lastModified = 12_000L), // included (12k >= 8k)
                makeFileScanInfo("ancient.mp3", lastModified = 1_000L), // skipped (1k < 8k)
            )

        val result =
            policy.filterChangedFiles(
                files,
                lastScanTimestampMs = scanTime,
                graceWindowMs = graceWindow,
            )

        assertEquals(2, result.filesToScan.size)
        assertEquals(2, result.skippedCount)
        assertFalse(result.isFullScan)
    }

    @Test
    public fun `empty file list returns empty result`() {
        val result = policy.filterChangedFiles(emptyList(), lastScanTimestampMs = 10_000L)

        assertFalse(result.isFullScan)
        assertEquals(0, result.filesToScan.size)
        assertEquals(0, result.skippedCount)
    }

    // endregion

    // region groupChangedDirectories

    @Test
    public fun `groupChangedDirectories groups by directory`() {
        val files =
            listOf(
                makeFileScanInfo("a.mp3", directory = "/books/book1"),
                makeFileScanInfo("b.mp3", directory = "/books/book1"),
                makeFileScanInfo("c.mp3", directory = "/books/book2"),
            )

        val filterResult =
            IncrementalScanPolicy.FilterResult(
                filesToScan = files,
                skippedCount = 0,
                isFullScan = false,
            )

        val grouped = policy.groupChangedDirectories(filterResult)

        assertEquals(2, grouped.size)
        assertEquals(2, grouped["/books/book1"]?.size)
        assertEquals(1, grouped["/books/book2"]?.size)
    }

    @Test
    public fun `groupChangedDirectories with empty result returns empty map`() {
        val filterResult =
            IncrementalScanPolicy.FilterResult(
                filesToScan = emptyList(),
                skippedCount = 0,
                isFullScan = false,
            )

        val grouped = policy.groupChangedDirectories(filterResult)

        assertTrue(grouped.isEmpty())
    }

    // endregion

    // region Default constants

    @Test
    public fun `default grace window is 2000ms`() {
        assertEquals(2_000L, IncrementalScanPolicy.DEFAULT_GRACE_WINDOW_MS)
    }

    // endregion

    // region Helpers

    private fun makeFileScanInfo(
        displayName: String,
        directory: String = "/books/test",
        lastModified: Long = 0L,
        size: Long = 1024L,
    ): IncrementalScanPolicy.FileScanInfo =
        IncrementalScanPolicy.FileScanInfo(
            filePath = "$directory/$displayName",
            displayName = displayName,
            directory = directory,
            size = size,
            lastModified = lastModified,
        )

    // endregion
}
