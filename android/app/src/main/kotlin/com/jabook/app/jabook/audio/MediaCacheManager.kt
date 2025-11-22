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

package com.jabook.app.jabook.audio

import android.content.Context
import android.os.StatFs
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Manages media cache for offline playback and reduced network usage.
 *
 * Inspired by lissen-android implementation for efficient cache management.
 * Uses sliding window strategy: caches current and nearby chapters,
 * old chapters are gradually evicted.
 */
class MediaCacheManager(private val context: Context) {
    private var cache: Cache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    
    companion object {
        // Cache size limits (inspired by lissen-android)
        private const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB max
        private const val KEEP_FREE_BYTES = 20L * 1024 * 1024 // Keep 20 MB free
        private const val MIN_CACHE_BYTES = 10L * 1024 * 1024 // 10 MB minimum
    }
    
    /**
     * Gets or creates media cache instance.
     * 
     * @return Cache instance for media files
     */
    fun getCache(): Cache {
        if (cache == null) {
            val baseFolder = context.externalCacheDir
                ?.takeIf { it.exists() && it.canWrite() }
                ?: context.cacheDir
            
            databaseProvider = StandaloneDatabaseProvider(context)
            
            cache = SimpleCache(
                File(baseFolder, "playback_cache"),
                LeastRecentlyUsedCacheEvictor(calculateCacheLimit()),
                databaseProvider!!
            )
            
            android.util.Log.d("MediaCacheManager", "Media cache initialized: ${calculateCacheLimit() / (1024 * 1024)} MB")
        }
        return cache!!
    }
    
    /**
     * Calculates optimal cache size limit based on available storage.
     * 
     * @return Cache size limit in bytes
     */
    private fun calculateCacheLimit(): Long {
        val baseFolder = context.externalCacheDir
            ?.takeIf { it.exists() && it.canWrite() }
            ?: context.cacheDir
        
        val stat = StatFs(baseFolder.path)
        val available = stat.availableBytes
        val dynamicCap = (available - KEEP_FREE_BYTES).coerceAtLeast(MIN_CACHE_BYTES)
        
        return minOf(MAX_CACHE_BYTES, dynamicCap)
    }
    
    /**
     * Releases cache resources.
     */
    fun release() {
        try {
            cache?.release()
            cache = null
            databaseProvider = null
            android.util.Log.d("MediaCacheManager", "Media cache released")
        } catch (e: Exception) {
            android.util.Log.e("MediaCacheManager", "Failed to release cache", e)
        }
    }
}

