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

import com.jabook.app.jabook.compose.core.util.CoverWaterfallPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration tests validating cover waterfall resolution against realistic
 * scanner-produced book directory structures.
 *
 * Covers scenarios the scanner encounters: single-file books, multi-chapter
 * books, nested series, books with embedded covers, and online-only books.
 */
class ScannerCoverIntegrationTest {
    private val tempFolder = TemporaryFolder()

    @Before
    fun setup() {
        tempFolder.create()
    }

    @After
    fun teardown() {
        tempFolder.delete()
    }

    // ---- Single-file book with cover image in same folder ----

    @Test
    fun `single-file book resolves cover from folder image`() {
        // Simulates: /storage/audiobooks/BookTitle/
        //   ├── book.mp3
        //   └── cover.jpg
        val bookDir = tempFolder.newFolder("BookTitle")
        File(bookDir, "book.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "cover.jpg").writeBytes(ByteArray(2048))

        val coversDir = tempFolder.newFolder("covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "single-file-1",
                localPath = bookDir.absolutePath,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result.source)
    }

    // ---- Multi-chapter book with folder.jpg ----

    @Test
    fun `multi-chapter book resolves cover from folder image`() {
        // Simulates: /storage/audiobooks/MultiChapter/
        //   ├── 01 - Introduction.mp3
        //   ├── 02 - Chapter One.mp3
        //   ├── 03 - Chapter Two.mp3
        //   └── folder.jpg
        val bookDir = tempFolder.newFolder("MultiChapter")
        File(bookDir, "01 - Introduction.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "02 - Chapter One.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "03 - Chapter Two.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "folder.jpg").writeBytes(ByteArray(4096))

        val coversDir = tempFolder.newFolder("covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "multi-chapter-1",
                localPath = bookDir.absolutePath,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result.source)
        val coverFile = result.data as File
        assertEquals("folder.jpg", coverFile.name)
    }

    // ---- Book with embedded cover (extracted during scan) ----

    @Test
    fun `book with embedded cover prefers embedded over folder image`() {
        // Simulates: scanner extracted cover from ID3 tags -> covers/{bookId}.jpg
        val coversDir = tempFolder.newFolder("covers")
        val embeddedCover = File(coversDir, "embedded-book-1.jpg")
        embeddedCover.writeBytes(ByteArray(3072))

        // Book folder also has a cover.jpg
        val bookDir = tempFolder.newFolder("EmbeddedBook")
        File(bookDir, "cover.jpg").writeBytes(ByteArray(2048))

        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "embedded-book-1",
                localPath = bookDir.absolutePath,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.EMBEDDED, result.source)
        assertEquals(embeddedCover, result.data)
    }

    // ---- Online-only book (no local path) ----

    @Test
    fun `online-only book resolves from cover URL`() {
        val coversDir = tempFolder.newFolder("covers")

        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "online-book-1",
                localPath = null,
                coverUrl = "https://static.rutracker.org/covers/12345.jpg",
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.ONLINE_URL, result.source)
        assertEquals("https://static.rutracker.org/covers/12345.jpg", result.data)
    }

    // ---- Online-only book without cover URL ----

