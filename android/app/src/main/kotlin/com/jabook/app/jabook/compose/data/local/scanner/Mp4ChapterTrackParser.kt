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
 * Pure-Kotlin parser for iTunes-style MP4/M4B chapter tracks: a hidden text
 * track referenced via a tref 'chap' box, used when the Nero chpl atom is
 * absent (common in iTunes-authored .m4b audiobooks).
 *
 * Walks the box tree with [RandomAccessFile] (same defensive style as
 * [M4bChapterParser]): moov → trak → {tkhd, tref(chap), mdia → mdhd,
 * minf → stbl → stts/stsc/stco}. Chapter start times are derived from the
 * chapter track's own sample tables; titles are read directly from each
 * chunk (QuickTime text sample: 2-byte big-endian length + text bytes).
 *
 * ponytail: reduced port of Voice's visitor-based parser — handles only the
 * common iTunes layout: 32-bit stco (no co64), length-prefixed UTF-8/UTF-16
 * (BOM) titles, single chap reference. co64 / smhd-style stsz walk /
 * BOM-less UTF-16 titles are unsupported; extend if real files show up.
 */
public object Mp4ChapterTrackParser {
    private const val MAX_CHAPTERS = 4096
    private const val MAX_TABLE_ENTRIES = 65536
    private const val MAX_ATOM_BYTES = 8L * 1024 * 1024
    private const val MAX_TITLE_BYTES = 64 * 1024

    /**
     * Parses chapters from an iTunes-style chapter text track.
     *
     * @return Chapters sorted by start time, or null when the file has no
     *         chapter track / is malformed / is not MP4.
     */
    public fun parseChapters(file: File): List<M4bChapter>? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val moov = findBox(raf, 0, raf.length(), "moov") ?: return null
                val info = walkMoov(raf, moov)
                // Per QuickTime spec the 'chap' tref on any track references
                // the chapter text track; prefer the first referenced ID whose
                // track carries a complete sample table.
                val chapterTrack =
                    info.chapterTrackIds
                        .asSequence()
                        .mapNotNull { refId -> info.tracks.firstOrNull { it.id == refId } }
                        .firstOrNull { it.isComplete } ?: return null
                buildChapters(raf, chapterTrack)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── box tree walking ───────────────────────────────────────────────

    private fun walkMoov(
        raf: RandomAccessFile,
        moov: Box,
    ): MoovInfo {
        val info = MoovInfo()
        var pos = moov.dataStart
        while (pos < moov.end) {
            val box = readBoxAt(raf, pos, moov.end) ?: break
            if (box.type == "trak") {
                val track = TrackInfo()
                walkTrak(raf, box, info, track)
                info.tracks.add(track)
            }
            pos = box.end
        }
        return info
    }

    private fun walkTrak(
        raf: RandomAccessFile,
        trak: Box,
        info: MoovInfo,
        track: TrackInfo,
    ) {
        var pos = trak.dataStart
        while (pos < trak.end) {
            val box = readBoxAt(raf, pos, trak.end) ?: return
            when (box.type) {
                "tkhd" -> parseTkhd(raf, box)?.let { track.id = it }
                "tref" -> walkTref(raf, box, info)
                "mdia" -> walkMdia(raf, box, track)
            }
            pos = box.end
        }
    }

    private fun walkTref(
        raf: RandomAccessFile,
        tref: Box,
        info: MoovInfo,
    ) {
        var pos = tref.dataStart
        while (pos < tref.end) {
            val box = readBoxAt(raf, pos, tref.end) ?: return
            if (box.type == "chap") readChapRefs(raf, box, info)
            pos = box.end
        }
    }

    private fun walkMdia(
        raf: RandomAccessFile,
        mdia: Box,
        track: TrackInfo,
    ) {
        var pos = mdia.dataStart
        while (pos < mdia.end) {
            val box = readBoxAt(raf, pos, mdia.end) ?: return
            when (box.type) {
                "mdhd" -> parseMdhd(raf, box)?.let { track.timescale = it }
                "minf" -> walkMinf(raf, box, track)
            }
            pos = box.end
        }
    }

