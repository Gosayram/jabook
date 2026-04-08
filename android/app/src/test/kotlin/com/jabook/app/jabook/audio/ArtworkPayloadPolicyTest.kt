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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPayloadPolicyTest {
    @Test
    fun `null data is not allowed`() {
        assertFalse(ArtworkPayloadPolicy.isArtworkDataAllowed(null))
    }

    @Test
    fun `empty data is allowed`() {
        assertTrue(ArtworkPayloadPolicy.isArtworkDataAllowed(ByteArray(0)))
    }

    @Test
    fun `small data within limit is allowed`() {
        val smallData = ByteArray(1024) // 1 KB
        assertTrue(ArtworkPayloadPolicy.isArtworkDataAllowed(smallData))
    }

    @Test
    fun `data exactly at limit is allowed`() {
        val exactData = ByteArray(ArtworkPayloadPolicy.MAX_ARTWORK_DATA_BYTES)
        assertTrue(ArtworkPayloadPolicy.isArtworkDataAllowed(exactData))
    }

    @Test
    fun `data one byte over limit is rejected`() {
        val overData = ByteArray(ArtworkPayloadPolicy.MAX_ARTWORK_DATA_BYTES + 1)
        assertFalse(ArtworkPayloadPolicy.isArtworkDataAllowed(overData))
    }

    @Test
    fun `massive data is rejected`() {
        // 10 MB payload
        val hugeData = ByteArray(10 * 1024 * 1024)
        assertFalse(ArtworkPayloadPolicy.isArtworkDataAllowed(hugeData))
    }

    @Test
    fun `sanitize returns original data when within limit`() {
        val data = ByteArray(512)
        val result = ArtworkPayloadPolicy.sanitizeArtworkData(data)
        assertNotNull(result)
        assertEquals(data.size, result!!.size)
    }

    @Test
    fun `sanitize returns null for null input`() {
        assertNull(ArtworkPayloadPolicy.sanitizeArtworkData(null))
    }

    @Test
    fun `sanitize returns null for oversized input`() {
        val overData = ByteArray(ArtworkPayloadPolicy.MAX_ARTWORK_DATA_BYTES + 100)
        assertNull(ArtworkPayloadPolicy.sanitizeArtworkData(overData))
    }

    @Test
    fun `max artwork bytes is 2MB`() {
        assertEquals(2 * 1024 * 1024, ArtworkPayloadPolicy.MAX_ARTWORK_DATA_BYTES)
    }
}
