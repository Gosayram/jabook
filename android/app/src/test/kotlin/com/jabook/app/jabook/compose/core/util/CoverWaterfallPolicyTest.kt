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

package com.jabook.app.jabook.compose.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [CoverWaterfallPolicy].
 *
 * Verifies each level of the cover waterfall independently and the full chain.
 */
class CoverWaterfallPolicyTest {

    private val tempFolder = TemporaryFolder()

    @Before
    fun setup() {
        tempFolder.create()
    }

    @After
    fun teardown() {
        tempFolder.delete()
    }

    // ---- Level 1: Embedded covers ----

    @Test
    fun `resolveEmbedded returns result when cover file exists`() {
        val coversDir = tempFolder.newFolder("covers")
        val coverFile = File(coversDir, "book-42.jpg")
        coverFile.writeBytes(ByteArray(1024))

        val result = CoverWaterfallPolicy.resolveEmbedded("book-42", coversDir)

        assertNotNull(result)
        assertEquals(CoverWaterfallPolicy.CoverSource.EMBEDDED, result!!.source)
        assertEquals(coverFile, result.data)
    }

    @Test
    fun `resolveEmbedded returns null when cover file does not exist`() {
        val coversDir = tempFolder.newFolder("covers")

        val result = CoverWaterfallPolicy.resolveEmbedded("nonexistent", coversDir)

        assertNull(result)
    }

    @Test
    fun `resolveEmbedded returns null when cover file is empty`() {
        val coversDir = tempFolder.newFolder("covers")
        File(coversDir, "book-42.jpg").writeBytes(ByteArray(0))

        val result = CoverWaterfallPolicy.resolveEmbedded("book-42", coversDir)

        assertNull(result)
    }

    // ---- Level 2: Folder images ----

