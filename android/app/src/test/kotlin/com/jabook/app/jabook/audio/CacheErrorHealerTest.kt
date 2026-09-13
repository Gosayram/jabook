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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheErrorHealerTest {
    private val cache: Cache = mock()
    private val healer = CacheErrorHealer(cache)

    @Test
    fun `removes suspect resource on cache error ignore reason`() {
        healer.onOpen(DataSpec(Uri.parse("https://example.com/a.mp3")))

        healer.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_ERROR)

        verify(cache).removeResource("https://example.com/a.mp3")
    }

    @Test
    fun `prefers explicit dataspec key over uri`() {
        val dataSpec =
            DataSpec
                .Builder()
                .setUri(Uri.parse("https://example.com/a.mp3"))
                .setKey("book-1")
                .build()

        healer.onOpen(dataSpec)
        healer.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_ERROR)

        verify(cache).removeResource("book-1")
        verify(cache, never()).removeResource("https://example.com/a.mp3")
    }

    @Test
    fun `does not remove for unset length ignore reason`() {
        healer.onOpen(DataSpec(Uri.parse("https://example.com/a.mp3")))

        healer.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_UNSET_LENGTH)

        verify(cache, never()).removeResource(any())
    }

    @Test
    fun `tracks the latest opened request`() {
        healer.onOpen(DataSpec(Uri.parse("https://example.com/a.mp3")))
        healer.onOpen(DataSpec(Uri.parse("https://example.com/b.mp3")))

        healer.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_ERROR)

        verify(cache, never()).removeResource("https://example.com/a.mp3")
        verify(cache).removeResource("https://example.com/b.mp3")
    }

    @Test
    fun `no removal when no request was opened`() {
        healer.onCacheIgnored(CacheDataSource.CACHE_IGNORED_REASON_ERROR)

        verify(cache, never()).removeResource(any())
    }
}
