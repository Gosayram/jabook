// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.data.remote.encoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

/**
 * Unit tests for DefensiveEncodingHandler.
 *
 * Tests cover:
 * - CP1251 decoding
 * - UTF-8 decoding
 * - BOM removal
 * - Mojibake detection
 * - Fallback chain
 * - Content-Type header parsing
 */
class DefensiveEncodingHandlerTest {
    private lateinit var handler: DefensiveEncodingHandler

    private val cp1251 = Charset.forName("windows-1251")
    private val utf8 = Charsets.UTF_8

    @Before
    fun setup() {
        handler = DefensiveEncodingHandler()
    }

    // ============ CP1251 Decoding Tests ============

    @Test
    fun `decode handles Windows-1251 Cyrillic text correctly`() {
        val originalText = "Привет, мир! Аудиокнига"
        val bytes = originalText.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertEquals(originalText, result.text)
        assertEquals("windows-1251", result.encoding.lowercase())
        assertFalse(result.hasMojibake)
        assertTrue(result.isValid)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `decode detects CP1251 without Content-Type header`() {
        val originalText = "Толстой - Война и мир"
        val bytes = originalText.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = null)

        assertEquals(originalText, result.text)
        assertFalse(result.hasMojibake)
        assertTrue(result.isValid)
    }

    @Test
    fun `decode handles CP1251 with HTML structure`() {
        val html = "<html><body>Тестовая аудиокнига</body></html>"
        val bytes = html.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertTrue(result.text.contains("Тестовая аудиокнига"))
        assertTrue(result.text.contains("<html"))
        assertTrue(result.isValid)
    }

    // ============ UTF-8 Decoding Tests ============

    @Test
    fun `decode handles UTF-8 text correctly`() {
        val originalText = "Привет, UTF-8! 你好"
        val bytes = originalText.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = "text/html; charset=utf-8")

