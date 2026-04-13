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
