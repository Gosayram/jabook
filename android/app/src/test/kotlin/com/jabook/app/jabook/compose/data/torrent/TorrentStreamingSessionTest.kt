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

package com.jabook.app.jabook.compose.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TorrentStreamingSession] contract and [TorrentStreamingSessionAdapter].
 *
 * Uses a lightweight [FakeTorrentStreamingSession] to verify contract semantics
 * without requiring the native libtorrent4j library.
 */
public class TorrentStreamingSessionTest {
    /** Fake implementation for testing contract behavior. */
    private class FakeTorrentStreamingSession : TorrentStreamingSession {
        val sequentialRanges = mutableMapOf<String, TorrentStreamingSession.PieceRange?>()
        val deadlines = mutableMapOf<String, MutableList<Pair<Int, Int>>>()
        val clearedDeadlines = mutableSetOf<String>()
        val filePriorities = mutableMapOf<String, List<Int>>()
        val pieces = mutableMapOf<Pair<String, Int>, Boolean>()
        val pieceData = mutableMapOf<Pair<String, Int>, ByteArray>()
        val filePieceRanges = mutableMapOf<Pair<String, Int>, TorrentStreamingSession.PieceRange>()
        val readyFiles = mutableSetOf<Pair<String, Int>>()
        val downloadedBytes = mutableMapOf<Pair<String, Int>, Long>()
        var sequentialDownloadEnabled = mutableMapOf<String, Boolean>()

        override fun setSequentialDownload(
            hash: String,
            enabled: Boolean,
        ) {
            sequentialDownloadEnabled[hash] = enabled
        }

        override fun setSequentialRange(
            hash: String,
            range: TorrentStreamingSession.PieceRange?,
        ) {
            sequentialRanges[hash] = range
        }

        override fun setFilePriorities(
            hash: String,
            priorities: List<Int>,
        ) {
            filePriorities[hash] = priorities
        }

        override fun setPieceDeadline(
            hash: String,
            pieceIndex: Int,
            deadlineMs: Int,
        ) {
            deadlines.getOrPut(hash) { mutableListOf() }.add(pieceIndex to deadlineMs)
        }

        override fun clearPieceDeadlines(hash: String) {
            deadlines.remove(hash)
            clearedDeadlines.add(hash)
        }

        override fun havePiece(
            hash: String,
            pieceIndex: Int,
        ): Boolean = pieces[hash to pieceIndex] ?: false

        override fun readPiece(
            hash: String,
            pieceIndex: Int,
        ): ByteArray = pieceData[hash to pieceIndex] ?: ByteArray(0)

        override fun getFilePieceRange(
            hash: String,
            fileIndex: Int,
        ): TorrentStreamingSession.PieceRange? = filePieceRanges[hash to fileIndex]

        override fun isFileReadyForStreaming(
            hash: String,
            fileIndex: Int,
        ): Boolean = (hash to fileIndex) in readyFiles

        override fun getDownloadedBytes(
            hash: String,
            fileIndex: Int,
        ): Long = downloadedBytes[hash to fileIndex] ?: 0L
    }

    private lateinit var fake: FakeTorrentStreamingSession

    @Before
    public fun setUp() {
        fake = FakeTorrentStreamingSession()
    }

    // ========================================
    // PieceRange data class tests
    // ========================================

    @Test
    public fun pieceRange_totalPieces_isCorrect() {
        val range = TorrentStreamingSession.PieceRange(firstPiece = 5, lastPiece = 10)
        assertEquals(6, range.totalPieces)
    }