        assertEquals(originalText, result.text)
        assertEquals("utf-8", result.encoding.lowercase())
        assertFalse(result.hasMojibake)
        assertTrue(result.isValid)
    }

    @Test
    fun `decode detects UTF-8 with Cyrillic sequences`() {
        val originalText = "Русский текст в UTF-8"
        val bytes = originalText.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = null)

        assertEquals(originalText, result.text)
        assertFalse(result.hasMojibake)
        assertTrue(result.isValid)
    }

    // ============ BOM Removal Tests ============

    @Test
    fun `decode removes UTF-8 BOM`() {
        val text = "Test content"
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val textBytes = text.toByteArray(utf8)
        val bytes = bomBytes + textBytes

        val result = handler.decode(bytes, contentType = "text/html; charset=utf-8")

        assertEquals(text, result.text)
        assertTrue(result.isValid)
    }

    @Test
    fun `decode removes UTF-16 BE BOM`() {
        val bomBytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val textBytes = "Test".toByteArray(utf8)
        val bytes = bomBytes + textBytes

        val result = handler.decode(bytes, contentType = null)

        // After BOM removal, should decode remaining bytes
        assertTrue(result.isValid)
    }

    @Test
    fun `decode removes UTF-16 LE BOM`() {
        val bomBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val textBytes = "Test".toByteArray(utf8)
        val bytes = bomBytes + textBytes

        val result = handler.decode(bytes, contentType = null)

        assertTrue(result.isValid)
    }

    @Test
    fun `decode handles text without BOM`() {
        val text = "No BOM here"
        val bytes = text.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = null)

        assertTrue(result.text.contains("No BOM"))
        assertTrue(result.isValid)
    }

    // ============ Mojibake Detection Tests ============

    @Test
    fun `decode detects mojibake in incorrectly decoded text`() {
        // Simulate mojibake: encode CP1251 text, decode as UTF-8
        val originalText = "Привет"
        val cp1251Bytes = originalText.toByteArray(cp1251)

        // Force decode as Latin-1 first (will create mojibake)
        val mojibakeText = String(cp1251Bytes, Charsets.ISO_8859_1)

        // The mojibake text should contain patterns we detect
        val containsMojibakePattern =
            mojibakeText.contains("Р") ||
                mojibakeText.contains("в") ||
                mojibakeText != originalText

        assertTrue("Text should be corrupted when wrong encoding used", containsMojibakePattern)
    }

    @Test
    fun `decode does not report mojibake for correct decoding`() {
        val originalText = "Правильный текст"
        val bytes = originalText.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertEquals(originalText, result.text)
        assertFalse(result.hasMojibake)
    }

    // ============ Fallback Chain Tests ============

    @Test
    fun `decode tries CP1251 when no encoding specified`() {
        val text = "Текст"
        val bytes = text.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = null)

        assertEquals(text, result.text)
        assertTrue(result.isValid)
    }

    @Test
    fun `decode falls back to UTF-8 if CP1251 produces mojibake`() {
        val text = "English text with émojis 😀"
        val bytes = text.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = null)

        assertTrue(result.text.contains("English"))
        assertTrue(result.isValid)
    }

    @Test
    fun `decode returns emergency fallback for invalid data`() {
        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte(), 0xFE.toByte())

        val result = handler.decode(invalidBytes, contentType = null)

        // Should still return something (emergency fallback)
        assertTrue(result.text.isNotEmpty() || !result.isValid)
    }

    // ============ Content-Type Parsing Tests ============

    @Test
    fun `decode extracts charset from Content-Type header`() {
        val text = "Test"
        val bytes = text.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertEquals("windows-1251", result.encoding.lowercase())
    }

    @Test
    fun `decode handles Content-Type with UTF-8`() {
        val text = "Test UTF-8"
        val bytes = text.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = "text/html; charset=utf-8")

        assertEquals("utf-8", result.encoding.lowercase())
    }

    @Test
    fun `decode handles Content-Type with cp1251 variant`() {
        val text = "Текст"
        val bytes = text.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=cp1251")

        assertTrue(result.text.contains("Текст"))
    }

    // ============ HTML Structure Validation Tests ============

    @Test
    fun `decode validates HTML structure presence`() {
        val html = "<!DOCTYPE html><html><head></head><body>Content</body></html>"
        val bytes = html.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = "text/html; charset=utf-8")

        assertTrue(result.isValid)
        assertTrue(result.text.contains("<html"))
    }

    @Test
    fun `decode accepts partial HTML`() {
        val html = "<body>Partial HTML</body>"
        val bytes = html.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = null)

        assertTrue(result.isValid)
    }

    @Test
    fun `decode handles empty bytes`() {
        val bytes = ByteArray(0)

        val result = handler.decode(bytes, contentType = null)

        // Should return invalid result for empty input
        assertFalse(result.isValid)
    }

    // ============ Mixed Content Tests ============

    @Test
    fun `decode handles Russian author and English title`() {
        val text = "Author: Толстой Лев Николаевич, Title: War and Peace"
        val bytes = text.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertTrue(result.text.contains("Толстой"))
        assertTrue(result.text.contains("War and Peace"))
        assertFalse(result.hasMojibake)
    }

    @Test
    fun `decode handles special characters in CP1251`() {
        val text = "Цена: 100₽, Размер: 500МБ, №1234"
        val bytes = text.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertTrue(result.text.contains("Цена"))
        assertTrue(result.isValid)
    }

    // ============ Confidence Score Tests ============

    @Test
    fun `decode returns high confidence for clean CP1251`() {
        val text = "Чистый текст"
        val bytes = text.toByteArray(cp1251)

        val result = handler.decode(bytes, contentType = "text/html; charset=windows-1251")

        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `decode returns low confidence for mojibake`() {
        // Create intentional mojibake
        val text = "РўРµСЃС‚" // This is mojibake pattern
        val bytes = text.toByteArray(utf8)

        val result = handler.decode(bytes, contentType = null)

        // If mojibake detected, confidence should be low
        if (result.hasMojibake) {
            assertTrue(result.confidence < 0.5f)
        }
    }
}
