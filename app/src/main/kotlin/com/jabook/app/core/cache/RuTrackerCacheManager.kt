package com.jabook.app.core.cache

import com.jabook.app.core.network.exceptions.RuTrackerException
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache entry metadata
 */
@Serializable
data class CacheEntry<T>(
    val data: T,
    val timestamp: Long,
    val ttl: Long,
    val accessCount: Long = 0,
    val lastAccessed: Long = System.currentTimeMillis(),
)

/**
 * Cache configuration
 */
data class CacheConfig(
    val memoryMaxSize: Int = 100, // Maximum entries in memory cache
    val diskMaxSize: Long = 100 * 1024 * 1024, // 100MB disk cache
    val defaultTTL: Long = 30 * 60 * 1000L, // 30 minutes default TTL
    val cleanupInterval: Long = 10 * 60 * 1000L, // 10 minutes cleanup interval
    val compressionEnabled: Boolean = true,
    val encryptionEnabled: Boolean = false,
)

/**
 * Cache statistics
 */
data class CacheStatistics(
    val memoryEntries: Int,
    val diskEntries: Int,
    val memorySize: Long,
    val diskSize: Long,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long,
    val lastCleanup: Long,
)

/**
 * Cache key with namespace support
 */
data class CacheKey(
    val namespace: String,
    val key: String,
    val version: Int = 1,
) {
    override fun toString(): String = "${namespace}_v${version}_$key"
}

/**
 * RuTracker Cache Manager with memory and disk caching
 */
