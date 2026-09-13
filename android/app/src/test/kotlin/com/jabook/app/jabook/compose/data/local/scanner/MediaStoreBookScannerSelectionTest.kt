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

import android.content.ContentResolver
import android.content.Context
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadata
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.local.parser.EncodingDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Best-wins selection behavior of [MediaStoreBookScanner.createScannedBook]:
 * chapter titles, book title/author, and track-file-name chapter derivation.
 */
class MediaStoreBookScannerSelectionTest {
    private val loggerFactory = mock<LoggerFactory>()

    init {
        val logger = mock<Logger>()
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)
        whenever(loggerFactory.get(any<kotlin.reflect.KClass<*>>())).thenReturn(logger)
    }

    private fun scanner(parser: AudioMetadataParser) =
        MediaStoreBookScanner(
            context = mock<Context>(),
            metadataParser = parser,
            scanPathDao = mock<ScanPathDao>(),
            encodingDetector = EncodingDetector(loggerFactory),
            loggerFactory = loggerFactory,
        )

    private fun file(
        name: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ) = AudioFileInfo(
        filePath = "/storage/emulated/0/Audiobooks/Book/$name",
        displayName = name,
        duration = 60_000L,
        album = album,
        artist = artist,
        title = title,
    )

    private class FakeParser(
        private val byPath: Map<String, AudioMetadata>,
    ) : AudioMetadataParser {
        val calls = mutableListOf<String>()

        override suspend fun parseMetadata(filePath: String): AudioMetadata? {
            calls.add(filePath)
            return byPath[filePath]
        }
    }

    private fun meta(
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
    ) = AudioMetadata(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        duration = 60_000L,
        genre = null,
        year = null,
        trackNumber = null,
        coverArt = null,
    )

    @Test
    fun `clean parser title wins over corrupted mediastore title and file is parsed once`() =
        runTest {
            val a = file("01.mp3", title = "Dune \uFFFDart \uFFFDne")
            val b = file("02.mp3")
            val parser =
                FakeParser(
                    mapOf(
                        a.filePath to meta(title = "Dune Part One"),
                        b.filePath to meta(title = "Dune Part Two"),
                    ),
                )

            val book = scanner(parser).createScannedBook("Dune", listOf(a, b))!!

            assertEquals(listOf("Dune Part One", "Dune Part Two"), book.chapters.map { it.title })
            // Book-level parse of the first file is reused for its chapter title.
            assertEquals(2, parser.calls.size)
        }

    @Test
    fun `book title and author pick the best candidate across parser and mediastore`() =
        runTest {
            val a = file("01.mp3", artist = "Frank Herbert", album = "<unknown>")
            val parser =
                FakeParser(
                    mapOf(
                        a.filePath to
                            meta(
                                album = "Dune",
                                albumArtist = "<unknown>",
                                artist = "Frank Herbert",
                            ),
                    ),
                )

            val book = scanner(parser).createScannedBook("<unknown>", listOf(a))!!

            assertEquals("Dune", book.title)
            assertEquals("Frank Herbert", book.author)
        }

    @Test
    fun `clean mediastore album beats corrupted parser album`() =
        runTest {
            val a = file("01.mp3", album = "War and Peace")
            val parser = FakeParser(mapOf(a.filePath to meta(album = "Ã¢ÂÂÃ¢ÂÂ")))

            val book = scanner(parser).createScannedBook("War and Peace", listOf(a))!!

            assertEquals("War and Peace", book.title)
        }

    @Test
    fun `track file names become chapter N from leading number or position`() =
        runTest {
            val parser = FakeParser(emptyMap())

            val book =
                scanner(parser)
                    .createScannedBook(
                        "Book",
                        listOf(
                            file("01.mp3"),
                            file("03.mp3", title = "Track 03"),
                            file("07 - The Call.mp3", title = "Track 07"),
                        ),
                    )!!

            assertEquals(
                listOf("Chapter 1", "Chapter 3", "07 - The Call"),
                book.chapters.map { it.title },
            )
        }

    @Test
    fun `default scan does not require MediaStore category flags`() =
        runTest {
            val context = mock<Context>()
            val contentResolver = mock<ContentResolver>()
            val scanPathDao = mock<ScanPathDao>()

            whenever(context.contentResolver).thenReturn(contentResolver)
            whenever(scanPathDao.getAllPathsList()).thenReturn(emptyList())
            whenever(
                contentResolver.query(
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(null)

            MediaStoreBookScanner(
                context = context,
                metadataParser = FakeParser(emptyMap()),
                scanPathDao = scanPathDao,
                encodingDetector = EncodingDetector(loggerFactory),
                loggerFactory = loggerFactory,
            ).scanAudiobooks()

            verify(contentResolver).query(anyOrNull(), anyOrNull(), isNull(), isNull(), isNull())
        }
}
