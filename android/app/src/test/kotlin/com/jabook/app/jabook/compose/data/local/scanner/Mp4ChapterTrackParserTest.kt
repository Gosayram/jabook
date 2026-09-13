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
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

class Mp4ChapterTrackParserTest {
    // ── helpers ────────────────────────────────────────────────────────

    private fun tempFile(bytes: ByteArray): File {
        val f = File.createTempFile("mp4_chap_test_", ".m4b")
        f.deleteOnExit()
        RandomAccessFile(f, "rw").use { it.write(bytes) }
        return f
    }

    private fun u32(v: Long): ByteArray {
        val out = ByteArray(4)
        for (i in 0..3) out[i] = ((v shr (24 - 8 * i)) and 0xFF).toByte()
        return out
    }

    private fun u16(v: Int): ByteArray = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

    private fun box(
        type: String,
        vararg payload: ByteArray,
    ): ByteArray {
        val size = 8 + payload.sumOf { it.size }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(size)
            d.write(type.toByteArray(Charsets.US_ASCII))
            payload.forEach { d.write(it) }
        }
        return out.toByteArray()
    }

    /** QuickTime text sample: 2-byte BE length + UTF-8 bytes. */
    private fun titleSample(text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        return u16(textBytes.size) + textBytes
    }

    /**
     * Minimal MP4 with an iTunes chapter track:
     * mdat(title samples) + moov(trak1(tkhd, tref(chap→2)) + trak2(tkhd, mdia(mdhd, minf(stbl)))).
     *
     * stts gives each chapter track sample [durationMs]; 1 sample per chunk.
     */
    private fun buildMp4WithChapterTrack(
        titles: List<String>,
        durationsMs: List<Long>,
        timescale: Long = 1000,
    ): ByteArray {
        val samples = titles.map { titleSample(it) }
        val mdat = box("mdat", *samples.toTypedArray())

        // mdat is the first top-level box → chunk offsets start at 8.
        val offsets = mutableListOf<Long>()
        var p = 8L
        samples.forEach {
            offsets.add(p)
            p += it.size
        }

        val sttsPayload =
            byteArrayOf(0, 0, 0, 0) + u32(durationsMs.size.toLong()) +
                durationsMs.flatMap { (u32(1) + u32(it)).toList() }.toByteArray()
        val stts = box("stts", sttsPayload)
        val stsc = box("stsc", byteArrayOf(0, 0, 0, 0), u32(1), u32(1), u32(1), u32(0))
        val stcoPayload =
            byteArrayOf(0, 0, 0, 0) + u32(offsets.size.toLong()) +
                offsets.flatMap { u32(it).toList() }.toByteArray()
        val stco = box("stco", stcoPayload)

        val stbl = box("stbl", stts, stsc, stco)
        val minf = box("minf", stbl)
        val mdhd = box("mdhd", byteArrayOf(0, 0, 0, 0), u32(0), u32(0), u32(timescale), u32(0))
        val mdia = box("mdia", mdhd, minf)

        val chapterTrak = box("trak", box("tkhd", byteArrayOf(0, 0, 0, 0), u32(0), u32(0), u32(2)), mdia)

        // Referencing (audio) track: tref 'chap' → chapter track ID 2.
        val audioTrak =
            box(
                "trak",
                box("tkhd", byteArrayOf(0, 0, 0, 0), u32(0), u32(0), u32(1)),
                box("tref", box("chap", u32(2))),
            )

        return mdat + box("moov", audioTrak, chapterTrak)
    }

    // ── tests ──────────────────────────────────────────────────────────

    @Test
    fun `parses two chapters from tref chap track`() {
        val bytes =
            buildMp4WithChapterTrack(
                titles = listOf("Chapter One", "Chapter Two"),
                durationsMs = listOf(10_000L, 5_000L),
            )
        val chapters = Mp4ChapterTrackParser.parseChapters(tempFile(bytes))

        assertNotNull(chapters)
        assertEquals(2, chapters!!.size)
        assertEquals(M4bChapter(startMs = 0, title = "Chapter One"), chapters[0])
        assertEquals(M4bChapter(startMs = 10_000, title = "Chapter Two"), chapters[1])
    }

    @Test
    fun `falls back to Chapter N when sample text missing`() {
        val bytes =
            buildMp4WithChapterTrack(
                titles = listOf("Chapter One", ""),
                durationsMs = listOf(1_000L, 1_000L),
            )
        val chapters = Mp4ChapterTrackParser.parseChapters(tempFile(bytes))

        assertNotNull(chapters)
        assertEquals("Chapter 2", chapters!![1].title)
    }

    @Test
    fun `returns null when no chap track present`() {
        val bytes =
            box(
                "moov",
                box(
                    "trak",
                    box("tkhd", byteArrayOf(0, 0, 0, 0), u32(0), u32(0), u32(1)),
                    box("mdia", box("mdhd", byteArrayOf(0, 0, 0, 0), u32(0), u32(0), u32(1000), u32(0))),
                ),
            )
        assertNull(Mp4ChapterTrackParser.parseChapters(tempFile(bytes)))
    }
}
