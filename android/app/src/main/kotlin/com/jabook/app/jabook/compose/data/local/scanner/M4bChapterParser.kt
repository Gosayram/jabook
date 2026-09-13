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

import java.io.File
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
 * Walks the MP4 box tree: moov → udta → chpl. Supports both 32-bit and
 * 64-bit box sizes.
 */
public object M4bChapterParser {
    private const val MAX_CHAPTERS = 4096

    /**
     * Parses embedded chapter markers from an MP4/M4B file.
     *
     * Prefers the Nero chpl atom; if absent/empty, falls back to the
     * iTunes-style chapter text track (tref 'chap') via
     * [Mp4ChapterTrackParser].
     *
     * @return List of [M4bChapter] sorted by start time, or null when
     *         the file has no chapters / is malformed / is not MP4.
     */
    public fun parseM4bChapters(filePath: String): List<M4bChapter>? {
        val chplChapters =
            try {
                RandomAccessFile(filePath, "r").use { raf ->
                    val chpl = findChplBox(raf) ?: return@use null
                    parseChplBox(raf, chpl)
                }
            } catch (_: Exception) {
                null
            }
        if (!chplChapters.isNullOrEmpty()) return chplChapters
        // Many iTunes-authored .m4b files store chapters as a hidden text
        // track referenced by tref 'chap' instead of a chpl atom.
        return Mp4ChapterTrackParser.parseChapters(File(filePath))
    }

    // ── box tree walking ───────────────────────────────────────────────

    private fun findChplBox(raf: RandomAccessFile): Box? {
        val moov = findBox(raf, 0, raf.length(), "moov") ?: return null
        val udta = findBox(raf, moov.dataStart, moov.end, "udta") ?: return null
        return findBox(raf, udta.dataStart, udta.end, "chpl")
    }

    private fun findBox(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        type: String,
    ): Box? {
        var pos = start
        while (pos < end) {
            val box = readBoxAt(raf, pos, end) ?: return null
            if (box.type == type) return box
            pos = box.end
        }
        return null
    }

    // ── chpl parsing ───────────────────────────────────────────────────

    private fun parseChplBox(
        raf: RandomAccessFile,
        chpl: Box,
    ): List<M4bChapter>? {
        if (chpl.end - chpl.dataStart < 9) return null
        raf.seek(chpl.dataStart)
        raf.skipBytes(5) // 1 byte version + 3 bytes flags + 1 byte reserved.
        val entryCount = readUint32(raf).toInt()
        if (entryCount <= 0 || entryCount > MAX_CHAPTERS) return null
        return parseEntries(raf, raf.filePointer, chpl.end, entryCount)
    }

    private fun parseEntries(
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

            val ts100ns = readInt64(raf)
            if (ts100ns < 0) return null
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
            if (chapters[i].startMs <= chapters[i - 1].startMs) return null
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

    private fun readInt64(raf: RandomAccessFile): Long {
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

    private fun readBoxAt(
        raf: RandomAccessFile,
        offset: Long,
        parentEnd: Long,
    ): Box? {
        if (
            offset < 0 ||
            offset > parentEnd ||
            parentEnd > raf.length() ||
            parentEnd - offset < 8
        ) {
            return null
        }
        raf.seek(offset)
        val declaredSize = readUint32(raf)
        val type = readBoxType(raf)
        val headerSize =
            if (declaredSize == 1L) {
                if (parentEnd - offset < 16) return null
                16L
            } else {
                8L
            }
        val size =
            when (declaredSize) {
                0L -> parentEnd - offset
                1L -> readInt64(raf)
                else -> declaredSize
            }
        if (size < headerSize || size > parentEnd - offset) return null
        return Box(type = type, dataStart = offset + headerSize, end = offset + size)
    }

    private data class Box(
        val type: String,
        val dataStart: Long,
        val end: Long,
    )
}
