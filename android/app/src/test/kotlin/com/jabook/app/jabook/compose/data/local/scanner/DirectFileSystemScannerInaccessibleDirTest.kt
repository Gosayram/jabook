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
import com.jabook.app.jabook.compose.data.local.parser.MetadataCache
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Regression tests for "scanner sees 3 of 5 books":
 * - incremental scans must scan directories that are not known books yet even
 *   when their files carry old mtimes (torrent-unpacked / copied folders), and
 * - permission-denied directories must be reported (skipped paths + no
 *   timestamp bump) instead of silently scanning to nothing.
 */
class DirectFileSystemScannerInaccessibleDirTest {
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
    fun `unknown directory with old mtimes is scanned while known unchanged directory is skipped`() =
        runTest {
            val root = Files.createTempDirectory("jabook-incr")
            val known = root.resolve("KnownBook")
            val unknown = root.resolve("TorrentBook")
            try {
                writeAudio(known, "01.mp3")
                writeAudio(unknown, "01.mp3")

                val scanPathDao = mock<ScanPathDao>()
                // Future timestamp => every real file mtime looks unchanged
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(
                        ScanPathEntity(path = root.toString(), lastScanTimestamp = System.currentTimeMillis() + 3_600_000L),
                    ),
                )

                val result =
                    scanner(scanPathDao)
                        .scanAudiobooks(knownDirectories = setOf(known.toString()))

                val books = (result as Result.Success).data
                // Only the directory that is NOT a known book gets scanned
                assertEquals(1, books.size)
                assertEquals("TorrentBook", books.single().title)
            } finally {
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

    @Test
    fun `default call with no previous timestamps falls back to full scan`() =
        runTest {
            val root = Files.createTempDirectory("jabook-incr-all")
            try {
                writeAudio(root.resolve("A"), "01.mp3")
                writeAudio(root.resolve("B"), "01.mp3")

                val scanPathDao = mock<ScanPathDao>()
                // lastScanTimestamp = 0 -> no previous scan -> full scan
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(ScanPathEntity(path = root.toString())),
                )

                val result = scanner(scanPathDao).scanAudiobooks()

                assertEquals(2, (result as Result.Success).data.size)
            } finally {
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

    @Test
    fun `unreadable directory is counted inaccessible and does not abort the scan`() =
        runTest {
            val root = Files.createTempDirectory("jabook-inaccessible")
            val locked = root.resolve("LockedBook")
            try {
                writeAudio(root.resolve("OkBook"), "01.mp3")
                writeAudio(locked, "hidden.mp3")
                Files.setPosixFilePermissions(locked, emptySet())

                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(ScanPathEntity(path = root.toString())),
                )

                val directScanner = scanner(scanPathDao)
                val result = directScanner.scanAudiobooks()

                val books = (result as Result.Success).data
                assertEquals(1, books.size)
                assertEquals("OkBook", books.single().title)
                assertEquals(1, directScanner.lastInaccessibleDirCount)

                // Locked dir must be retried next scan: no timestamp bump
                verify(scanPathDao, never()).updateLastScanTimestamp(any(), any())
            } finally {
                if (Files.exists(locked)) {
                    Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"))
                }
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

    @Test
    fun `hybrid scanner surfaces inaccessible dirs as skipped paths`() =
        runTest {
            val root = Files.createTempDirectory("jabook-hybrid-lock")
            val locked = root.resolve("LockedBook")
            try {
                writeAudio(root.resolve("OkBook"), "01.mp3")
                writeAudio(locked, "hidden.mp3")
                Files.setPosixFilePermissions(locked, emptySet())

                val scanPathDao = mock<ScanPathDao>()
                whenever(scanPathDao.getAllPathsList()).thenReturn(
                    listOf(ScanPathEntity(path = root.toString())),
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
                        directScanner = scanner(scanPathDao),
                        scanPathDao = scanPathDao,
                        loggerFactory = loggerFactory,
                    )

                val result = hybrid.scanAudiobooks()

                assertEquals(1, (result as Result.Success).data.size)
                val completed = hybrid.scanProgress.value as ScanProgress.Completed
                assertEquals(1, completed.skippedPaths)
            } finally {
                if (Files.exists(locked)) {
                    Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"))
                }
                Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
}
