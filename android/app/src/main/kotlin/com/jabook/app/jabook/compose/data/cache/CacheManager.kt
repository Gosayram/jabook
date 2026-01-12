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

package com.jabook.app.jabook.compose.data.cache

import android.content.Context
import coil3.SingletonImageLoader
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages app cache: tracks sizes, provides cleanup operations.
 */
@Singleton
public class CacheManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val database: JabookDatabase,
        private val rutrackerSearchCache: RutrackerSearchCache,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("CacheManager")

        /**
         * Get total cache size in bytes.
         */
        public suspend fun getTotalCacheSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    val appCache = context.cacheDir.walkFileTree().sumOf { it.length() }
                    val externalCache = context.externalCacheDir?.walkFileTree()?.sumOf { it.length() } ?: 0L
                    appCache + externalCache
                } catch (e: Exception) {
                    logger.e({ "Failed to calculate cache size" }, e)
                    0L
                }
            }

        /**
         * Get cache statistics by type.
         */
        public suspend fun getCacheStatistics(): CacheStatistics =
            withContext(Dispatchers.IO) {
                try {
                    val searchCacheSize = getSearchCacheSize()
                    val topicCacheSize = getTopicCacheSize()
                    val tempDownloadsSize = getTempDownloadsSize()
                    val logFilesSize = getLogFilesSize()
                    val imageCacheSize = getImageCacheSize()

                    CacheStatistics(
                        totalSize = getTotalCacheSize(),
                        searchCacheSize = searchCacheSize,
                        topicCacheSize = topicCacheSize,
                        tempDownloadsSize = tempDownloadsSize,
                        logFilesSize = logFilesSize,
                        imageCacheSize = imageCacheSize,
                        lastCleanup = getLastCleanupTimestamp(),
                    )
                } catch (e: Exception) {
                    logger.e({ "Failed to get cache statistics" }, e)
                    CacheStatistics(
                        totalSize = 0L,
                        searchCacheSize = 0L,
                        topicCacheSize = 0L,
                        tempDownloadsSize = 0L,
                        logFilesSize = 0L,
                        imageCacheSize = 0L,
                        lastCleanup = 0L,
                    )
                }
            }

        /**
         * Clear all cache.
         */
        public suspend fun clearAllCache(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    logger.d { "Clearing all cache" }

                    // Clear Coil memory cache first (before deleting directories)
                    try {
                        val imageLoader = SingletonImageLoader.get(context)
                        imageLoader.memoryCache?.clear()
                        logger.d { "Coil memory cache cleared" }
                    } catch (e: Exception) {
                        logger.e({ "Failed to clear Coil memory cache" }, e)
                    }

                    // Clear cache directories (includes Coil disk cache in image_cache/)
                    context.cacheDir.deleteRecursively()
                    context.externalCacheDir?.deleteRecursively()

                    // Recreate directories
                    context.cacheDir.mkdirs()
                    context.externalCacheDir?.mkdirs()

                    saveLastCleanupTimestamp()
                    logger.d { "All cache cleared successfully" }
                    true
                } catch (e: Exception) {
                    logger.e({ "Failed to clear cache" }, e)
                    false
                }
            }

        /**
         * Clear specific cache type.
         */
        public suspend fun clearCacheType(type: CacheType): Boolean =
            when (type) {
                CacheType.SEARCH -> clearSearchCache()
                CacheType.TOPICS -> clearTopicCache()
                CacheType.TEMP_DOWNLOADS -> clearTempDownloads()
                CacheType.LOGS -> clearLogFiles()
            }

        private suspend fun clearSearchCache(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    rutrackerSearchCache.clear()
                    logger.d { "Search cache cleared successfully" }
                    true
                } catch (e: Exception) {
                    logger.e({ "Failed to clear search cache" }, e)
                    false
                }
            }

        private suspend fun clearTopicCache(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    database.offlineSearchDao().deleteAllTopics()
                    database.offlineSearchDao().deleteAllMappings()
                    logger.d { "Topic cache cleared successfully" }
                    true
                } catch (e: Exception) {
                    logger.e({ "Failed to clear topic cache" }, e)
                    false
                }
            }

        private suspend fun clearTempDownloads(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val tempDir = File(context.cacheDir, "downloads")
                    val result = tempDir.deleteRecursively()
                    tempDir.mkdirs()
                    logger.d { "Temp downloads cleared: $result" }
                    result
                } catch (e: Exception) {
                    logger.e({ "Failed to clear temp downloads" }, e)
                    false
                }
            }

        private suspend fun clearLogFiles(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val logFiles =
                        context.cacheDir.listFiles { file ->
                            file.name.startsWith("jabook_logs_")
                        }
                    var cleared: Int = 0
                    logFiles?.forEach { file ->
                        if (file.delete()) cleared++
                    }
                    logger.d { "Log files cleared: $cleared" }
                    true
                } catch (e: Exception) {
                    logger.e({ "Failed to clear log files" }, e)
                    false
                }
            }

        private suspend fun getSearchCacheSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    rutrackerSearchCache.getCacheSize()
                } catch (e: Exception) {
                    0L
                }
            }

        private suspend fun getTopicCacheSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    val count = database.offlineSearchDao().getTopicCount()
                    // Estimate ~1KB per topic entity including mappings
                    count * 1024L
                } catch (e: Exception) {
                    0L
                }
            }

        private suspend fun getTempDownloadsSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    val tempDir = File(context.cacheDir, "downloads")
                    if (tempDir.exists()) {
                        tempDir.walkFileTree().sumOf { it.length() }
                    } else {
                        0L
                    }
                } catch (e: Exception) {
                    0L
                }
            }

        private suspend fun getLogFilesSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    val logFiles =
                        context.cacheDir.listFiles { file ->
                            file.name.startsWith("jabook_logs_")
                        }
                    logFiles?.sumOf { it.length() } ?: 0L
                } catch (e: Exception) {
                    0L
                }
            }

        private suspend fun getImageCacheSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    val imageCacheDir = File(context.cacheDir, "image_cache")
                    if (imageCacheDir.exists()) {
                        imageCacheDir.walkFileTree().sumOf { it.length() }
                    } else {
                        0L
                    }
                } catch (e: Exception) {
                    logger.e({ "Failed to get image cache size" }, e)
                    0L
                }
            }

        private fun getLastCleanupTimestamp(): Long {
            val prefs = context.getSharedPreferences("cache_prefs", Context.MODE_PRIVATE)
            return prefs.getLong("last_cleanup", 0L)
        }

        private fun saveLastCleanupTimestamp() {
            val prefs = context.getSharedPreferences("cache_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_cleanup", System.currentTimeMillis()).apply()
        }

    }

/**
 * Cache statistics by type.
 */
public data class CacheStatistics(
    val totalSize: Long,
    val searchCacheSize: Long,
    val topicCacheSize: Long,
    val tempDownloadsSize: Long,
    val logFilesSize: Long,
    val imageCacheSize: Long,
    val lastCleanup: Long,
)

/**
 * Cache type for selective clearing.
 */
public enum class CacheType {
    SEARCH,
    TOPICS,
    TEMP_DOWNLOADS,
    LOGS,
}

/**
 * Helper extension to walk file tree and collect all files.
 */
private fun File.walkFileTree(): Sequence<File> =
    sequence {
        if (exists()) {
            if (isDirectory) {
                val children = listFiles()
                if (children != null) {
                    for (child in children) {
                        yieldAll(child.walkFileTree())
                    }
                }
            } else {
                yield(this@walkFileTree)
            }
        }
    }
