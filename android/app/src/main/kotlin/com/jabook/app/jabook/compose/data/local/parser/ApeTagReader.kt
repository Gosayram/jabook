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

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Text fields we care about from an APEv2 tag. All values are already UTF-8
 * decoded by the reader — that is the entire reason this class exists:
 * MediaMetadataRetriever ignores APEv2, so RuTracker audiobook MP3s with
 * Cyrillic APE titles come back empty or mojibake from ID3 alone.
 */
public data class ApeTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val composer: String? = null,
    val year: String? = null,
    val comment: String? = null,
)

/**
 * Minimal APEv2 tag reader (format reading only, ported from TagLib's
 * ape.cpp — no writing, no native code).
 *
 * Layout (all integers 32-bit LITTLE endian — APEv2 is LE, unlike most
 * container formats, see TagLib's ByteVector::toUInt32(..., false)):
 *
 *   footer, last 32 bytes of the tag:
 *     [0..8)   "APETAGEX"
 *     [8..12)  version (1000|2000)
 *     [12..16) length of all items (excluding footer and optional header)
 *     [16..20) item count
 *     [20..32) flags (bit 31 = header present — we never touch the header)
 *
 *   items, starting at (footerOffset - itemsLength):
 *     4B value length | 4B flags | key + NUL | value
 *
 * Item flags bits 1-2: 0 = UTF-8 text, 1 = binary, 2 = locator. Binary items
 * (cover art, cuesheet) are skipped — this reader only extracts text.
 *
 * Safe on untrusted files: [MAX_ITEMS]/[MAX_VALUE_BYTES] caps, and a
 * truncated tail just ends iteration with whatever was parsed so far.
 */
public object ApeTagReader {
    public const val MAX_ITEMS: Int = 1000
    public const val MAX_VALUE_BYTES: Int = 64 * 1024

    private const val FOOTER_SIZE = 32
    private const val MAGIC = "APETAGEX"
    private const val MAX_KEY_BYTES = 256

    /** @return parsed tags, or null if the file has no usable APEv2 footer. */
    public fun read(file: File): ApeTags? {
        val fileLength = file.length()
        if (fileLength < FOOTER_SIZE) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(fileLength - FOOTER_SIZE)
                val footer = ByteArray(FOOTER_SIZE).also { raf.readFully(it) }
                if (String(footer, 0, 8, StandardCharsets.ISO_8859_1) != MAGIC) return null

                // ponytail: ignore the optional 32-byte header — items live strictly
                // between (footer - itemsLength) and footer, header or not.
                val itemsLength = le32(footer, 12)
                val itemCount = le32(footer, 16)
                val tagStart = fileLength - FOOTER_SIZE - itemsLength
                if (itemsLength < 0 || itemCount < 0 || tagStart < 0) return null

                raf.seek(tagStart)
                val tags = LinkedHashMap<String, String>()
                var index = 0
                while (index < itemCount && index < MAX_ITEMS) {
                    val head = ByteArray(8)
                    try {
                        raf.readFully(head)
                    } catch (_: EOFException) {
                        break // truncated item header
                    }
                    val valueLength = le32(head, 0)
                    val flags = le32(head, 4)
                    val key = readCString(raf) ?: break
                    if (valueLength < 0) break
                    val binary = (flags ushr 1) and 0x3 == 1
                    if (valueLength <= MAX_VALUE_BYTES) {
                        val value = ByteArray(valueLength)
                        try {
                            raf.readFully(value)
                        } catch (_: EOFException) {
                            break
                        }
                        if (!binary) {
                            tags.putIfAbsent(key.uppercase(), String(value, StandardCharsets.UTF_8))
                        }
                    } else {
                        // Oversized: skip it, keeping the stream in sync for later items.
                        raf.seek(min(fileLength, raf.filePointer + valueLength))
                    }
                    index++
                }
                if (tags.isEmpty()) return null
                ApeTags(
                    title = tags["TITLE"],
                    artist = tags["ARTIST"],
                    album = tags["ALBUM"],
                    composer = tags["COMPOSER"],
                    year = tags["YEAR"],
                    comment = tags["COMMENT"],
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun le32(
        b: ByteArray,
        off: Int,
    ): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun readCString(raf: RandomAccessFile): String? {
        val bytes = ByteArray(MAX_KEY_BYTES)
        var n = 0
        while (n < MAX_KEY_BYTES) {
            val c = raf.read()
            if (c < 0) return null // EOF before terminator
            if (c == 0) break
            bytes[n++] = c.toByte()
        }
        return String(bytes, 0, n, StandardCharsets.ISO_8859_1)
    }
}
