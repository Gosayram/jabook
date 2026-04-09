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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.util.Comparator

class DirectFileSystemScannerCancellationTest {
    @Test
    fun `scanAudiobooks propagates cancellation and skips timestamp updates`() =
        runBlocking {
            val logger = mock<Logger>()
            val loggerFactory = mock<LoggerFactory>()
            whenever(loggerFactory.get(any<String>())).thenReturn(logger)
            whenever(loggerFactory.get(any<kotlin.reflect.KClass<*>>())).thenReturn(logger)

            val scanPathDao = mock<ScanPathDao>()
            val rootDir = Files.createTempDirectory("jabook-scan-cancel")
            try {
                val audioFile = rootDir.resolve("chapter01.mp3")
                Files.write(audioFile, ByteArray(512))

                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(
                        ScanPathEntity(
                            path = rootDir.toString(),
                            lastScanTimestamp = 0L,
                        ),
                    ),
                )
                doAnswer {}.whenever(scanPathDao).updateLastScanTimestamp(any(), any())

                val parser =
                    object : AudioMetadataParser {
                        override suspend fun parseMetadata(filePath: String): AudioMetadata? =
                            suspendCancellableCoroutine { continuation ->
                                continuation.invokeOnCancellation { }
                            }
                    }

                val scanner =
                    DirectFileSystemScanner(
                        metadataParser = parser,
                        scanPathDao = scanPathDao,
                        bookIdentifier = BookIdentifier(),
                        encodingDetector = EncodingDetector(loggerFactory),
                        metadataCache = MetadataCache(loggerFactory),
                        loggerFactory = loggerFactory,
                        incrementalScanPolicy = IncrementalScanPolicy(),
                    )

                val scanJob = async { scanner.scanAudiobooks() }

                withTimeout(2_000L) {
                    scanJob.cancelAndJoin()
                }

                assertThrows(CancellationException::class.java) {
                    runBlocking {
                        scanJob.await()
                    }
                }
                verify(scanPathDao, never()).updateLastScanTimestamp(eq(rootDir.toString()), any())
            } finally {
                Files
                    .walk(rootDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
}