    @Test
    fun `resolveFolderImage returns result for exact match cover jpg`() {
        val bookDir = tempFolder.newFolder("book")
        val coverFile = File(bookDir, "cover.jpg")
        coverFile.writeBytes(ByteArray(512))

        val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

        assertNotNull(result)
        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result!!.source)
        assertEquals(coverFile, result.data)
    }

    @Test
    fun `resolveFolderImage returns result for case-insensitive match`() {
        val bookDir = tempFolder.newFolder("book")
        val coverFile = File(bookDir, "Cover.JPG")
        coverFile.writeBytes(ByteArray(512))

        val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

        assertNotNull(result)
        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result!!.source)
    }

    @Test
    fun `resolveFolderImage returns largest image as fallback`() {
        val bookDir = tempFolder.newFolder("book")
        val smallFile = File(bookDir, "track1.jpg")
        smallFile.writeBytes(ByteArray(100))
        val largeFile = File(bookDir, "photo.png")
        largeFile.writeBytes(ByteArray(5000))

        val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

        assertNotNull(result)
        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result!!.source)
        assertEquals(largeFile, result!!.data)
    }

    @Test
    fun `resolveFolderImage prefers common name over largest image`() {
        val bookDir = tempFolder.newFolder("book")
        val folderFile = File(bookDir, "folder.jpg")
        folderFile.writeBytes(ByteArray(100))
        val largeFile = File(bookDir, "screenshot.png")
        largeFile.writeBytes(ByteArray(5000))

        val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

        assertNotNull(result)
        assertEquals(folderFile, result!!.data)
    }

    @Test
    fun `resolveFolderImage returns null for empty directory`() {
        val bookDir = tempFolder.newFolder("book")

        val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

        assertNull(result)
    }

    @Test
    fun `resolveFolderImage returns null for non-existent directory`() {
        val result = CoverWaterfallPolicy.resolveFolderImage("/nonexistent/path")

        assertNull(result)
    }

    // ---- Level 3: Online URL ----

    @Test
    fun `resolveOnlineUrl returns result for absolute https URL`() {
        val result = CoverWaterfallPolicy.resolveOnlineUrl("https://example.com/cover.jpg")

        assertNotNull(result)
        assertEquals(CoverWaterfallPolicy.CoverSource.ONLINE_URL, result!!.source)
        assertEquals("https://example.com/cover.jpg", result.data)
    }

    @Test
    fun `resolveOnlineUrl normalizes protocol-relative URL`() {
        val result = CoverWaterfallPolicy.resolveOnlineUrl("//example.com/cover.jpg")

        assertNotNull(result)
        assertEquals("https://example.com/cover.jpg", result!!.data)
    }

    @Test
    fun `resolveOnlineUrl returns null for blank URL`() {
        assertNull(CoverWaterfallPolicy.resolveOnlineUrl(null))
        assertNull(CoverWaterfallPolicy.resolveOnlineUrl(""))
        assertNull(CoverWaterfallPolicy.resolveOnlineUrl("   "))
    }

    // ---- Level 4: Placeholder ----

    @Test
    fun `resolvePlaceholder always returns result`() {
        val result = CoverWaterfallPolicy.resolvePlaceholder("book-42")

        assertEquals(CoverWaterfallPolicy.CoverSource.PLACEHOLDER, result.source)
        assertNotNull(result.data)
    }

    @Test
    fun `generatePlaceholderKey is deterministic`() {
        val key1 = CoverWaterfallPolicy.generatePlaceholderKey("book-42")
        val key2 = CoverWaterfallPolicy.generatePlaceholderKey("book-42")

        assertEquals(key1, key2)
    }

    @Test
    fun `generatePlaceholderKey differs for different book IDs`() {
        val key1 = CoverWaterfallPolicy.generatePlaceholderKey("book-1")
        val key2 = CoverWaterfallPolicy.generatePlaceholderKey("book-2")

        assert(key1 != key2) { "Placeholder keys should differ for different book IDs" }
    }

    @Test
    fun `placeholderColorIndex returns value in range 0-15`() {
        for (id in 0..100) {
            val key = CoverWaterfallPolicy.generatePlaceholderKey("book-$id")
            val index = CoverWaterfallPolicy.placeholderColorIndex(key)
            assert(index in 0..15) { "Color index $index out of range for book-$id" }
        }
    }

    // ---- Full chain: resolveCover ----

    @Test
    fun `resolveCover prefers embedded over folder image`() {
        val coversDir = tempFolder.newFolder("covers")
        val embeddedCover = File(coversDir, "book-42.jpg")
        embeddedCover.writeBytes(ByteArray(1024))

        val bookDir = tempFolder.newFolder("book")
        File(bookDir, "cover.jpg").writeBytes(ByteArray(2048))

        val result = CoverWaterfallPolicy.resolveCover(
            "book-42",
            bookDir.absolutePath,
            null,
            coversDir,
        )

        assertEquals(CoverWaterfallPolicy.CoverSource.EMBEDDED, result.source)
        assertEquals(embeddedCover, result.data)
    }

    @Test
    fun `resolveCover prefers folder image over online URL`() {
        val coversDir = tempFolder.newFolder("covers")
        val bookDir = tempFolder.newFolder("book")
        File(bookDir, "cover.jpg").writeBytes(ByteArray(512))

        val result = CoverWaterfallPolicy.resolveCover(
            "book-99",
            bookDir.absolutePath,
            "https://example.com/cover.jpg",
            coversDir,
        )

        assertEquals(CoverWaterfallPolicy.CoverSource.FOLDER_IMAGE, result.source)
    }

    @Test
    fun `resolveCover falls back to online URL when no local covers`() {
        val coversDir = tempFolder.newFolder("covers")
        val bookDir = tempFolder.newFolder("book")

        val result = CoverWaterfallPolicy.resolveCover(
            "book-99",
            bookDir.absolutePath,
            "https://example.com/cover.jpg",
            coversDir,
        )

        assertEquals(CoverWaterfallPolicy.CoverSource.ONLINE_URL, result.source)
        assertEquals("https://example.com/cover.jpg", result.data)
    }

    @Test
    fun `resolveCover falls back to placeholder when nothing available`() {
        val coversDir = tempFolder.newFolder("covers")
        val bookDir = tempFolder.newFolder("book")

        val result = CoverWaterfallPolicy.resolveCover(
            "book-99",
            bookDir.absolutePath,
            null,
            coversDir,
        )

        assertEquals(CoverWaterfallPolicy.CoverSource.PLACEHOLDER, result.source)
    }

    @Test
    fun `resolveCover with null localPath skips folder level`() {
        val coversDir = tempFolder.newFolder("covers")

        val result = CoverWaterfallPolicy.resolveCover(
            "book-99",
            null,
            "https://example.com/cover.jpg",
            coversDir,
        )

        assertEquals(CoverWaterfallPolicy.CoverSource.ONLINE_URL, result.source)
    }

    @Test
    fun `resolveCover online-only book falls to placeholder`() {
        val coversDir = tempFolder.newFolder("covers")

        val result = CoverWaterfallPolicy.resolveCover("book-99", null, null, coversDir)

        assertEquals(CoverWaterfallPolicy.CoverSource.PLACEHOLDER, result.source)
    }

    // ---- Common cover name detection ----

    @Test
    fun `all common cover names are detected`() {
        for (name in CoverWaterfallPolicy.COMMON_COVER_NAMES) {
            val bookDir = tempFolder.newFolder("book_$name")
            val coverFile = File(bookDir, "$name.jpg")
            coverFile.writeBytes(ByteArray(100))

            val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

            assertNotNull("Expected cover for '$name.jpg' to be found", result)
            assertEquals(coverFile, result!!.data)
        }
    }

    @Test
    fun `all supported extensions are detected`() {
        for (ext in CoverWaterfallPolicy.COVER_EXTENSIONS) {
            val bookDir = tempFolder.newFolder("book_$ext")
            val coverFile = File(bookDir, "cover.$ext")
            coverFile.writeBytes(ByteArray(100))

            val result = CoverWaterfallPolicy.resolveFolderImage(bookDir.absolutePath)

            assertNotNull("Expected cover for 'cover.$ext' to be found", result)
        }
    }
}