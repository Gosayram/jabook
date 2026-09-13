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

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import com.jabook.app.jabook.util.LogUtils

/**
 * Self-heals the media cache after read failures (#26: FLAG_IGNORE_CACHE_ON_ERROR silent rot).
 *
 * With [CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR] a failed cache read is silently bypassed and
 * re-fetched from network — but the suspect entry stays in the cache, so every subsequent read of
 * that resource repeats the failed cache attempt (silent rot). [onCacheIgnored] fires exactly when
 * the ignore flag trips ([CacheDataSource.CACHE_IGNORED_REASON_ERROR] only), so we drop the
 * suspect resource via [Cache.removeResource] and the next fetch can be cached again.
 *
 * Trade-off: a transient cache error (e.g. disk pressure) also triggers removal and costs one
 * re-download; LRU eviction remains the size-based cleanup path.
 *
 * One instance per [CacheDataSource]. [onOpen] must be called before delegating
 * `CacheDataSource.open()`, because `onCacheIgnored` can fire from within `open()` before the
 * upstream source of the current request has been opened.
 */
@OptIn(UnstableApi::class)
internal class CacheErrorHealer(
    private val cache: Cache,
) : CacheDataSource.EventListener {
    private var currentKey: String? = null

    /** Records the cache key of the request being opened. */
    fun onOpen(dataSpec: DataSpec) {
        currentKey = CacheKeyFactory.DEFAULT.buildCacheKey(dataSpec)
    }

    override fun onCachedBytesRead(
        cacheBytesRead: Long,
        totalBytesRead: Long,
    ) = Unit

    override fun onCacheIgnored(reason: Int) {
        if (reason != CacheDataSource.CACHE_IGNORED_REASON_ERROR) return
        val key = currentKey ?: return
        LogUtils.w(TAG, "Cache read failed; dropping suspect cache entry: $key")
        try {
            cache.removeResource(key)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to remove suspect cache entry $key: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "CacheErrorHealer"
    }
}