    @Test
    fun `online-only book without cover URL gets placeholder`() {
        val coversDir = tempFolder.newFolder("covers")

        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "online-book-2",
                localPath = null,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.PLACEHOLDER, result.source)
    }

    // ---- Book folder with only audio files (no cover image) ----

    @Test
    fun `book folder with no images falls back to online URL`() {
        // Simulates: /storage/audiobooks/NoCover/
        //   ├── 01.mp3
        //   └── 02.mp3
        val bookDir = tempFolder.newFolder("NoCover")
        File(bookDir, "01.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "02.mp3").writeBytes(ByteArray(1024))

        val coversDir = tempFolder.newFolder("covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "no-cover-1",
                localPath = bookDir.absolutePath,
                coverUrl = "https://example.com/cover.jpg",
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.ONLINE_URL, result.source)
    }

    // ---- Book folder with non-standard cover name ----

    @Test
    fun `book folder with front png cover is detected`() {
        // Simulates: /storage/audiobooks/CustomCover/
        //   ├── book.mp3
        //   └── front.png
        val bookDir = tempFolder.newFolder("CustomCover")
        File(bookDir, "book.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "front.png").writeBytes(ByteArray(2048))

        val coversDir = tempFolder.newFolder("covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "custom-cover-1",
                localPath = bookDir.absolutePath,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result.source)
    }

    // ---- Book folder with uppercase cover file ----

    @Test
    fun `book folder with uppercase Cover JPG is detected`() {
        // Simulates: /storage/audiobooks/UpperCover/
        //   ├── chapter.mp3
        //   └── Cover.JPG
        val bookDir = tempFolder.newFolder("UpperCover")
        File(bookDir, "chapter.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "Cover.JPG").writeBytes(ByteArray(2048))

        val coversDir = tempFolder.newFolder("covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "upper-cover-1",
                localPath = bookDir.absolutePath,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result.source)
    }

    // ---- Protocol-relative cover URL normalization ----

    @Test
    fun `protocol-relative cover URL is normalized to https`() {
        val coversDir = tempFolder.newFolder("covers")

        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "proto-rel-1",
                localPath = null,
                coverUrl = "//static.rutracker.org/covers/cover.jpg",
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.ONLINE_URL, result.source)
        assertEquals("https://static.rutracker.org/covers/cover.jpg", result.data)
    }

    // ---- Cover waterfall with mixed sources verifies priority ----

    @Test
    fun `cover waterfall correctly prioritizes embedded over folder over online`() {
        // Setup: embedded cover exists + folder cover exists + online URL provided
        val coversDir = tempFolder.newFolder("covers")
        val embeddedCover = File(coversDir, "priority-test-1.jpg")
        embeddedCover.writeBytes(ByteArray(1024))

        val bookDir = tempFolder.newFolder("PriorityTest")
        File(bookDir, "cover.jpg").writeBytes(ByteArray(2048))

        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "priority-test-1",
                localPath = bookDir.absolutePath,
                coverUrl = "https://example.com/cover.jpg",
                coversDir = coversDir,
            )

        // Embedded must win over folder and online
        assertEquals(CoverWaterfallPolicy.CoverSource.EMBEDDED, result.source)
        assertEquals(embeddedCover, result.data)
    }

    // ---- Deterministic placeholder for different books ----

    @Test
    fun `different books get different placeholder keys`() {
        val coversDir = tempFolder.newFolder("covers")

        val result1 = CoverWaterfallPolicy.resolveCover("book-alpha", null, null, coversDir)
        val result2 = CoverWaterfallPolicy.resolveCover("book-beta", null, null, coversDir)

        assert(result1.data != result2.data) {
            "Different books should get different placeholder keys"
        }
    }

    // ---- Book with mixed image and audio files selects cover by name ----

    @Test
    fun `book with screenshots and cover selects cover by common name`() {
        // Simulates: /storage/audiobooks/MixedContent/
        //   ├── 01.mp3
        //   ├── screenshot.png (large, but not a cover name)
        //   └── art.jpg (small, but is a cover name)
        val bookDir = tempFolder.newFolder("MixedContent")
        File(bookDir, "01.mp3").writeBytes(ByteArray(1024))
        File(bookDir, "screenshot.png").writeBytes(ByteArray(10000))
        File(bookDir, "art.jpg").writeBytes(ByteArray(100))

        val coversDir = tempFolder.newFolder("covers")
        val result =
            CoverWaterfallPolicy.resolveCover(
                bookId = "mixed-1",
                localPath = bookDir.absolutePath,
                coverUrl = null,
                coversDir = coversDir,
            )

        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result.source)
        // art.jpg should be selected despite being smaller than screenshot.png
        assertEquals("art.jpg", (result.data as File).name)
    }
}
