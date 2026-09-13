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

package com.jabook.app.jabook.compose.data.local.parser

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression tests for the OOM fix: MetadataCache's LruCache must be sized in
 * BYTES (via sizeOf on coverArt), not by entry count — otherwise large cover
 * art ByteArrays accumulate unbounded during big library scans.
 */
@RunWith(RobolectricTestRunner::class)
class MetadataCacheTest {
    // ── helpers ────────────────────────────────────────────────────────

    private class FakeParser : AudioMetadataParser {
        val parseCounts = mutableMapOf<String, Int>()

        var metadataForPath: (String) -> AudioMetadata? = { null }

        override suspend fun parseMetadata(filePath: String): AudioMetadata? {
            parseCounts[filePath] = (parseCounts[filePath] ?: 0) + 1
            return metadataForPath(filePath)
        }
    }

    private fun tempFile(name: String): File =
        File.createTempFile("metadata_cache_test_$name", ".mp3").apply {
            deleteOnExit()
            writeBytes(ByteArray(1))
        }

    private fun metadata(
        title: String?,
        coverArt: ByteArray?,
    ): AudioMetadata =
        AudioMetadata(
            title = title,
            artist = "artist",
            album = "album",
            albumArtist = "albumArtist",
            duration = 1234L,
            genre = "genre",
            year = "2026",
            trackNumber = 1,
            coverArt = coverArt,
        )

    private fun newCache() = MetadataCache(NoOpLoggerFactory)

    private val oneMb = ByteArray(1024 * 1024)

    /** Max byte budget used by MetadataCache internally (32MB). */
    private val maxBytes = 32 * 1024 * 1024

    // ── regression: byte-sized eviction ────────────────────────────────

    @Test
    fun cacheStaysBoundedWhenCoverArtEntriesExceedByteBudget() =
        runTest {
            val cache = newCache()
            val parser = FakeParser()
            // 40 entries × 1MB = 40MB total > 32MB budget → must evict.
            val files =
                (0 until 40).map { i ->
                    val f = tempFile(i.toString())
                    val art = oneMb.clone()
                    parser.metadataForPath = { path -> if (path == f.absolutePath) metadata("t$i", art) else null }
                    cache.getOrParse(f, parser)
                    f
                }
            // Reset so lookups below resolve against any file.
            parser.metadataForPath = { path ->
                val idx = files.indexOfFirst { it.absolutePath == path }
                if (idx >= 0) metadata("t$idx", oneMb) else null
            }

            val stats = cache.getCacheStats()
            assertTrue(
                "expected evictions once byte budget exceeded",
                stats.evictionCount > 0,
            )
            assertTrue(
                "retained bytes (${stats.size}) must stay within budget ($maxBytes)",
                stats.size <= maxBytes,
            )
            // Earliest entry was evicted → next lookup is a MISS (re-parse).
            val parsesBefore = parser.parseCounts[files[0].absolutePath] ?: 0
            cache.getOrParse(files[0], parser)
            assertEquals(
                parsesBefore + 1,
                parser.parseCounts[files[0].absolutePath],
            )
        }

    @Test
    fun entriesWithoutCoverArtUseMinimalSizeOf() =
        runTest {
            val cache = newCache()
            val parser = FakeParser()
            val files = (0 until 200).map { i -> tempFile("small$i") }
            parser.metadataForPath = { path ->
                val idx = files.indexOfFirst { it.absolutePath == path }
                if (idx >= 0) metadata("s$idx", null) else null
            }
            files.forEach { cache.getOrParse(it, parser) }

            val stats = cache.getCacheStats()
            assertEquals("no-coverArt entries are ~free, none should be evicted", 0, stats.evictionCount)
            // Early entries still cached (no re-parse).
            val parsesBefore = parser.parseCounts[files[0].absolutePath] ?: 0
            cache.getOrParse(files[0], parser)
            assertEquals(parsesBefore, parser.parseCounts[files[0].absolutePath])
        }

    @Test
    fun getReturnsCachedMetadataIntact() =
        runTest {
            val cache = newCache()
            val parser = FakeParser()
            val art = byteArrayOf(1, 2, 3, 4)
            val f = tempFile("roundtrip")
            val expected = metadata("roundtrip-title", art)
            parser.metadataForPath = { if (it == f.absolutePath) expected else null }

            val first = cache.getOrParse(f, parser)
            assertNotNull(first)
            val second = cache.getOrParse(f, parser)
            assertNotNull(second)
            assertEquals(expected.title, second?.title)
            assertEquals(expected.artist, second?.artist)
            assertEquals(expected.album, second?.album)
            assertEquals(expected.duration, second?.duration)
            assertTrue(second?.coverArt!!.contentEquals(art))
            // Second call was served from cache (same instance content).
            assertEquals(first, second)
            assertEquals(1, parser.parseCounts[f.absolutePath])
        }

    @Test
    fun invalidFileTimestampInvalidatesEntry() =
        runTest {
            val cache = newCache()
            val parser = FakeParser()
            val f = tempFile("stale")
            parser.metadataForPath = { metadata("v", null) }
            assertNotNull(cache.getOrParse(f, parser))
            // Touch the file so cached lastModified no longer matches.
            f.setLastModified(System.currentTimeMillis() + 60_000)
            cache.getOrParse(f, parser)
            assertEquals(2, parser.parseCounts[f.absolutePath])
        }

    @Test
    fun clearCacheEvictsEverything() =
        runTest {
            val cache = newCache()
            val parser = FakeParser()
            val f = tempFile("clear")
            parser.metadataForPath = { metadata("c", null) }
            assertNotNull(cache.getOrParse(f, parser))
            cache.clearCache()
            val stats = cache.getCacheStats()
            assertEquals(0, stats.size)
        }

    @Test
    fun hitRateIsComputedFromStats() =
        runTest {
            val cache = newCache()
            val parser = FakeParser()
            val f = tempFile("rate")
            parser.metadataForPath = { metadata("r", null) }
            cache.getOrParse(f, parser) // miss
            cache.getOrParse(f, parser) // hit
            val stats = cache.getCacheStats()
            assertEquals(1, stats.missCount)
            assertEquals(1, stats.hitCount)
            assertEquals(0.5f, stats.hitRate, 0.001f)
        }
}
