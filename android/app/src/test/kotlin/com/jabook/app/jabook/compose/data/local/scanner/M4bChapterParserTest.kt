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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class M4bChapterParserTest {
    // ── helpers ────────────────────────────────────────────────────────

    /** Writes [bytes] to a temp file and returns its path. */
    private fun tempFile(bytes: ByteArray): String {
        val f = File.createTempFile("m4b_test_", ".m4b")
        f.deleteOnExit()
        RandomAccessFile(f, "rw").use { it.write(bytes) }
        return f.absolutePath
    }

    private fun writeUint32BE(
        buf: ByteArray,
        offset: Int,
        value: Long,
    ) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeUint64BE(
        buf: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (i in 0..7) {
            buf[offset + i] = ((value shr (56 - 8 * i)) and 0xFF).toByte()
        }
    }

    /**
     * Builds a minimal MP4 byte stream: ftyp + moov(udta(chpl(...))).
     *
     * chpl layout: version(1) + flags(3) + entryCount(4) + [reserved(8)] + entries
     */
    private fun buildM4b(
        chapters: List<Pair<String, Long>>, // title → startMs
        includeReserved: Boolean = true,
        withValidFtyp: Boolean = true,
    ): ByteArray {
        // Build chpl payload first
        val chplEntries = mutableListOf<Byte>()
        for ((title, startMs) in chapters) {
            val titleBytes = title.toByteArray(Charsets.UTF_8)
            val ts100ns = startMs * 10_000L
            // timestamp (8 bytes)
            for (i in 0..7) {
                chplEntries.add(((ts100ns shr (56 - 8 * i)) and 0xFF).toByte())
            }
            // title length + bytes
            chplEntries.add(titleBytes.size.toByte())
            chplEntries.addAll(titleBytes.toList())
        }
        val entryCount = chapters.size
        val chplDataSize = 1 + 3 + 4 + (if (includeReserved) 8 else 0) + chplEntries.size
        val chplBoxSize = 8 + chplDataSize // header(8) + data
        val chplBox = ByteArray(chplBoxSize)
        writeUint32BE(chplBox, 0, chplBoxSize.toLong())
        chplBox[4] = 'c'.code.toByte()
        chplBox[5] = 'h'.code.toByte()
        chplBox[6] = 'p'.code.toByte()
        chplBox[7] = 'l'.code.toByte()
        chplBox[8] = 0 // version
        chplBox[9] = 0
        chplBox[10] = 0
        chplBox[11] = 0 // flags
        writeUint32BE(chplBox, 12, entryCount.toLong())
        var off = 16
        if (includeReserved) off += 8
        for (b in chplEntries) {
            chplBox[off++] = b
        }

        // udta wrapping chpl
        val udtaBoxSize = 8 + chplBoxSize
        val udtaBox = ByteArray(udtaBoxSize)
        writeUint32BE(udtaBox, 0, udtaBoxSize.toLong())
        udtaBox[4] = 'u'.code.toByte()
        udtaBox[5] = 'd'.code.toByte()
        udtaBox[6] = 't'.code.toByte()
        udtaBox[7] = 'a'.code.toByte()
        chplBox.copyInto(udtaBox, 8)

        // moov wrapping udta
        val moovBoxSize = 8 + udtaBoxSize
        val moovBox = ByteArray(moovBoxSize)
        writeUint32BE(moovBox, 0, moovBoxSize.toLong())
        moovBox[4] = 'm'.code.toByte()
        moovBox[5] = 'o'.code.toByte()
        moovBox[6] = 'o'.code.toByte()
        moovBox[7] = 'v'.code.toByte()
        udtaBox.copyInto(moovBox, 8)

        // ftyp
        val ftypBox = ByteArray(12)
        if (withValidFtyp) {
            writeUint32BE(ftypBox, 0, 12L)
            ftypBox[4] = 'f'.code.toByte()
            ftypBox[5] = 't'.code.toByte()
            ftypBox[6] = 'y'.code.toByte()
            ftypBox[7] = 'p'.code.toByte()
            ftypBox[8] = 'i'.code.toByte()
            ftypBox[9] = 's'.code.toByte()
            ftypBox[10] = 'o'.code.toByte()
            ftypBox[11] = 'm'.code.toByte()
        }

        return ftypBox + moovBox
    }

    // ── tests ──────────────────────────────────────────────────────────

    @Test
    fun `parses two chapters with reserved field`() {
        val path =
            tempFile(
                buildM4b(
                    chapters =
                        listOf(
                            "Intro" to 0L,
                            "Chapter 1" to 30_000L,
                        ),
                    includeReserved = true,
                ),
            )

        val result = M4bChapterParser.parseM4bChapters(path)

        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("Intro", result[0].title)
        assertEquals(0L, result[0].startMs)
        assertEquals("Chapter 1", result[1].title)
        assertEquals(30_000L, result[1].startMs)
    }

    @Test
    fun `parses two chapters without reserved field`() {
        val path =
            tempFile(
                buildM4b(
                    chapters =
                        listOf(
                            "Start" to 0L,
                            "Middle" to 60_000L,
                        ),
                    includeReserved = false,
                ),
            )

        val result = M4bChapterParser.parseM4bChapters(path)

        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("Start", result[0].title)
        assertEquals(0L, result[0].startMs)
        assertEquals("Middle", result[1].title)
        assertEquals(60_000L, result[1].startMs)
    }

    @Test
    fun `parses many chapters`() {
        val chapterData =
            (0..19).map { i ->
                "Chapter ${i + 1}" to (i * 120_000L) // 2 min apart
            }
        val path = tempFile(buildM4b(chapters = chapterData))

        val result = M4bChapterParser.parseM4bChapters(path)

        assertNotNull(result)
        assertEquals(20, result!!.size)
        assertEquals("Chapter 1", result[0].title)
        assertEquals(0L, result[0].startMs)
        assertEquals("Chapter 20", result[19].title)
        assertEquals(19 * 120_000L, result[19].startMs)
    }

    @Test
    fun `handles unicode chapter titles`() {
        val path =
            tempFile(
                buildM4b(
                    chapters =
                        listOf(
                            "Глава 1: Начало" to 0L,
                            "Глава 2: Продолжение" to 45_000L,
                        ),
                ),
            )

        val result = M4bChapterParser.parseM4bChapters(path)

        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertEquals("Глава 1: Начало", result[0].title)
        assertEquals("Глава 2: Продолжение", result[1].title)
    }

    @Test
    fun `returns null for non-mp4 file (no ftyp)`() {
        val path =
            tempFile(
                buildM4b(
                    chapters = listOf("Ch1" to 0L),
                    withValidFtyp = false,
                ),
            )

        val result = M4bChapterParser.parseM4bChapters(path)
        assertNull(result)
    }

    @Test
    fun `returns null for empty file`() {
        val path = tempFile(ByteArray(0))
        assertNull(M4bChapterParser.parseM4bChapters(path))
    }

    @Test
    fun `returns null for garbage bytes`() {
        val path = tempFile(ByteArray(256) { (it * 7).toByte() })
        assertNull(M4bChapterParser.parseM4bChapters(path))
    }

    @Test
    fun `returns null when chpl box is missing`() {
        // ftyp + moov with no udta/chpl
        val moovBox = ByteArray(8)
        writeUint32BE(moovBox, 0, 8L)
        moovBox[4] = 'm'.code.toByte()
        moovBox[5] = 'o'.code.toByte()
        moovBox[6] = 'o'.code.toByte()
        moovBox[7] = 'v'.code.toByte()

        val ftypBox = ByteArray(12)
        writeUint32BE(ftypBox, 0, 12L)
        ftypBox[4] = 'f'.code.toByte()
        ftypBox[5] = 't'.code.toByte()
        ftypBox[6] = 'y'.code.toByte()
        ftypBox[7] = 'p'.code.toByte()

        val path = tempFile(ftypBox + moovBox)
        assertNull(M4bChapterParser.parseM4bChapters(path))
    }

    @Test
    fun `timestamps are converted from 100ns units to ms`() {
        // 1_000_000 in 100ns units = 100ms
        val path =
            tempFile(
                buildM4b(
                    chapters =
                        listOf(
                            "A" to 0L,
                            "B" to 100L,
                        ),
                ),
            )

        val result = M4bChapterParser.parseM4bChapters(path)

        assertNotNull(result)
        assertEquals(0L, result!![0].startMs)
        assertEquals(100L, result[1].startMs)
    }

    @Test
    fun `returns null for nonexistent file`() {
        assertNull(M4bChapterParser.parseM4bChapters("/no/such/file.m4b"))
    }
}
