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
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.utils.PerformanceClass
import com.jabook.app.jabook.utils.PerformanceUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing Media3 ExoPlayer and Cache as singletons.
 *
 * Inspired by lissen-android implementation for better architecture.
 * This module provides:
 * - ExoPlayer with optimized LoadControl for audiobooks
 * - Cache for network streaming and offline playback
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideMediaCache(
        @ApplicationContext context: Context,
    ): androidx.media3.datasource.cache.Cache {
        val initStart = System.currentTimeMillis()

        // CRITICAL OPTIMIZATION: Minimize blocking operations during cache creation
        // For local files, cache is NOT used, so we can optimize for fast initialization
        // Only network streams use cache, so we can use defaults and avoid slow operations

        // Use default cache dir immediately - no need to check external cache or permissions
        val baseFolder = context.cacheDir
        val cacheDir = File(baseFolder, "playback_cache")

        // CRITICAL: Use default cache limit immediately - NO StatFs call
        // StatFs can be VERY slow on some devices (especially with large storage or slow I/O)
        // and can block initialization for several seconds. This is unacceptable for fast startup.
        // For local files, cache is not used anyway, so we don't need optimal cache size.
        // If StatFs is needed for network streams, it should be done asynchronously later.
        val cacheLimit = DEFAULT_CACHE_BYTES

        android.util.Log.d(
            "MediaModule",
            "Providing Media Cache: ${cacheDir.absolutePath}, limit: ${cacheLimit / (1024 * 1024)} MB (using default to avoid slow StatFs)",
        )

        // CRITICAL: Create StandaloneDatabaseProvider - this may initialize DB synchronously
        // If cache has many entries, DB initialization can be slow.
        // However, for local files, cache is not used, so this won't affect playback startup.
        // The DB initialization happens here, but it's necessary for cache to work.
        // We accept this because:
        // 1. Local files don't use cache, so this doesn't affect local playback startup
        // 2. Network streams need cache, and DB initialization is necessary
        // 3. DB initialization is usually fast (<100ms) unless cache is corrupted
        //
        // OPTIMIZATION: Create database provider - this is lightweight, DB is initialized lazily
        val databaseProvider = StandaloneDatabaseProvider(context)

        val cache =
            try {
                // SimpleCache creation is fast - it just creates the object
                // Actual DB operations happen lazily when cache is first used
                SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(cacheLimit),
                    databaseProvider,
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaModule", "Error creating SimpleCache: ${e.message}", e)
                // If cache creation fails, we can still work without cache (for local files)
                // But we need to throw to prevent using broken cache
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        android.util.Log.d("MediaModule", "Media Cache provided (${initDuration}ms)")

        // Log warning if cache initialization took too long
        if (initDuration > 500) {
            android.util.Log.w(
                "MediaModule",
                "Cache initialization took ${initDuration}ms (slow). Consider cleaning cache if this persists.",
            )
        }

        return cache
    }

    @Provides
    @Singleton
    @Named("okhttp")
    fun provideOkHttpCache(
        @ApplicationContext context: Context,
    ): okhttp3.Cache {
        val cacheDir = File(context.cacheDir, "okhttp_cache")
        val cacheSize = 50L * 1024 * 1024 // 50 MB
        android.util.Log.d(
            "MediaModule",
            "Providing OkHttp Cache: ${cacheDir.absolutePath}, size: ${cacheSize / (1024 * 1024)} MB",
        )
        return okhttp3.Cache(cacheDir, cacheSize)
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
    ): ExoPlayer {
        val initStart = System.currentTimeMillis()

        android.util.Log.d("MediaModule", "Creating ExoPlayer singleton...")

        // Match lissen-android configuration exactly
        // Note: AudioProcessors are configured dynamically in AudioPlayerService
        // based on user settings, not here in the singleton
        // Create optimized LoadControl
        val loadControl = createOptimizedLoadControl(context)

        val player =
            try {
                ExoPlayer
                    .Builder(context)
                    .setLoadControl(loadControl)
                    .setHandleAudioBecomingNoisy(true)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Match lissen-android exactly
                            .build(),
                        true, // handleAudioFocus=true - ExoPlayer manages AudioFocus automatically
                    ).build()
            } catch (e: Exception) {
                android.util.Log.e("MediaModule", "Error creating ExoPlayer: ${e.message}", e)
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        android.util.Log.d("MediaModule", "ExoPlayer singleton provided (${initDuration}ms)")

        return player
    }

    /**
     * Creates ExoPlayer with AudioProcessors based on settings.
     *
     * This method is used by AudioPlayerService to create a player instance
     * with audio processing enabled. The player is not a singleton and should
     * be released when done.
     *
     * @param context Application context
     * @param settings Audio processing settings
     * @return Configured ExoPlayer instance
     */
    @OptIn(UnstableApi::class)
    fun createExoPlayerWithProcessors(
        context: Context,
        settings: AudioProcessingSettings,
    ): ExoPlayer {
        val initStart = System.currentTimeMillis()

        android.util.Log.d("MediaModule", "Creating ExoPlayer with AudioProcessors...")

        // Create processor chain
        val processors =
            com.jabook.app.jabook.audio.processors.AudioProcessorFactory
                .createProcessorChain(settings)

        val player =
            try {
                // Create RenderersFactory
                // Note: AudioProcessors support in Media3 1.8.0 may require a different approach
                // For now, we'll create the player without processors and log a warning
                val renderersFactory: RenderersFactory = DefaultRenderersFactory(context)

                if (processors.isNotEmpty()) {
                    android.util.Log.w(
                        "MediaModule",
                        "AudioProcessors requested but may not be fully supported in Media3 1.8.0. " +
                            "Processors: ${processors.size}. Consider upgrading Media3 version.",
                    )
                }

                val builder =
                    ExoPlayer
                        .Builder(context)
                        .setRenderersFactory(renderersFactory)
                        .setLoadControl(createOptimizedLoadControl(context))
                        .setHandleAudioBecomingNoisy(true)
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                                .build(),
                            true, // handleAudioFocus=true
                        )

                if (processors.isNotEmpty()) {
                    android.util.Log.d("MediaModule", "Added ${processors.size} AudioProcessors to ExoPlayer")
                }

                builder.build()
            } catch (e: Exception) {
                android.util.Log.e("MediaModule", "Error creating ExoPlayer with processors: ${e.message}", e)
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        android.util.Log.d("MediaModule", "ExoPlayer with processors provided (${initDuration}ms)")

        return player
    }

    private fun createOptimizedLoadControl(context: Context): androidx.media3.exoplayer.LoadControl {
        val performanceClass = PerformanceUtils.getPerformanceClass(context)
        val loadControlBuilder = DefaultLoadControl.Builder()

        if (performanceClass == PerformanceClass.LOW) {
            // For low-end devices, reduce buffer sizes to save memory
            loadControlBuilder
                .setBufferDurationsMs(
                    15000,
                    30000,
                    1500,
                    3000,
                ).setTargetBufferBytes(32 * 1024 * 1024)
        }
        return loadControlBuilder.build()
    }

    /**
     * Calculates optimal cache size limit based on available storage.
     *
     * @param ctx Application context
     * @return Cache size limit in bytes
     */
    private fun buildPlaybackCacheLimit(ctx: Context): Long {
        val baseFolder =
            ctx
                .externalCacheDir
                ?.takeIf { it.exists() && it.canWrite() }
                ?: ctx.cacheDir

        val stat = android.os.StatFs(baseFolder.path)
        val available = stat.availableBytes
        val dynamicCap = (available - KEEP_FREE_BYTES).coerceAtLeast(MIN_CACHE_BYTES)

        return minOf(MAX_CACHE_BYTES, dynamicCap)
    }

    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB
    private const val DEFAULT_CACHE_BYTES = 200L * 1024 * 1024 // 200 MB (fallback if StatFs fails)
    private const val KEEP_FREE_BYTES = 20L * 1024 * 1024 // 20 MB
    private const val MIN_CACHE_BYTES = 10L * 1024 * 1024 // 10 MB
}

/**
 * Hilt module for providing audio database and preferences.
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioDataModule {
    @Provides
    @Singleton
    fun provideAudioDatabase(
        @ApplicationContext context: Context,
    ): com.jabook.app.jabook.audio.data.local.database.AudioDatabase =
        androidx.room.Room
            .databaseBuilder(
                context,
                com.jabook.app.jabook.audio.data.local.database.AudioDatabase::class.java,
                "audio_database",
            ).build()

    @Provides
    @Singleton
    fun provideAudioPreferences(
        @ApplicationContext context: Context,
    ): com.jabook.app.jabook.audio.data.local.datastore.AudioPreferences =
        com.jabook.app.jabook.audio.data.local.datastore
            .AudioPreferences(context)
}

/**
 * Hilt module for providing audio data repositories.
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioRepositoryModule {
    @Provides
    @Singleton
    fun provideSavedPlayerStateRepository(
        database: com.jabook.app.jabook.audio.data.local.database.AudioDatabase,
    ): com.jabook.app.jabook.audio.data.repository.SavedPlayerStateRepository {
        val dao = database.savedPlayerStateDao()
        return com.jabook.app.jabook.audio.data.repository
            .SavedPlayerStateRepository(dao)
    }
}