    @Test
    public fun pieceRange_singlePiece_hasOneTotal() {
        val range = TorrentStreamingSession.PieceRange(firstPiece = 3, lastPiece = 3)
        assertEquals(1, range.totalPieces)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun pieceRange_negativeFirst_throws() {
        TorrentStreamingSession.PieceRange(firstPiece = -1, lastPiece = 5)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun pieceRange_lastBeforeFirst_throws() {
        TorrentStreamingSession.PieceRange(firstPiece = 10, lastPiece = 5)
    }

    // ========================================
    // Sequential download tests
    // ========================================

    @Test
    public fun setSequentialDownload_enablesForHash() {
        fake.setSequentialDownload("abc123", true)
        assertTrue(fake.sequentialDownloadEnabled["abc123"]!!)
    }

    @Test
    public fun setSequentialDownload_disablesForHash() {
        fake.setSequentialDownload("abc123", false)
        assertFalse(fake.sequentialDownloadEnabled["abc123"]!!)
    }

    // ========================================
    // Sequential range tests
    // ========================================

    @Test
    public fun setSequentialRange_setsRangeForHash() {
        val range = TorrentStreamingSession.PieceRange(firstPiece = 0, lastPiece = 99)
        fake.setSequentialRange("abc123", range)
        assertEquals(range, fake.sequentialRanges["abc123"])
    }

    @Test
    public fun resetSequentialRange_clearsRange() {
        fake.setSequentialRange("abc123", TorrentStreamingSession.PieceRange(0, 99))
        fake.resetSequentialRange("abc123")
        assertNull(fake.sequentialRanges["abc123"])
    }

    @Test
    public fun setSequentialRange_nullResets() {
        fake.setSequentialRange("abc123", TorrentStreamingSession.PieceRange(0, 50))
        fake.setSequentialRange("abc123", null)
        assertNull(fake.sequentialRanges["abc123"])
    }

    // ========================================
    // File priorities tests
    // ========================================

    @Test
    public fun setFilePriorities_storesPriorities() {
        val priorities = listOf(0, 7, 0, 4)
        fake.setFilePriorities("abc123", priorities)
        assertEquals(priorities, fake.filePriorities["abc123"])
    }

    // ========================================
    // Piece deadline tests
    // ========================================

    @Test
    public fun setPieceDeadline_addsDeadline() {
        fake.setPieceDeadline("abc123", 5, 1000)
        fake.setPieceDeadline("abc123", 6, 2000)
        assertEquals(2, fake.deadlines["abc123"]!!.size)
        assertEquals(5 to 1000, fake.deadlines["abc123"]!![0])
        assertEquals(6 to 2000, fake.deadlines["abc123"]!![1])
    }

    @Test
    public fun clearPieceDeadlines_removesAllDeadlines() {
        fake.setPieceDeadline("abc123", 5, 1000)
        fake.setPieceDeadline("abc123", 6, 2000)
        fake.clearPieceDeadlines("abc123")
        assertTrue("Hash should be in clearedDeadlines", "abc123" in fake.clearedDeadlines)
        assertNull("Deadlines should be removed", fake.deadlines["abc123"])
    }

    // ========================================
    // havePiece / readPiece tests
    // ========================================

    @Test
    public fun havePiece_returnsFalseWhenNotAvailable() {
        assertFalse(fake.havePiece("abc123", 42))
    }

    @Test
    public fun havePiece_returnsTrueWhenAvailable() {
        fake.pieces["abc123" to 42] = true
        assertTrue(fake.havePiece("abc123", 42))
    }

    @Test
    public fun readPiece_returnsEmptyWhenNotAvailable() {
        assertTrue(fake.readPiece("abc123", 42).isEmpty())
    }

    @Test
    public fun readPiece_returnsDataWhenAvailable() {
        val data = byteArrayOf(1, 2, 3, 4)
        fake.pieceData["abc123" to 0] = data
        val result = fake.readPiece("abc123", 0)
        assertEquals(4, result.size)
        assertArrayEquals(data, result)
    }

    // ========================================
    // getFilePieceRange tests
    // ========================================

    @Test
    public fun getFilePieceRange_returnsNullForUnknownFile() {
        assertNull(fake.getFilePieceRange("abc123", 0))
    }

    @Test
    public fun getFilePieceRange_returnsRangeForKnownFile() {
        val range = TorrentStreamingSession.PieceRange(firstPiece = 10, lastPiece = 20)
        fake.filePieceRanges["abc123" to 2] = range
        val result = fake.getFilePieceRange("abc123", 2)
        assertNotNull(result)
        assertEquals(10, result!!.firstPiece)
        assertEquals(20, result.lastPiece)
    }

    // ========================================
    // isFileReadyForStreaming tests
    // ========================================

    @Test
    public fun isFileReadyForStreaming_returnsFalseByDefault() {
        assertFalse(fake.isFileReadyForStreaming("abc123", 0))
    }

    @Test
    public fun isFileReadyForStreaming_returnsTrueWhenReady() {
        fake.readyFiles.add("abc123" to 3)
        assertTrue(fake.isFileReadyForStreaming("abc123", 3))
    }

    // ========================================
    // getDownloadedBytes tests
    // ========================================

    @Test
    public fun getDownloadedBytes_returnsZeroByDefault() {
        assertEquals(0L, fake.getDownloadedBytes("abc123", 0))
    }

    @Test
    public fun getDownloadedBytes_returnsStoredValue() {
        fake.downloadedBytes["abc123" to 1] = 1024L
        assertEquals(1024L, fake.getDownloadedBytes("abc123", 1))
    }

    // ========================================
    // Contract invariants
    // ========================================

    @Test
    public fun unknownHash_allOperationsReturnDefaults() {
        assertFalse(fake.havePiece("unknown", 0))
        assertTrue(fake.readPiece("unknown", 0).isEmpty())
        assertNull(fake.getFilePieceRange("unknown", 0))
        assertFalse(fake.isFileReadyForStreaming("unknown", 0))
        assertEquals(0L, fake.getDownloadedBytes("unknown", 0))
    }

    public companion object {
        /** Helper to avoid Kotlin/JUnit ambiguity with array equality. */
        private fun assertArrayEquals(
            expected: ByteArray,
            actual: ByteArray,
        ) {
            org.junit.Assert.assertArrayEquals(expected, actual)
        }
    }
}
