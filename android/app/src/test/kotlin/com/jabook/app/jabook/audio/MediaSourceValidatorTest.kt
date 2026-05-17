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

package com.jabook.app.jabook.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [MediaSourceValidator].
 *
 * P-11: Validates magic-bytes detection and file integrity checks.
 */
class MediaSourceValidatorTest {
    // --- isKnownAudioHeader ---

    @Test
    fun `MP3 sync word 0xFF 0xFB is recognised`() {
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00, 0x00)
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `MP3 sync word 0xFF 0xF3 is recognised`() {
        val header = byteArrayOf(0xFF.toByte(), 0xF3.toByte(), 0x00, 0x00)
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `MP3 with ID3v2 tag is recognised`() {
        val header =
            byteArrayOf(
                'I'.code.toByte(),
                'D'.code.toByte(),
                '3'.code.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            )
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `M4B ftyp box is recognised`() {
        // MP4/M4B: 4 bytes size + "ftyp" at offset 4
        val header =
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x20,
                'f'.code.toByte(),
                't'.code.toByte(),
                'y'.code.toByte(),
                'p'.code.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
            )
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `OGG header is recognised`() {
        val header =
            byteArrayOf(
                'O'.code.toByte(),
                'g'.code.toByte(),
                'g'.code.toByte(),
                'S'.code.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
            )
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `FLAC header is recognised`() {
        val header =
            byteArrayOf(
                'f'.code.toByte(),
                'L'.code.toByte(),
                'a'.code.toByte(),
                'C'.code.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
            )
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `WAV RIFF header is recognised`() {
        val header =
            byteArrayOf(
                'R'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                'F'.code.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
            )
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `WMA ASF header is recognised`() {
        val header = byteArrayOf(0x30.toByte(), 0x26.toByte(), 0xB2.toByte(), 0x75.toByte())
        assertTrue(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `unknown header is rejected`() {
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertFalse(MediaSourceValidator.isKnownAudioHeader(header))
    }

    @Test
    fun `header shorter than 4 bytes is rejected`() {
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte())
        assertFalse(MediaSourceValidator.isKnownAudioHeader(header))
    }

    // --- isValid (with temp files) ---

    @Test
    fun `valid MP3 file passes validation`() {
        val tempFile = File.createTempFile("test_audio_", ".mp3")
        try {
            // Write ID3v2 header + enough padding
            tempFile.outputStream().use { os ->
                os.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
                os.write(ByteArray(1021)) // pad to > 1024 bytes
            }
            assertTrue(MediaSourceValidator.isValid(tempFile.absolutePath))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `file smaller than 1KB is rejected`() {
        val tempFile = File.createTempFile("test_small_", ".mp3")
        try {
            tempFile.outputStream().use { os ->
                os.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
                os.write(ByteArray(10)) // total = 13 bytes
            }
            assertFalse(MediaSourceValidator.isValid(tempFile.absolutePath))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `non-existent file is rejected`() {
        assertFalse(MediaSourceValidator.isValid("/nonexistent/path/to/file.mp3"))
    }

    @Test
    fun `file with unknown content is rejected`() {
        val tempFile = File.createTempFile("test_corrupt_", ".mp3")
        try {
            tempFile.outputStream().use { os ->
                os.write(ByteArray(2048)) // all zeros — no valid audio header
            }
            assertFalse(MediaSourceValidator.isValid(tempFile.absolutePath))
        } finally {
            tempFile.delete()
        }
    }

    // --- validate (batch) ---

    @Test
    fun `validate partitions into valid invalid and corrupted`() {
        val validFile = File.createTempFile("test_valid_", ".mp3")
        val corruptFile = File.createTempFile("test_corrupt_", ".mp3")
        try {
            // Valid: ID3 header + enough padding
            validFile.outputStream().use { os ->
                os.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()))
                os.write(ByteArray(1021))
            }
            // Corrupt: all zeros but > 1KB
            corruptFile.outputStream().use { os ->
                os.write(ByteArray(2048))
            }

            val paths =
                listOf(
                    validFile.absolutePath,
                    "/nonexistent/file.mp3",
                    corruptFile.absolutePath,
                )

            val result = runBlocking { MediaSourceValidator.validate(paths) }

            assertEquals(1, result.validPaths.size)
            assertEquals(1, result.invalidPaths.size)
            assertEquals(1, result.corruptedPaths.size)
            assertTrue(result.validPaths.contains(validFile.absolutePath))
            assertTrue(result.invalidPaths.contains("/nonexistent/file.mp3"))
            assertTrue(result.corruptedPaths.contains(corruptFile.absolutePath))
        } finally {
            validFile.delete()
            corruptFile.delete()
        }
    }
}