    private fun walkMinf(
        raf: RandomAccessFile,
        minf: Box,
        track: TrackInfo,
    ) {
        var pos = minf.dataStart
        while (pos < minf.end) {
            val box = readBoxAt(raf, pos, minf.end) ?: return
            if (box.type == "stbl") walkStbl(raf, box, track)
            pos = box.end
        }
    }

    private fun walkStbl(
        raf: RandomAccessFile,
        stbl: Box,
        track: TrackInfo,
    ) {
        var pos = stbl.dataStart
        while (pos < stbl.end) {
            val box = readBoxAt(raf, pos, stbl.end) ?: return
            when (box.type) {
                "stts" -> parseStts(raf, box)?.let { track.stts = it }
                "stsc" -> parseStsc(raf, box)?.let { track.stsc = it }
                "stco" -> parseStco(raf, box)?.let { track.stco = it }
            }
            pos = box.end
        }
    }

    // ── leaf box parsing ───────────────────────────────────────────────

    private fun parseTkhd(
        raf: RandomAccessFile,
        box: Box,
    ): Long? {
        if (!hasPayload(box, 4)) return null
        raf.seek(box.dataStart)
        val version = raf.read()
        if (version != 0 && version != 1) return null
        // version/flags + creation/modification, then track_ID.
        raf.skipBytes(3 + (if (version == 1) 16 else 8))
        if (box.end - raf.filePointer < 4) return null
        return readUint32(raf)
    }

    private fun parseMdhd(
        raf: RandomAccessFile,
        box: Box,
    ): Long? {
        if (!hasPayload(box, 4)) return null
        raf.seek(box.dataStart)
        val version = raf.read()
        if (version != 0 && version != 1) return null
        // version/flags + creation/modification, then timescale.
        raf.skipBytes(3 + (if (version == 1) 16 else 8))
        if (box.end - raf.filePointer < 4) return null
        return readUint32(raf)
    }

    private fun readChapRefs(
        raf: RandomAccessFile,
        box: Box,
        info: MoovInfo,
    ) {
        if (box.end - box.dataStart > MAX_ATOM_BYTES) return
        var pos = box.dataStart
        while (pos + 4 <= box.end && info.chapterTrackIds.size < MAX_TABLE_ENTRIES) {
            raf.seek(pos)
            info.chapterTrackIds.add(readUint32(raf))
            pos += 4
        }
    }

    private fun parseStts(
        raf: RandomAccessFile,
        box: Box,
    ): List<SttsEntry>? {
        val count = readEntryCount(raf, box) ?: return null
        if (box.end - raf.filePointer < count * 8) return null
        val entries = ArrayList<SttsEntry>(count.toInt())
        repeat(count.toInt()) {
            entries.add(
                SttsEntry(
                    sampleCount = readUint32(raf),
                    sampleDuration = readUint32(raf),
                ),
            )
        }
        return entries
    }

    private fun parseStsc(
        raf: RandomAccessFile,
        box: Box,
    ): List<StscEntry>? {
        val count = readEntryCount(raf, box) ?: return null
        if (box.end - raf.filePointer < count * 12) return null
        val entries = ArrayList<StscEntry>(count.toInt())
        repeat(count.toInt()) {
            entries.add(
                StscEntry(
                    firstChunk = readUint32(raf),
                    samplesPerChunk = readUint32(raf).toInt(),
                ),
            )
            raf.skipBytes(4) // sample description index — unused
        }
        return entries
    }

    private fun parseStco(
        raf: RandomAccessFile,
        box: Box,
    ): List<Long>? {
        val count = readEntryCount(raf, box) ?: return null
        if (box.end - raf.filePointer < count * 4) return null
        val offsets = ArrayList<Long>(count.toInt())
        repeat(count.toInt()) { offsets.add(readUint32(raf)) }
        return offsets
    }

    /** Positions raf at first entry; returns entry count after ver/flags. */
    private fun readEntryCount(
        raf: RandomAccessFile,
        box: Box,
    ): Long? {
        if (box.end - box.dataStart > MAX_ATOM_BYTES) return null
        if (box.end - box.dataStart < 8) return null
        raf.seek(box.dataStart)
        raf.skipBytes(4) // version + flags
        val count = readUint32(raf)
        return if (count in 1..MAX_TABLE_ENTRIES) count else null
    }

    // ── chapter assembly (port of Voice's ChapterTrackProcessor) ───────

