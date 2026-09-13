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

import android.content.Context
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadata
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.local.parser.EncodingDetector
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

/**
 * The scan path list holds one dead content:// row plus two valid folders:
 * valid rows must be scanned and the dead row must be REPORTED as skipped in
 * [ScanProgress.Completed] instead of silently scanning to zero.
 */
class HybridBookScannerSkipReportingTest {
    @Test
    fun `invalid scan path is reported as skipped while valid rows are scanned`() =
        runTest {
            val logger = mock<Logger>()
            val loggerFactory = mock<LoggerFactory>()
            whenever(loggerFactory.get(any<String>())).thenReturn(logger)
            whenever(loggerFactory.get(any<kotlin.reflect.KClass<*>>())).thenReturn(logger)

            val nullParser =
                object : AudioMetadataParser {
                    override suspend fun parseMetadata(filePath: String): AudioMetadata? = null
                }

            val dirA = Files.createTempDirectory("jabook-hybrid-a")
            val dirB = Files.createTempDirectory("jabook-hybrid-b")
            try {
                Files.write(dirA.resolve("01.mp3"), ByteArray(512))
                Files.write(dirB.resolve("01.mp3"), ByteArray(512))

                val contentRow =
                    ScanPathEntity(
                        path = "content://com.android.externalstorage.documents/tree/primary%3AAudiobooks",
                    )
                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(contentRow, ScanPathEntity(path = dirA.toString()), ScanPathEntity(path = dirB.toString())),
                )

                val hybrid =
                    HybridBookScanner(
                        mediaStoreScanner =
                            MediaStoreBookScanner(
                                context = mock<Context>(),
                                metadataParser = nullParser,
                                scanPathDao = scanPathDao,
                                encodingDetector = EncodingDetector(loggerFactory),
                                loggerFactory = loggerFactory,
                            ),
                        directScanner =
                            DirectFileSystemScanner(
                                metadataParser = nullParser,
                                scanPathDao = scanPathDao,
                                bookIdentifier = BookIdentifier(),
                                encodingDetector = EncodingDetector(loggerFactory),
                                metadataCache =
                                    com.jabook.app.jabook.compose.data.local.parser
                                        .MetadataCache(loggerFactory),
                                loggerFactory = loggerFactory,
                            ),
                        scanPathDao = scanPathDao,
                        loggerFactory = loggerFactory,
                    )

                val result = hybrid.scanAudiobooks()

                val books = (result as Result.Success).data
                assertEquals(2, books.size)

                val completed = hybrid.scanProgress.value as ScanProgress.Completed
                assertEquals(2, completed.booksAdded)
                assertEquals(1, completed.skippedPaths)

                // Dead row is kept (don't drop user config) but never deleted silently
                verify(scanPathDao, never()).deletePath(contentRow)
                assertTrue(Files.exists(dirA.resolve("01.mp3")))
            } finally {
                listOf<Path>(dirA, dirB).forEach { dir ->
                    Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
}
