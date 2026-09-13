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

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [SourceAttemptCache] (failed-source memory with ordered
 * sibling fallback and LRU cap).
 */
@RunWith(RobolectricTestRunner::class)
class SourceAttemptCacheTest {
    private fun uri(s: String): Uri = Uri.parse("https://cdn.example.com/$s")

    @Test
    fun `nextCandidate returns first candidate when nothing failed`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3"), uri("b.mp3")))
        assertEquals(uri("a.mp3"), cache.nextCandidate("ch1"))
    }

    @Test
    fun `markFailed skips to next sibling`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3"), uri("b.mp3"), uri("c.mp3")))
        cache.markFailed("ch1", uri("a.mp3"))
        assertEquals(uri("b.mp3"), cache.nextCandidate("ch1"))
        cache.markFailed("ch1", uri("b.mp3"))
        assertEquals(uri("c.mp3"), cache.nextCandidate("ch1"))
    }

    @Test
    fun `all candidates exhausted returns null`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3"), uri("b.mp3")))
        cache.markFailed("ch1", uri("a.mp3"))
        cache.markFailed("ch1", uri("b.mp3"))
        assertNull(cache.nextCandidate("ch1"))
    }

    @Test
    fun `nextCandidate unknown mediaId returns null`() {
        val cache = SourceAttemptCache()
        assertNull(cache.nextCandidate("missing"))
    }

    @Test
    fun `markFailed for unknown mediaId is a no-op`() {
        val cache = SourceAttemptCache()
        cache.markFailed("missing", uri("a.mp3"))
        assertNull(cache.nextCandidate("missing"))
    }

    @Test
    fun `markFailed for unknown URI of known mediaId keeps candidates`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3")))
        cache.markFailed("ch1", uri("not-a-candidate.mp3"))
        assertEquals(uri("a.mp3"), cache.nextCandidate("ch1"))
    }

    @Test
    fun `rememberCandidates resets prior failed state`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3"), uri("b.mp3")))
        cache.markFailed("ch1", uri("a.mp3"))
        cache.markFailed("ch1", uri("b.mp3"))
        assertNull(cache.nextCandidate("ch1"))
        // Re-resolution produced a fresh candidate list → memory cleared.
        cache.rememberCandidates("ch1", listOf(uri("c.mp3")))
        assertEquals(uri("c.mp3"), cache.nextCandidate("ch1"))
    }

    @Test
    fun `duplicate candidates are de-duplicated`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3"), uri("a.mp3")))
        cache.markFailed("ch1", uri("a.mp3"))
        assertNull(cache.nextCandidate("ch1"))
    }

    @Test
    fun `LRU cap evicts oldest mediaId beyond 32`() {
        val cache = SourceAttemptCache()
        for (i in 0 until 32) {
            cache.rememberCandidates("ch$i", listOf(uri("x$i.mp3")))
        }
        // Touch ch0 so ch1 becomes the LRU victim.
        assertEquals(uri("x0.mp3"), cache.nextCandidate("ch0"))
        cache.rememberCandidates("ch32", listOf(uri("x32.mp3")))
        assertNull(cache.nextCandidate("ch1"))
        assertEquals(uri("x0.mp3"), cache.nextCandidate("ch0"))
        assertEquals(uri("x32.mp3"), cache.nextCandidate("ch32"))
    }

    @Test
    fun `clear drops all state`() {
        val cache = SourceAttemptCache()
        cache.rememberCandidates("ch1", listOf(uri("a.mp3")))
        cache.clear()
        assertNull(cache.nextCandidate("ch1"))
    }
}