@Singleton
class RuTrackerCacheManager
    @Inject
    constructor(
        private val debugLogger: IDebugLogger,
        private val cacheDir: File,
        private val config: CacheConfig = CacheConfig(),
    ) {
        private val memoryCache = ConcurrentHashMap<String, CacheEntry<Any>>()
        private val accessCounts = ConcurrentHashMap<String, AtomicLong>()
        private val mutex = Mutex()

        private val _statistics = MutableStateFlow(CacheStatistics(0, 0, 0, 0, 0, 0, 0, 0))
        val statistics: Flow<CacheStatistics> = _statistics.asStateFlow()

        private val hitCount = AtomicLong(0)
        private val missCount = AtomicLong(0)
        private val evictionCount = AtomicLong(0)

        private var cleanupJob: kotlinx.coroutines.Job? = null
        private val isCleanupRunning =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        init {
            // Ensure cache directory exists
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Start periodic cleanup
            startCleanupTask()

            debugLogger.logInfo("RuTrackerCacheManager: Initialized with config: $config")
        }

        /**
         * Get data from cache
         */
        suspend fun <T> get(
            key: CacheKey,
            deserializer: (String) -> T,
        ): Result<T> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val cacheKey = key.toString()

                    // Try memory cache first
                    val memoryEntry = memoryCache[cacheKey]
                    if (memoryEntry != null && !isExpired(memoryEntry)) {
                        // Update access statistics
                        memoryEntry.accessCount = memoryEntry.accessCount + 1
                        memoryEntry.lastAccessed = System.currentTimeMillis()
                        hitCount.incrementAndGet()
                        updateStatistics()

                        @Suppress("UNCHECKED_CAST")
                        return@withContext Result.success(memoryEntry.data as T)
                    }

                    // Try disk cache
                    val diskFile = getDiskCacheFile(cacheKey)
                    if (diskFile.exists()) {
                        try {
                            val json = diskFile.readText()
                            val entry = Json.decodeFromString<CacheEntry<String>>(json)

                            if (!isExpired(entry)) {
                                // Move to memory cache if space available
                                if (memoryCache.size < config.memoryMaxSize) {
                                    val data = deserializer(entry.data)
                                    memoryCache[cacheKey] = entry.copy(data = data as Any)
                                }

                                hitCount.incrementAndGet()
                                updateStatistics()

                                return@withContext Result.success(deserializer(entry.data))
                            } else {
                                // Remove expired entry
                                diskFile.delete()
                            }
                        } catch (e: Exception) {
                            debugLogger.logWarning("RuTrackerCacheManager: Failed to read disk cache for $cacheKey", e)
                            diskFile.delete()
                        }
                    }

                    missCount.incrementAndGet()
                    updateStatistics()
                    Result.failure(RuTrackerException.CacheException("Cache miss for key: $cacheKey"))
                }
            }

        /**
         * Put data in cache
         */
        suspend fun <T> put(
            key: CacheKey,
            data: T,
            ttl: Long = config.defaultTTL,
            serializer: (T) -> String,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val cacheKey = key.toString()
                    val now = System.currentTimeMillis()

                    try {
                        val entry =
                            CacheEntry(
                                data = data as Any,
                                timestamp = now,
                                ttl = ttl,
                                accessCount = 1,
                                lastAccessed = now,
                            )

                        // Put in memory cache
                        if (memoryCache.size >= config.memoryMaxSize) {
                            evictFromMemoryCache()
                        }
                        memoryCache[cacheKey] = entry

                        // Put in disk cache
                        val diskFile = getDiskCacheFile(cacheKey)
                        val jsonEntry = entry.copy(data = serializer(data))
                        val json = Json.encodeToString(jsonEntry)

                        diskFile.writeText(json)

                        updateStatistics()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        debugLogger.logError("RuTrackerCacheManager: Failed to cache data for key: $cacheKey", e)
                        Result.failure(RuTrackerException.CacheException("Failed to cache data: ${e.message}"))
                    }
                }
            }

        /**
         * Remove data from cache
         */
        suspend fun remove(key: CacheKey): Result<Unit> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val cacheKey = key.toString()

                    try {
                        // Remove from memory cache
                        memoryCache.remove(cacheKey)

                        // Remove from disk cache
                        val diskFile = getDiskCacheFile(cacheKey)
                        if (diskFile.exists()) {
                            diskFile.delete()
                        }

                        updateStatistics()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        debugLogger.logError("RuTrackerCacheManager: Failed to remove cache for key: $cacheKey", e)
                        Result.failure(RuTrackerException.CacheException("Failed to remove cache: ${e.message}"))
                    }
                }
            }

        /**
         * Clear all cache
         */
        suspend fun clear(): Result<Unit> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        // Clear memory cache
                        memoryCache.clear()

                        // Clear disk cache
                        cacheDir.listFiles()?.forEach { file ->
                            if (file.name.endsWith(".cache")) {
                                file.delete()
                            }
                        }

                        // Reset counters
                        hitCount.set(0)
                        missCount.set(0)
                        evictionCount.set(0)

                        updateStatistics()
                        debugLogger.logInfo("RuTrackerCacheManager: Cache cleared")
                        Result.success(Unit)
                    } catch (e: Exception) {
                        debugLogger.logError("RuTrackerCacheManager: Failed to clear cache", e)
                        Result.failure(RuTrackerException.CacheException("Failed to clear cache: ${e.message}"))
                    }
                }
            }

        /**
         * Clear cache by namespace
         */
        suspend fun clearNamespace(namespace: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        // Remove from memory cache
                        val keysToRemove = memoryCache.keys.filter { it.startsWith("${namespace}_v") }
                        keysToRemove.forEach { memoryCache.remove(it) }

                        // Remove from disk cache
                        cacheDir.listFiles()?.forEach { file ->
                            if (file.name.startsWith("${namespace}_v") && file.name.endsWith(".cache")) {
                                file.delete()
                            }
                        }

                        updateStatistics()
                        debugLogger.logInfo("RuTrackerCacheManager: Cache cleared for namespace: $namespace")
                        Result.success(Unit)
                    } catch (e: Exception) {
                        debugLogger.logError("RuTrackerCacheManager: Failed to clear cache for namespace: $namespace", e)
                        Result.failure(RuTrackerException.CacheException("Failed to clear namespace cache: ${e.message}"))
                    }
                }
            }

        /**
         * Check if key exists in cache
         */
        suspend fun contains(key: CacheKey): Boolean =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val cacheKey = key.toString()

                    // Check memory cache
                    val memoryEntry = memoryCache[cacheKey]
                    if (memoryEntry != null && !isExpired(memoryEntry)) {
                        return@withLock true
                    }

                    // Check disk cache
                    val diskFile = getDiskCacheFile(cacheKey)
                    if (diskFile.exists()) {
                        try {
                            val json = diskFile.readText()
                            val entry = Json.decodeFromString<CacheEntry<String>>(json)
                            return@withLock !isExpired(entry)
                        } catch (e: Exception) {
                            diskFile.delete()
                        }
                    }

                    false
                }
            }

        /**
         * Get cache size
         */
        suspend fun getSize(): Long =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    var totalSize = 0L

                    // Memory cache size (approximate)
                    memoryCache.values.forEach { entry ->
                        totalSize += estimateSize(entry.data)
                    }

                    // Disk cache size
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".cache")) {
                            totalSize += file.length()
                        }
                    }

                    totalSize
                }
            }

        /**
         * Get cache keys by namespace
         */
        suspend fun getKeys(namespace: String): List<String> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val keys = mutableListOf<String>()
                    val prefix = "${namespace}_v"

                    // Memory cache keys
                    memoryCache.keys.filter { it.startsWith(prefix) }.forEach { keys.add(it) }

                    // Disk cache keys
                    cacheDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith(prefix) && file.name.endsWith(".cache")) {
                            keys.add(file.name.removeSuffix(".cache"))
                        }
                    }

                    keys.distinct()
                }
            }

        /**
         * Evict least recently used entries from memory cache
         */
        private suspend fun evictFromMemoryCache() {
            if (memoryCache.size <= config.memoryMaxSize) return

            val entriesToEvict =
                memoryCache.entries
                    .sortedBy { it.value.lastAccessed }
                    .take(memoryCache.size - config.memoryMaxSize + 1)

            entriesToEvict.forEach { (key, _) ->
                memoryCache.remove(key)
                evictionCount.incrementAndGet()
            }

            updateStatistics()
        }

        /**
         * Check if cache entry is expired
         */
        private fun isExpired(entry: CacheEntry<*>): Boolean = System.currentTimeMillis() - entry.timestamp > entry.ttl

        /**
         * Get disk cache file
         */
        private fun getDiskCacheFile(key: String): File = File(cacheDir, "${key.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.cache")

        /**
         * Estimate size of object in bytes
         */
        private fun estimateSize(obj: Any): Long =
            try {
                Json
                    .encodeToString(obj)
                    .toByteArray()
                    .size
                    .toLong()
            } catch (e: Exception) {
                1024L // Default estimate
            }

        /**
         * Update cache statistics
         */
        private suspend fun updateStatistics() {
            mutex.withLock {
                val memorySize = memoryCache.values.sumOf { estimateSize(it.data) }
                val diskSize = cacheDir.listFiles()?.filter { it.name.endsWith(".cache") }?.sumOf { it.length() } ?: 0L

                _statistics.value =
                    CacheStatistics(
                        memoryEntries = memoryCache.size,
                        diskEntries = cacheDir.listFiles()?.count { it.name.endsWith(".cache") } ?: 0,
                        memorySize = memorySize,
                        diskSize = diskSize,
                        hitCount = hitCount.get(),
                        missCount = missCount.get(),
                        evictionCount = evictionCount.get(),
                        lastCleanup = System.currentTimeMillis(),
                    )
            }
        }

        /**
         * Start periodic cleanup task
         */
        private fun startCleanupTask() {
            if (isCleanupRunning.getAndSet(true)) return

            cleanupJob =
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    while (isCleanupRunning.get()) {
                        try {
                            cleanupExpiredEntries()
                            kotlinx.coroutines.delay(config.cleanupInterval)
                        } catch (e: Exception) {
                            debugLogger.logError("RuTrackerCacheManager: Error in cleanup task", e)
                            kotlinx.coroutines.delay(config.cleanupInterval)
                        }
                    }
                }

            debugLogger.logInfo("RuTrackerCacheManager: Cleanup task started")
        }

        /**
         * Stop cleanup task
         */
        fun stopCleanupTask() {
            isCleanupRunning.set(false)
            cleanupJob?.cancel()
            cleanupJob = null
            debugLogger.logInfo("RuTrackerCacheManager: Cleanup task stopped")
        }

        /**
         * Clean up expired entries
         */
        private suspend fun cleanupExpiredEntries() {
            mutex.withLock {
                var cleanedCount = 0

                // Clean memory cache
                val expiredMemoryKeys =
                    memoryCache.keys.filter { key ->
                        val entry = memoryCache[key]
                        entry != null && isExpired(entry)
                    }

                expiredMemoryKeys.forEach { key ->
                    memoryCache.remove(key)
                    cleanedCount++
                }

                // Clean disk cache
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".cache")) {
                        try {
                            val json = file.readText()
                            val entry = Json.decodeFromString<CacheEntry<String>>(json)

                            if (isExpired(entry)) {
                                file.delete()
                                cleanedCount++
                            }
                        } catch (e: Exception) {
                            file.delete()
                            cleanedCount++
                        }
                    }
                }

                if (cleanedCount > 0) {
                    updateStatistics()
                    debugLogger.logDebug("RuTrackerCacheManager: Cleaned up $cleanedCount expired entries")
                }
            }
        }

        /**
         * Force cleanup of expired entries
         */
        suspend fun forceCleanup(): Result<Int> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    try {
                        val beforeCount = memoryCache.size + (cacheDir.listFiles()?.count { it.name.endsWith(".cache") } ?: 0)
                        cleanupExpiredEntries()
                        val afterCount = memoryCache.size + (cacheDir.listFiles()?.count { it.name.endsWith(".cache") } ?: 0)

                        val cleanedCount = beforeCount - afterCount
                        debugLogger.logInfo("RuTrackerCacheManager: Force cleanup completed, removed $cleanedCount entries")

                        Result.success(cleanedCount)
                    } catch (e: Exception) {
                        debugLogger.logError("RuTrackerCacheManager: Force cleanup failed", e)
                        Result.failure(RuTrackerException.CacheException("Force cleanup failed: ${e.message}"))
                    }
                }
            }

        /**
         * Get cache hit rate
         */
        fun getHitRate(): Flow<Float> =
            statistics.map { stats ->
                val total = stats.hitCount + stats.missCount
                if (total > 0) stats.hitCount.toFloat() / total else 0f
            }

        /**
         * Get cache efficiency metrics
         */
        fun getEfficiencyMetrics(): Flow<Map<String, Float>> =
            statistics.map { stats ->
                val total = stats.hitCount + stats.missCount
                val hitRate = if (total > 0) stats.hitCount.toFloat() / total else 0f
                val memoryUtilization = if (config.memoryMaxSize > 0) stats.memoryEntries.toFloat() / config.memoryMaxSize else 0f
                val diskUtilization = if (config.diskMaxSize > 0) stats.diskSize.toFloat() / config.diskMaxSize else 0f

                mapOf(
                    "hitRate" to hitRate,
                    "memoryUtilization" to memoryUtilization,
                    "diskUtilization" to diskUtilization,
                    "evictionRate" to (if (total > 0) stats.evictionCount.toFloat() / total else 0f),
                )
            }
    }
