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

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadata
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.local.parser.EncodingDetector
import com.jabook.app.jabook.compose.data.local.parser.MetadataCache
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

/**
 * Regression tests for the "5 books in Dmitry's folder, app sees 3" scanner bug:
 * nested multi-disc book folders, stale content:// scan path rows, and
 * audiobook extension coverage.
 */
class DirectFileSystemScannerGroupingTest {
    private val logger = mock<Logger>()
    private val loggerFactory = mock<LoggerFactory>()

    init {
        whenever(loggerFactory.get(any<String>())).thenReturn(logger)
        whenever(loggerFactory.get(any<kotlin.reflect.KClass<*>>())).thenReturn(logger)
    }

    private val nullParser =
        object : AudioMetadataParser {
            override suspend fun parseMetadata(filePath: String): AudioMetadata? = null
        }

    private fun scanner(scanPathDao: ScanPathDao) =
        DirectFileSystemScanner(
            metadataParser = nullParser,
            scanPathDao = scanPathDao,
            bookIdentifier = BookIdentifier(),
            encodingDetector = EncodingDetector(loggerFactory),
            metadataCache = MetadataCache(loggerFactory),
            loggerFactory = loggerFactory,
        )

    private fun writeAudio(
        dir: Path,
        vararg fileNames: String,
    ) {
        Files.createDirectories(dir)
        fileNames.forEach { name -> Files.write(dir.resolve(name), ByteArray(512)) }
    }

    @Test
    fun `five books with two stored as nested subfolders detect five books`() =
        runTest {
            val root = Files.createTempDirectory("jabook-scan-nested")
            try {
                // 3 flat books + 1 multi-disc book (2 nested subfolders) + 1 nested single file
                writeAudio(root.resolve("Book1"), "01.mp3", "02.mp3")
                writeAudio(root.resolve("Book2"), "01.mp3")
                writeAudio(root.resolve("Book3"), "01.mp3")
                writeAudio(root.resolve("Book4/CD1"), "01.mp3")
                writeAudio(root.resolve("Book4/CD2"), "01.mp3")
                writeAudio(root.resolve("Book5"), "01.m4b")

                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(ScanPathEntity(path = root.toString())),
                )

                val result = scanner(scanPathDao).scanAudiobooks()

                val books = (result as com.jabook.app.jabook.compose.domain.model.Result.Success).data
                assertEquals(5, books.size)
                val titles = books.map { it.title }.sorted()
                assertEquals(listOf("Book1", "Book2", "Book3", "Book4", "Book5"), titles)

                // The multi-disc book merges both discs into one book with all chapters
                val book4 = books.first { it.title == "Book4" }
                assertEquals(2, book4.chapters.size)
                assertEquals(root.resolve("Book4").toString(), book4.directory)
            } finally {
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

    @Test
    fun `scan path list with content uri and valid rows scans valid and skips invalid`() =
        runTest {
            val dirA = Files.createTempDirectory("jabook-scan-valid-a")
            val dirB = Files.createTempDirectory("jabook-scan-valid-b")
            try {
                writeAudio(dirA, "01.mp3")
                writeAudio(dirB, "01.mp3")

                val contentRow =
                    ScanPathEntity(
                        path = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks",
                    )
                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(contentRow, ScanPathEntity(path = dirA.toString()), ScanPathEntity(path = dirB.toString())),
                )

                val result = scanner(scanPathDao).scanAudiobooks()

                val books = (result as com.jabook.app.jabook.compose.domain.model.Result.Success).data
                assertEquals(2, books.size)

                // The dead content:// row never gets its timestamp bumped: it is skipped, not scanned
                verify(scanPathDao, never()).updateLastScanTimestamp(eq(contentRow.path), any())
            } finally {
                listOf(dirA, dirB).forEach { dir ->
                    Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }

    @Test
    fun `every audiobook extension in the fixed list is accepted by the scanner`() =
        runTest {
            val extensions =
                listOf("mp3", "m4b", "m4a", "flac", "ogg", "opus", "wav", "wma", "aac", "webm", "mkv")
            val root = Files.createTempDirectory("jabook-scan-extensions")
            try {
                writeAudio(
                    root.resolve("ExtBook"),
                    *extensions.mapIndexed { index, ext -> "%02d track.%s".format(index, ext) }.toTypedArray(),
                )

                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(ScanPathEntity(path = root.toString())),
                )

                val result = scanner(scanPathDao).scanAudiobooks()

                val books = (result as com.jabook.app.jabook.compose.domain.model.Result.Success).data
                assertEquals(1, books.size)
                assertEquals(extensions.size, books.single().chapters.size)

                // And the import-time policy stays in sync with the scanner
                assertTrue(
                    "MimeTypeValidationPolicy.SUPPORTED_EXTENSIONS must cover every audiobook extension",
                    MimeTypeValidationPolicy.SUPPORTED_EXTENSIONS.containsAll(extensions),
                )
            } finally {
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

    @Test
    fun `scan path prefix does not leak files from sibling directory with shared name`() =
        runTest {
            val root = Files.createTempDirectory("jabook-scan-root")
            try {
                // Scan path "root/books" must not match files under sibling "root/books2"
                writeAudio(root.resolve("books/BookA"), "01.mp3")
                writeAudio(root.resolve("books2/BookB"), "01.mp3")

                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(ScanPathEntity(path = root.resolve("books").toString())),
                )

                val result = scanner(scanPathDao).scanAudiobooks()

                val books = (result as com.jabook.app.jabook.compose.domain.model.Result.Success).data
                assertEquals(1, books.size)
                assertEquals("BookA", books.single().title)
            } finally {
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
}
