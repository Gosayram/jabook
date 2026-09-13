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

package com.jabook.app.jabook.compose.data.local.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Pure-JVM tests for the APEv2 reader (no Robolectric needed — the reader has
 * zero Android imports). Tag bytes are hand-built per the APEv2 spec: all
 * integers little endian, item = len|flags|key+NUL|value, footer =
 * "APETAGEX"|version|itemsLength|itemCount|flags at the very end.
 */
public class ApeTagReaderTest {
    @get:Rule
    public val tmp: TemporaryFolder = TemporaryFolder()

    private fun le32(v: Int): ByteArray =
        byteArrayOf(
            (v and 0xFF).toByte(),
            (v ushr 8 and 0xFF).toByte(),
            (v ushr 16 and 0xFF).toByte(),
            (v ushr 24 and 0xFF).toByte(),
        )

    private fun item(
        key: String,
        value: ByteArray,
        flags: Int = 0,
    ): ByteArray = le32(value.size) + le32(flags) + key.toByteArray(StandardCharsets.ISO_8859_1) + 0.toByte() + value

    private fun item(
        key: String,
        value: String,
    ): ByteArray = item(key, value.toByteArray(StandardCharsets.UTF_8))

    private fun apeFooter(
        itemsLength: Int,
        count: Int,
    ): ByteArray =
        "APETAGEX".toByteArray(StandardCharsets.ISO_8859_1) +
            le32(2000) + le32(itemsLength) + le32(count) + le32(0) +
            ByteArray(8) // 8 reserved bytes make the footer 32 total

    /** junk audio bytes + items + 32-byte footer; [declaredCount] lets a test lie about item count. */
    private fun writeMp3(
        vararg items: ByteArray,
        declaredCount: Int = items.size,
    ): File {
        val body = ByteArray(1024) { (it % 251).toByte() } + items.fold(ByteArray(0)) { a, b -> a + b }
        val footer = apeFooter(body.size - 1024, declaredCount)
        val file = File(tmp.root, "book.mp3")
        file.writeBytes(body + footer)
        return file
    }

    @Test
    public fun parsesUtf8CyrillicFields() {
        val f =
            writeMp3(
                item("TITLE", "Пикник на обочине"),
                item("ARTIST", "Стругацкие"),
                item("ALBUM", "Пикник"),
                item("YEAR", "1977"),
            )
        val tags = ApeTagReader.read(f)
        assertNotNull(tags)
        assertEquals("Пикник на обочине", tags!!.title)
        assertEquals("Стругацкие", tags.artist)
        assertEquals("Пикник", tags.album)
        assertEquals("1977", tags.year)
    }

    @Test
    public fun noFooterReturnsNull() {
        val f = File(tmp.root, "plain.mp3")
        f.writeBytes(ByteArray(2048) { 0x7F.toByte() })
        assertNull(ApeTagReader.read(f))
    }

    @Test
    public fun binaryFlaggedItemsAreSkipped() {
        // item flags bits 1-2 == 1 means binary (e.g. cover art) — must not leak into text fields
        val f =
            writeMp3(
                item("TITLE", "Нормальный заголовок"),
                item("COVER ART (JPEG)", ByteArray(16) { 0xFF.toByte() }, flags = 1 shl 1),
            )
        val tags = ApeTagReader.read(f)
        assertEquals("Нормальный заголовок", tags!!.title)
        assertNull(tags.comment)
    }

    @Test
    public fun truncatedTailKeepsEarlierItems() {
        // itemCount says 2 but the second item's value is cut off by EOF
        val body =
            ByteArray(1024) + item("TITLE", "Хоббит") +
                le32(9999) + le32(0) + "ARTIST".toByteArray(StandardCharsets.ISO_8859_1) + 0.toByte() + byteArrayOf(1, 2)
        val footer = apeFooter(body.size - 1024, 2)
        val f = File(tmp.root, "trunc.mp3")
        f.writeBytes(body + footer)
        val tags = ApeTagReader.read(f)
        assertEquals("Хоббит", tags!!.title)
        assertNull(tags.artist)
    }

    @Test
    public fun itemCapStopsAt1000Items() {
        val items = (0 until 1000).map { item("KEY$it", "v$it") }.toMutableList()
        items += item("TITLE", "за пределами лимита") // 1001st — must be ignored
        val f = writeMp3(*items.toTypedArray(), declaredCount = 1001)
        val tags = ApeTagReader.read(f)
        assertNotNull(tags) // 1000 junk items parsed, but cap hit before TITLE
        assertNull(tags!!.title)
    }
}
