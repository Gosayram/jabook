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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataQualityPolicyTest {
    @Test
    fun `real title outscores garbage which outscores blank`() {
        val blank = MetadataQualityPolicy.qualityScore("   ")
        val garbage = MetadataQualityPolicy.qualityScore("<unknown>")
        val real = MetadataQualityPolicy.qualityScore("Мастер и Маргарита")
        assertTrue(real > garbage)
        assertTrue(garbage > blank)
        assertEquals(Int.MIN_VALUE, blank)
    }

    @Test
    fun `replacement character lowers score`() {
        val clean = MetadataQualityPolicy.qualityScore("Достоевский")
        val broken = MetadataQualityPolicy.qualityScore("Достоевски\uFFFD")
        assertTrue(broken < clean)
    }

    @Test
    fun `short clean title beats long corrupted one`() {
        assertTrue(MetadataQualityPolicy.qualityScore("Война") > MetadataQualityPolicy.qualityScore("Достоевски\uFFFDыыыыыыыыыыыыы"))
    }

    @Test
    fun `isLikelyCorrupted detects mojibake`() {
        // UTF-8 Cyrillic decoded as Latin-1: "До" -> "Ð" + combining bytes
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("ÐÐ¾ÑÑÐ¾ÐµÐ²ÑÐºÐ¸Ð¹"))
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("Ã®l Ã©tait"))
        assertFalse(MetadataQualityPolicy.isLikelyCorrupted("Гарри Поттер"))
    }

    @Test
    fun `isLikelyCorrupted detects placeholders and question marks`() {
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("<unknown>"))
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("what??"))
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("�"))
        assertFalse(MetadataQualityPolicy.isLikelyCorrupted("Where? Here."))
    }

    @Test
    fun `isLikelyCorrupted rejects url-like and control chars`() {
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("https://example.com/book.mp3"))
        assertTrue(MetadataQualityPolicy.isLikelyCorrupted("brokentitle"))
    }

    @Test
    fun `isLikelyTrackFileName detects common patterns`() {
        assertTrue(MetadataQualityPolicy.isLikelyTrackFileName("Track 1"))
        assertTrue(MetadataQualityPolicy.isLikelyTrackFileName("01.mp3"))
        assertTrue(MetadataQualityPolicy.isLikelyTrackFileName("Audio Track 12"))
        assertTrue(MetadataQualityPolicy.isLikelyTrackFileName("disc_03.flac"))
        assertFalse(MetadataQualityPolicy.isLikelyTrackFileName("Chapter 01 — The Beginning.mp3"))
        assertFalse(MetadataQualityPolicy.isLikelyTrackFileName("1984"))
    }

    @Test
    fun `selectBest prefers highest score and keeps first on ties`() {
        assertEquals("Мастер", MetadataQualityPolicy.selectBest("<unknown>", "Мастер", null))
        assertEquals("Мастер", MetadataQualityPolicy.selectBest(null, "Мастер", "Мастер"))
        assertNull(MetadataQualityPolicy.selectBest(null, "  ", ""))
    }

    @Test
    fun `selectBest trims and ignores blank candidates`() {
        assertEquals("Гарри Поттер", MetadataQualityPolicy.selectBest("  ", " Гарри Поттер "))
    }

    @Test
    fun `fallback policy delegates to corruption check`() {
        assertTrue(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter("Достоевски\uFFFD"))
        assertTrue(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter("<unknown>"))
        assertFalse(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter("Достоевский"))
        assertFalse(MediaStoreMetadataFallbackPolicy.hasReplacementCharacter(null))
    }
}
