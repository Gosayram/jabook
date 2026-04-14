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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTypeValidationPolicyTest {
    //region Magic byte detection

    @Test
    fun `detectFormat recognizes MP3 with ID3 tag`() {
        val header = byteArrayOf(0x49, 0x44, 0x33) + ByteArray(13) // "ID3" + padding
        assertEquals("mp3", MimeTypeValidationPolicy.detectFormat(header))
    }

    @Test
    fun `detectFormat recognizes MP3 sync word 0xFF 0xFB`() {
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte()) + ByteArray(14)
        assertEquals("mp3", MimeTypeValidationPolicy.detectFormat(header))
    }

    @Test
    fun `detectFormat recognizes FLAC`() {
        val header = byteArrayOf(0x66, 0x4C, 0x61, 0x43) + ByteArray(12) // "fLaC"
        assertEquals("flac", MimeTypeValidationPolicy.detectFormat(header))
    }

    @Test
    fun `detectFormat recognizes OGG`() {
        val header = byteArrayOf(0x4F, 0x67, 0x67, 0x53) + ByteArray(12) // "OggS"
        assertEquals("ogg", MimeTypeValidationPolicy.detectFormat(header))
    }

    @Test
    fun `detectFormat recognizes WAV RIFF`() {
        val header = byteArrayOf(0x52, 0x49, 0x46, 0x46) + ByteArray(12) // "RIFF"
        assertEquals("wav", MimeTypeValidationPolicy.detectFormat(header))
    }

    @Test
    fun `detectFormat returns null for unknown bytes`() {
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x00) + ByteArray(12)
        assertNull(MimeTypeValidationPolicy.detectFormat(header))
    }

    @Test
    fun `detectFormat returns null for empty bytes`() {
        assertNull(MimeTypeValidationPolicy.detectFormat(ByteArray(0)))
    }

    //endregion

    //region Full validation — happy path

    @Test
    fun `validate accepts valid MP3 file`() {
        val header = byteArrayOf(0x49, 0x44, 0x33) + ByteArray(13) // ID3
        val result = MimeTypeValidationPolicy.validate("track.mp3", header)
        assertTrue(result.isValid)
        assertEquals("audio/mpeg", result.detectedMimeType)
        assertEquals("audio/mpeg", result.extensionMimeType)
        assertNull(result.rejectionReason)
    }

    @Test
    fun `validate accepts valid FLAC file`() {
        val header = byteArrayOf(0x66, 0x4C, 0x61, 0x43) + ByteArray(12) // fLaC
        val result = MimeTypeValidationPolicy.validate("audio.flac", header)
        assertTrue(result.isValid)
        assertEquals("audio/flac", result.detectedMimeType)
    }

    @Test
    fun `validate accepts valid OGG file`() {
        val header = byteArrayOf(0x4F, 0x67, 0x67, 0x53) + ByteArray(12) // OggS
        val result = MimeTypeValidationPolicy.validate("song.ogg", header)
        assertTrue(result.isValid)
        assertEquals("audio/ogg", result.detectedMimeType)
    }

    //endregion

    //region Extension mismatch detection

    @Test
    fun `validate rejects file with mismatched extension and content`() {
        // FLAC content with .mp3 extension
        val header = byteArrayOf(0x66, 0x4C, 0x61, 0x43) + ByteArray(12) // fLaC
        val result = MimeTypeValidationPolicy.validate("track.mp3", header)
        assertFalse(result.isValid)
        assertNotNull(result.rejectionReason)
        assertTrue(result.rejectionReason!!.contains("Extension suggests"))
    }

    //endregion

    //region Unsupported extension

    @Test
    fun `validate rejects unsupported extension txt`() {
        val header = ByteArray(16)
        val result = MimeTypeValidationPolicy.validate("readme.txt", header)
        assertFalse(result.isValid)
        assertTrue(result.rejectionReason!!.contains("Unsupported file extension"))
    }

    @Test
    fun `validate rejects unsupported extension exe`() {
        val header = ByteArray(16)
        val result = MimeTypeValidationPolicy.validate("malware.exe", header)
        assertFalse(result.isValid)
    }

    //endregion

    //region Unknown magic bytes

    @Test
    fun `validate rejects unknown magic bytes with supported extension`() {
        val header = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()) + ByteArray(12)
        val result = MimeTypeValidationPolicy.validate("track.mp3", header)
        assertFalse(result.isValid)
        assertTrue(result.rejectionReason!!.contains("magic bytes"))
    }

    //endregion

    //region Extension support

    @Test
    fun `isExtensionSupported returns true for mp3`() {
        assertTrue(MimeTypeValidationPolicy.isExtensionSupported("song.mp3"))
    }

    @Test
    fun `isExtensionSupported returns true for m4b`() {
        assertTrue(MimeTypeValidationPolicy.isExtensionSupported("book.m4b"))
    }

    @Test
    fun `isExtensionSupported returns false for txt`() {
        assertFalse(MimeTypeValidationPolicy.isExtensionSupported("readme.txt"))
    }

    @Test
    fun `isExtensionSupported returns false for no extension`() {
        assertFalse(MimeTypeValidationPolicy.isExtensionSupported("noext"))
    }

    @Test
    fun `isExtensionSupported is case insensitive`() {
        assertTrue(MimeTypeValidationPolicy.isExtensionSupported("song.MP3"))
    }

    //endregion

    //region MIME type lookup

    @Test
    fun `getMimeTypeForExtension returns correct MIME for mp3`() {
        assertEquals("audio/mpeg", MimeTypeValidationPolicy.getMimeTypeForExtension("mp3"))
    }

    @Test
    fun `getMimeTypeForExtension returns null for unknown extension`() {
        assertNull(MimeTypeValidationPolicy.getMimeTypeForExtension("xyz"))
    }

    //endregion

    //region M4B cross-compatibility

    @Test
    fun `m4b extension with mp4 content is accepted`() {
        // Build header: 8 bytes of size + "ftyp" at offset 4
        val header = ByteArray(16)
        header[4] = 0x66 // 'f'
        header[5] = 0x74 // 't'
        header[6] = 0x79 // 'y'
        header[7] = 0x70 // 'p'
        val result = MimeTypeValidationPolicy.validate("book.m4b", header)
        assertTrue(result.isValid)
    }

    //endregion

    //region Empty header

    @Test
    fun `validate accepts supported extension with empty header (no magic check)`() {
        val result = MimeTypeValidationPolicy.validate("track.mp3", ByteArray(0))
        assertTrue(result.isValid)
        assertNull(result.detectedMimeType)
        assertEquals("audio/mpeg", result.extensionMimeType)
    }

    //endregion
}
