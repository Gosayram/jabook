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

import java.io.RandomAccessFile

/**
 * Embedded chapter within a single M4B/MP4 file.
 *
 * @property startMs Start offset in milliseconds (from file beginning)
 * @property title Chapter title from the chpl atom
 */
public data class M4bChapter(
    val startMs: Long,
    val title: String,
)

/**
 * Pure-Kotlin parser for Nero chpl (chapter) atoms embedded in M4B/MP4 files.
 *
 * Walks the MP4 box tree: moov → udta → chpl.  Supports both 32-bit and
 * 64-bit box sizes.  Detects the optional 8-byte reserved field between
 * entry_count and the first entry by sanity-checking timestamps.
 */
public object M4bChapterParser {
    private const val MAX_CHAPTERS = 4096

    /**
     * Parses embedded Nero chapter markers from an MP4/M4B file.
     *
     * @return List of [M4bChapter] sorted by start time, or null when
     *         the file has no chapters / is malformed / is not MP4.
     */
    public fun parseM4bChapters(filePath: String): List<M4bChapter>? {
        return try {
            RandomAccessFile(filePath, "r").use { raf ->
                if (!hasFtypBox(raf)) return null

                val chplOffset = findChplBox(raf) ?: return null
                parseChplBox(raf, chplOffset)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── ftyp validation ────────────────────────────────────────────────

    private fun hasFtypBox(raf: RandomAccessFile): Boolean {
        if (raf.length() < 8) return false
        raf.seek(0)
        val size = readUint32(raf)
        if (size < 8) return false
        return readBoxType(raf) == "ftyp"
    }

    // ── box tree walking ───────────────────────────────────────────────

    private fun findChplBox(raf: RandomAccessFile): Long? {
        raf.seek(0)
        val moovOffset = findBox(raf, 0, raf.length(), "moov") ?: return null
        val moovSize = readBoxSizeAt(raf, moovOffset)
        val moovDataEnd = moovOffset + moovSize

        val udtaOffset = findBox(raf, dataStart(moovOffset, moovSize), moovDataEnd, "udta") ?: return null
        val udtaSize = readBoxSizeAt(raf, udtaOffset)

        return findBox(raf, dataStart(udtaOffset, udtaSize), udtaOffset + udtaSize, "chpl")
    }

    private fun findBox(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        type: String,
    ): Long? {
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            val size = readUint32(raf)
            if (size == 0L) return null // extends to EOF — not the box we want
            if (size < 8) return null
            val boxType = readBoxType(raf)
            if (boxType == type) return pos
            pos += size
        }
        return null
    }

    // ── chpl parsing ───────────────────────────────────────────────────

    private fun parseChplBox(
        raf: RandomAccessFile,
        chplOffset: Long,
    ): List<M4bChapter>? {
        val size = readBoxSizeAt(raf, chplOffset)
        val dataEnd = chplOffset + size
        var pos = dataStart(chplOffset, size)

        raf.seek(pos)
        val version = raf.read()
        if (version != 0) return null
        raf.skipBytes(3) // flags

        val entryCount = readUint32(raf).toInt()
        if (entryCount <= 0 || entryCount > MAX_CHAPTERS) return null
        pos = raf.filePointer

        // Try without reserved first (8-byte gap), then with.
        val without = tryParseEntries(raf, pos, dataEnd, entryCount)
        if (without != null) return without
        return tryParseEntries(raf, pos + 8, dataEnd, entryCount)
    }

    private fun tryParseEntries(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        expectedCount: Int,
    ): List<M4bChapter>? {
        val chapters = mutableListOf<M4bChapter>()
        var pos = start

        for (i in 0 until expectedCount) {
            if (pos + 9 > end) return null // need at least 8 ts + 1 len
            raf.seek(pos)

            val ts100ns = readUint64(raf)
            val titleLen = raf.read()
            if (titleLen < 0 || raf.filePointer + titleLen > end) return null

            val titleBytes = ByteArray(titleLen)
            raf.readFully(titleBytes)

            chapters.add(M4bChapter(startMs = ts100ns / 10_000, title = String(titleBytes, Charsets.UTF_8)))
            pos = raf.filePointer
        }

        if (chapters.size != expectedCount) return null

        // Monotonicity sanity check
        for (i in 1 until chapters.size) {
            if (chapters[i].startMs < chapters[i - 1].startMs) return null
        }

        return chapters
    }

    // ── binary helpers (big-endian) ────────────────────────────────────

    private fun readUint32(raf: RandomAccessFile): Long {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return ((buf[0].toLong() and 0xFF) shl 24) or
            ((buf[1].toLong() and 0xFF) shl 16) or
            ((buf[2].toLong() and 0xFF) shl 8) or
            (buf[3].toLong() and 0xFF)
    }

    private fun readUint64(raf: RandomAccessFile): Long {
        val buf = ByteArray(8)
        raf.readFully(buf)
        return ((buf[0].toLong() and 0xFF) shl 56) or
            ((buf[1].toLong() and 0xFF) shl 48) or
            ((buf[2].toLong() and 0xFF) shl 40) or
            ((buf[3].toLong() and 0xFF) shl 32) or
            ((buf[4].toLong() and 0xFF) shl 24) or
            ((buf[5].toLong() and 0xFF) shl 16) or
            ((buf[6].toLong() and 0xFF) shl 8) or
            (buf[7].toLong() and 0xFF)
    }

    private fun readBoxType(raf: RandomAccessFile): String {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return String(buf, Charsets.US_ASCII)
    }

    /** Reads the full box size at [offset], handling the 64-bit largesize. */
    private fun readBoxSizeAt(
        raf: RandomAccessFile,
        offset: Long,
    ): Long {
        raf.seek(offset)
        val size = readUint32(raf)
        return if (size == 1L) {
            readUint64(raf)
        } else {
            size
        }
    }

    /** Byte offset where box payload begins. */
    private fun dataStart(
        boxOffset: Long,
        size: Long,
    ): Long = boxOffset + if (size == 1L) 16 else 8
}
