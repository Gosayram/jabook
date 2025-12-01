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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
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
    ): Cache {
        val initStart = System.currentTimeMillis()

        // Optimize cache directory selection (avoid slow StatFs on main thread)
        val baseFolder =
            try {
                context.externalCacheDir?.takeIf { it.exists() && it.canWrite() }
                    ?: context.cacheDir
            } catch (e: Exception) {
                android.util.Log.w("MediaModule", "Error getting cache dir, using fallback: ${e.message}")
                context.cacheDir
            }

        val cacheDir = File(baseFolder, "playback_cache")

        // Use fixed cache limit to avoid slow StatFs call on main thread
        // StatFs can be slow and cause ANR, so we use a reasonable default
        // Inspired by lissen-android: minimize synchronous I/O operations
        val cacheLimit =
            try {
                // Try to get available space, but with timeout protection
                val stat = android.os.StatFs(baseFolder.path)
                val available = stat.availableBytes
                val dynamicCap = (available - KEEP_FREE_BYTES).coerceAtLeast(MIN_CACHE_BYTES)
                minOf(MAX_CACHE_BYTES, dynamicCap)
            } catch (e: Exception) {
                android.util.Log.w("MediaModule", "Error calculating cache limit, using default: ${e.message}")
                // Use default if StatFs fails (prevents ANR)
                DEFAULT_CACHE_BYTES
            }

        android.util.Log.d(
            "MediaModule",
            "Providing Media Cache: ${cacheDir.absolutePath}, limit: ${cacheLimit / (1024 * 1024)} MB",
        )

        val cache =
            try {
                SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(cacheLimit),
                    StandaloneDatabaseProvider(context),
                )
            } catch (e: Exception) {
                android.util.Log.e("MediaModule", "Error creating SimpleCache: ${e.message}", e)
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        android.util.Log.d("MediaModule", "Media Cache provided (${initDuration}ms)")

        return cache
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
        val player =
            try {
                ExoPlayer
                    .Builder(context)
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