    private fun buildChapters(
        raf: RandomAccessFile,
        track: TrackInfo,
    ): List<M4bChapter>? {
        val fileLength = raf.length()
        val chapters = mutableListOf<M4bChapter>()
        var position = 0L // in media timescale units
        var entryIndex = 0
        var consumedInEntry = 0L

        for (chunkIndex in track.stco.indices) {
            if (chapters.size >= MAX_CHAPTERS) break
            val startMs = position * 1000 / track.timescale
            val title =
                readTitleAt(raf, track.stco[chunkIndex], fileLength)
                    ?: "Chapter ${chapters.size + 1}"
            chapters.add(M4bChapter(startMs = startMs, title = title))

            // Advance the media clock by the durations of this chunk's samples.
            var remaining = samplesPerChunk(chunkIndex, track.stsc).toLong()
            while (remaining > 0) {
                if (entryIndex >= track.stts.size) break
                val entry = track.stts[entryIndex]
                val leftInEntry = entry.sampleCount - consumedInEntry
                if (leftInEntry <= 0) {
                    entryIndex++
                    consumedInEntry = 0
                    continue
                }
                val take = minOf(remaining, leftInEntry)
                position += take * entry.sampleDuration
                remaining -= take
                consumedInEntry += take
            }
            // ponytail: once stts entries are exhausted we cannot compute
            // further chapter starts — stop instead of emitting bogus zeros.
            if (remaining > 0) break
        }

        if (chapters.isEmpty()) return null
        return chapters.sortedBy { it.startMs }
    }

    private fun samplesPerChunk(
        chunkIndex: Int,
        stsc: List<StscEntry>,
    ): Int {
        for (i in stsc.indices) {
            val entry = stsc[i]
            val next = stsc.getOrNull(i + 1)
            if (chunkIndex + 1 >= entry.firstChunk && (next == null || chunkIndex + 1 < next.firstChunk)) {
                return entry.samplesPerChunk
            }
        }
        return 1
    }

    /** Reads a QuickTime text sample: 2-byte BE length + text bytes. */
    private fun readTitleAt(
        raf: RandomAccessFile,
        offset: Long,
        fileLength: Long,
    ): String? {
        if (offset < 0 || offset + 2 > fileLength) return null
        raf.seek(offset)
        val len = (raf.read() shl 8) or raf.read()
        if (len <= 0 || len > MAX_TITLE_BYTES || offset + 2 + len > fileLength) return null
        val bytes = ByteArray(len)
        raf.readFully(bytes)
        return when {
            // BOM handling: chapter tools frequently store UTF-16 titles.
            len >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, len - 2, Charsets.UTF_16BE)
            len >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, len - 2, Charsets.UTF_16LE)
            else -> String(bytes, Charsets.UTF_8)
        }.trim().ifEmpty { null }
    }

    // ── binary helpers (big-endian, mirrors M4bChapterParser) ──────────

    private fun hasPayload(
        box: Box,
        minBytes: Long,
    ): Boolean = box.end - box.dataStart >= minBytes

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
        var result = 0L
        for (b in buf) result = (result shl 8) or (b.toLong() and 0xFF)
        return result
    }

    private fun readBoxType(raf: RandomAccessFile): String {
        val buf = ByteArray(4)
        raf.readFully(buf)
        return String(buf, Charsets.US_ASCII)
    }

    // ── data holders ───────────────────────────────────────────────────

    private class MoovInfo {
        val tracks = mutableListOf<TrackInfo>()
        val chapterTrackIds = mutableListOf<Long>()
    }

    private class TrackInfo {
        var id: Long = 0
        var timescale: Long = 0
        var stts: List<SttsEntry> = emptyList()
        var stsc: List<StscEntry> = emptyList()
        var stco: List<Long> = emptyList()

        val isComplete: Boolean
            get() = timescale > 0 && stts.isNotEmpty() && stsc.isNotEmpty() && stco.isNotEmpty()
    }

    private data class SttsEntry(
        val sampleCount: Long,
        val sampleDuration: Long,
    )

    private data class StscEntry(
        val firstChunk: Long,
        val samplesPerChunk: Int,
    )

    private data class Box(
        val type: String,
        val dataStart: Long,
        val end: Long,
    )
}
