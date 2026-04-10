package com.jabook.app.jabook.compose.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetUriValidationPolicyTest {
    @Test
    fun `accepts valid hex btih magnet uri`() {
        val raw = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=test"

        assertTrue(MagnetUriValidationPolicy.isValidMagnetUri(raw))
        assertEquals(
            "0123456789abcdef0123456789abcdef01234567",
            MagnetUriValidationPolicy.extractInfoHash(raw),
        )
    }

    @Test
    fun `rejects magnet uri without xt`() {
        val raw = "magnet:?dn=test-only"

        assertFalse(MagnetUriValidationPolicy.isValidMagnetUri(raw))
        assertNull(MagnetUriValidationPolicy.extractInfoHash(raw))
    }

    @Test
    fun `rejects non magnet strings`() {
        assertFalse(MagnetUriValidationPolicy.isValidMagnetUri("https://example.org/file.torrent"))
        assertFalse(MagnetUriValidationPolicy.isValidMagnetUri("btih:123"))
    }

    @Test
    fun `accepts raw 40-char hex hash for compatibility`() {
        val raw = "ABCDEF0123456789ABCDEF0123456789ABCDEF01"

        assertTrue(MagnetUriValidationPolicy.isValidMagnetUri(raw))
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef01",
            MagnetUriValidationPolicy.extractInfoHash(raw),
        )
    }
}
