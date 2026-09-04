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

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.room.RoomDatabase
import com.jabook.app.jabook.audio.data.local.database.migration.AudioDatabaseMigrations
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.audio.processors.AudioProcessorFactory
import com.jabook.app.jabook.audio.processors.DRCLevel
import com.jabook.app.jabook.audio.processors.FloatPcmOutputProcessor
import com.jabook.app.jabook.audio.processors.NoiseGateLevel
import com.jabook.app.jabook.audio.processors.SpeechCompressorLevel
import com.jabook.app.jabook.audio.processors.VolumeBoostLevel
import com.jabook.app.jabook.crash.GlobalExceptionHandler
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.PerformanceClass
import com.jabook.app.jabook.utils.PerformanceUtils
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
public object MediaModule {
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    public fun provideMediaCache(
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

        LogUtils.d(
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
                LogUtils.e("MediaModule", "Error creating SimpleCache: ${e.message}", e)
                // If cache creation fails, we can still work without cache (for local files)
                // But we need to throw to prevent using broken cache
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        LogUtils.d("MediaModule", "Media Cache provided (${initDuration}ms)")

        // Log warning if cache initialization took too long
        if (initDuration > 500) {
            LogUtils.w(
                "MediaModule",
                "Cache initialization took ${initDuration}ms (slow). Consider cleaning cache if this persists.",
            )
        }

        return cache
    }

    @OptIn(UnstableApi::class, ExperimentalApi::class)
    @Provides
    @Singleton
    public fun provideExoPlayer(
        @ApplicationContext context: Context,
    ): ExoPlayer {
        val initStart = System.currentTimeMillis()

        LogUtils.d("MediaModule", "Creating ExoPlayer singleton...")

        // Match lissen-android configuration exactly
        // Note: AudioProcessors are configured dynamically in AudioPlayerService
        // based on user settings, not here in the singleton
        // Create optimized LoadControl
        val loadControl = createOptimizedLoadControl(context)

        val extractorsFactory =
            DefaultExtractorsFactory()
                .setMp3ExtractorFlags(
                    Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                        Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING,
                )

        val mediaSourceFactory =
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                context,
                extractorsFactory,
            )

        val player =
            try {
                ExoPlayer
                    .Builder(context)
                    .experimentalSetDynamicSchedulingEnabled(true)
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    // Seek increments for player.seekBack()/seekForward() — used by Wear/Auto
                    // skip buttons and KEYCODE_MEDIA_FAST_FORWARD/REWIND. Must match the app
                    // defaults (10s rewind / 30s forward, see MediaSessionManager).
                    .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                    .setSeekForwardIncrementMs(SEEK_FORWARD_INCREMENT_MS)
                    // We run our own SkipSilenceAudioProcessor in the processor chain —
                    // Media3's built-in silence skipper must stay off to avoid double-skipping.
                    .setSkipSilenceEnabled(false)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                            .build(),
                        true,
                    ).build()
                    .also {
                        // #10: Delegate playlist preloading to Media3 so LoadControl throttles
                        // preload contention with active playback (vs custom LRU re-fetch).
                        // AdaptivePlaylistMemoryOptimizer is kept for window sizing (±1..10
                        // based on availMem); this call enables the official preload path.
                        // ponytail: 30s target covers gapless chapter transition without
                        // bloating RAM; bump if chapters routinely exceed buffer.
                        it.setPreloadConfiguration(
                            ExoPlayer.PreloadConfiguration(30 * C.MICROS_PER_SECOND),
                        )
                    }.also {
                        // Disable audio offload in safe mode (crash-loop detected)
                        if (!GlobalExceptionHandler.isSafeMode(context)) {
                            it.trackSelectionParameters = createAudioOffloadTrackSelectionParameters()
                        } else {
                            LogUtils.w("MediaModule", "Safe mode: skipping audio offload")
                        }
                    }
            } catch (e: Exception) {
                LogUtils.e("MediaModule", "Error creating ExoPlayer: ${e.message}", e)
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        LogUtils.d("MediaModule", "ExoPlayer singleton provided (${initDuration}ms)")

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
    @OptIn(UnstableApi::class, ExperimentalApi::class)
    public fun createExoPlayerWithProcessors(
        context: Context,
        settings: AudioProcessingSettings,
        handleAudioFocus: Boolean = true,
        processorChain: AudioProcessorFactory.ProcessorChainResult =
            AudioProcessorFactory.createProcessorChain(
                settings,
                AudioOutputBufferInfo.outputFramesPerBuffer(context),
            ),
    ): ExoPlayer {
        val initStart = System.currentTimeMillis()

        LogUtils.d("MediaModule", "Creating ExoPlayer with AudioProcessors...")

        val processors = processorChain.processors

        val extractorsFactory =
            DefaultExtractorsFactory()
                .setMp3ExtractorFlags(
                    Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                        Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING,
                )

        val player =
            try {
                // Create RenderersFactory with custom AudioSink that supports processors
                val renderersFactory =
                    object : DefaultRenderersFactory(context) {
                        override fun buildAudioSink(
                            context: Context,
                            enableFloatOutput: Boolean,
                            enableAudioOutputPlaybackParams: Boolean,
                        ): androidx.media3.exoplayer.audio.AudioSink {
                            // Media3 1.11.0: DefaultAudioSink.configure drops the whole
                            // AudioProcessorChain from the pipeline whenever
                            // setEnableFloatOutput(true) AND the input is hi-res/float PCM,
                            // so sink-level float output would silently bypass EQ/normalizer.
                            // Instead the chain negotiates float itself: FloatPcmOutputProcessor
                            // (appended last, after the int16-only DSP processors) returns
                            // ENCODING_PCM_FLOAT from onConfigure and DefaultAudioSink builds
                            // the AudioTrack with the pipeline's output encoding. Sink-level
                            // float stays off so the chain always runs — including hi-res input.
                            val chainProcessors =
                                if (processors.isEmpty()) {
                                    processors
                                } else {
                                    processors + FloatPcmOutputProcessor()
                                }
                            return androidx.media3.exoplayer.audio.DefaultAudioSink
                                .Builder(context)
                                // TrackedAudioProcessorChain feeds our custom skip-silence's
                                // skipped frames back to Media3's position tracking.
                                .setAudioProcessorChain(
                                    TrackedAudioProcessorChain(chainProcessors.toTypedArray()),
                                ).setEnableFloatOutput(processors.isEmpty() && enableFloatOutput)
                                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                                .build()
                        }
                    }

                val mediaSourceFactory =
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                        context,
                        extractorsFactory,
                    )

                val builder =
                    ExoPlayer
                        .Builder(context)
                        .experimentalSetDynamicSchedulingEnabled(true)
                        .setRenderersFactory(renderersFactory)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .setLoadControl(createOptimizedLoadControl(context))
                        .setHandleAudioBecomingNoisy(true)
                        .setWakeMode(C.WAKE_MODE_LOCAL)
                        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                        .setSeekForwardIncrementMs(SEEK_FORWARD_INCREMENT_MS)
                        // Our chain already includes a custom SkipSilenceAudioProcessor
                        // when enabled; keep Media3's built-in silence skipper off.
                        .setSkipSilenceEnabled(false)
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                                .build(),
                            handleAudioFocus,
                        )

                if (processors.isNotEmpty()) {
                    LogUtils.d(
                        "MediaModule",
                        "Attach ${processors.size} AudioProcessors to ExoPlayer via custom RenderersFactory",
                    )
                }

                builder
                    .build()
                    .also {
                        // #10: same delegation as singleton player — enable Media3 preload
                        // so LoadControl throttles contention vs active playback.
                        it.setPreloadConfiguration(
                            ExoPlayer.PreloadConfiguration(30 * C.MICROS_PER_SECOND),
                        )
                    }.also {
                        // Disable audio offload in safe mode (crash-loop detected)
                        if (!com.jabook.app.jabook.crash.GlobalExceptionHandler
                                .isSafeMode(context)
                        ) {
                            it.trackSelectionParameters =
                                createTrackSelectionParameters(
                                    settings = settings,
                                    hasProcessors = processors.isNotEmpty(),
                                )
                        } else {
                            LogUtils.w("MediaModule", "Safe mode: skipping audio offload for processor player")
                        }
                    }
            } catch (e: Exception) {
                LogUtils.e("MediaModule", "Error creating ExoPlayer with processors: ${e.message}", e)
                throw e
            }

        val initDuration = System.currentTimeMillis() - initStart
        LogUtils.d("MediaModule", "ExoPlayer with processors provided (${initDuration}ms)")

        return player
    }

    /**
     * Creates optimized LoadControl for audiobooks.
     *
     * Inspired by Easybook implementation with optimized buffer settings for audiobooks.
     * Settings are tuned for speech content (lower bitrate, predictable playback).
     */
    private fun createOptimizedLoadControl(context: Context): androidx.media3.exoplayer.LoadControl {
        val performanceClass = PerformanceUtils.getPerformanceClass(context)
        val loadControlBuilder = DefaultLoadControl.Builder()

        if (performanceClass == PerformanceClass.LOW) {
            // For low-end devices, reduce buffer sizes to save memory
            loadControlBuilder
                .setBufferDurationsMs(
                    15000, // minBufferMs: 15 seconds
                    30000, // maxBufferMs: 30 seconds
                    1500, // bufferForPlaybackMs: 1.5 seconds
                    3000, // bufferForPlaybackAfterRebufferMs: 3 seconds
                ).setTargetBufferBytes(32 * 1024 * 1024)
        } else {
            // For normal/high-end devices, use Easybook-optimized settings
            // These settings are optimized for audiobooks (speech content)
            loadControlBuilder
                .setBufferDurationsMs(
                    60000, // minBufferMs: 1 minute (Easybook: 60_000)
                    300000, // maxBufferMs: 5 minutes (Easybook: 300_000)
                    5000, // bufferForPlaybackMs: 5 seconds (Easybook: 5_000)
                    10000, // bufferForPlaybackAfterRebufferMs: 10 seconds (Easybook: 10_000)
                ).setBackBuffer(10000, true) // Easybook: backBuffer = 10000, retainBackBufferFromKeyframe = true
        }
        return loadControlBuilder.build()
    }

    @OptIn(UnstableApi::class)
    private fun createAudioOffloadTrackSelectionParameters(): TrackSelectionParameters =
        TrackSelectionParameters
            .Builder()
            .setMaxAudioBitrate(128_000)
            .setPreferredAudioLanguage("ru")
            .setAudioOffloadPreferences(
                TrackSelectionParameters
                    .AudioOffloadPreferences
                    .Builder()
                    .setAudioOffloadMode(
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                    ).setIsGaplessSupportRequired(true) // Required for seamless book chapter transitions
                    .setIsSpeedChangeSupportRequired(true) // Required for pitch-corrected speed
                    .build(),
            ).build()

    /**
     * Creates TrackSelectionParameters based on audio processing settings.
     * Gapless is enabled only when offload is disabled AND crossfade is disabled.
     * Offload uses a 6-flag AND gate (Rhythm pattern): only heavy DSP disables offload.
     * ponytail: skipSilence excluded from gate — offload stays enabled for skipSilence-only,
     * saving ~20-30% battery; if silence artifacts appear with offload, add skipSilence to gate.
     */
    @OptIn(UnstableApi::class)
    public fun createTrackSelectionParameters(
        settings: AudioProcessingSettings,
        hasProcessors: Boolean = AudioProcessingSettings.hasAnyProcessorEnabled(settings),
    ): TrackSelectionParameters {
        val isCrossfadeEnabled = settings.isCrossfadeEnabled
        // 6-flag DSP gate: heavy DSP that requires CPU — skipSilence intentionally excluded
        val hasDspForOffload =
            settings.normalizeVolume ||
                settings.speechCompressorLevel != SpeechCompressorLevel.Off ||
                settings.volumeBoostLevel != VolumeBoostLevel.Off ||
                settings.drcLevel != DRCLevel.Off ||
                settings.speechEnhancer ||
                settings.autoVolumeLeveling ||
                settings.noiseGateLevel != NoiseGateLevel.Off
        val isOffloadEnabled = !hasDspForOffload && !isCrossfadeEnabled

        // Gapless requires offload path without DSP and no crossfade
        val gaplessSupported = isOffloadEnabled

        LogUtils.d(
            "MediaModule",
            "Creating TrackSelectionParameters: gapless=$gaplessSupported " +
                "(dsp=$hasDspForOffload, processors=$hasProcessors, crossfade=$isCrossfadeEnabled, offload=$isOffloadEnabled)",
        )

        return TrackSelectionParameters
            .Builder()
            .setMaxAudioBitrate(128_000)
            .setPreferredAudioLanguage(settings.preferredLanguageCode)
            .setAudioOffloadPreferences(
                TrackSelectionParameters
                    .AudioOffloadPreferences
                    .Builder()
                    .setAudioOffloadMode(
                        if (isOffloadEnabled) {
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        } else {
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        },
                    ).setIsGaplessSupportRequired(gaplessSupported)
                    .setIsSpeedChangeSupportRequired(true) // Required for pitch correction
                    .build(),
            ).build()
    }

    private const val DEFAULT_CACHE_BYTES = 200L * 1024 * 1024 // 200 MB (fallback if StatFs fails)

    /** Chapter/paragraph seek step for audiobook navigation (rewind). */
    private const val SEEK_INCREMENT_MS = 10_000L

    // Forward seek step matching the app default. NOTE: MediaModule has no access to the
    // runtime skip-duration settings (those live in MediaSessionManager, updated at runtime
    // by the service); user-changed values are still honored via onMediaButtonEvent and the
    // rewind/forward custom commands. Wear/Auto seekBack()/seekForward() use these literals.
    private const val SEEK_FORWARD_INCREMENT_MS = 30_000L
}

/**
 * Hilt module for providing audio database and preferences.
 */
@Module
@InstallIn(SingletonComponent::class)
public object AudioDataModule {
    @Provides
    @Singleton
    public fun provideAudioDatabase(
        @ApplicationContext context: Context,
    ): com.jabook.app.jabook.audio.data.local.database.AudioDatabase {
        val builder =
            androidx.room.Room
                .databaseBuilder(
                    context,
                    com.jabook.app.jabook.audio.data.local.database.AudioDatabase::class.java,
                    "audio_database",
                )
                // Use coroutine context for queries (better integration with coroutines)
                .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.IO)
                // PreparedStatementCache is enabled by default (size 25) for better query performance.
                // Enforce WAL explicitly for deterministic behavior and improved read/write concurrency.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)

        // Add callback for database lifecycle events
        builder.addCallback(
            object : RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    LogUtils.i("Room", "AudioDatabase created")
                    // Enable foreign key constraints for referential integrity
                    db.execSQL("PRAGMA foreign_keys = ON")
                }

                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Enable foreign key constraints on each database open
                    db.execSQL("PRAGMA foreign_keys = ON")
                    // Optimize for better query performance
                    db.execSQL("PRAGMA optimize")
                }
            },
        )

        // Add query callback for logging in debug builds only
        try {
            val isDebug =
                Class
                    .forName("com.jabook.app.jabook.BuildConfig")
                    .getField("DEBUG")
                    .get(null) as? Boolean ?: false
            if (isDebug) {
                builder.setQueryCallback(
                    kotlinx.coroutines.Dispatchers.Unconfined,
                    RoomDatabase.QueryCallback { sqlQuery: String, _ ->
                        LogUtils.d(
                            "Room",
                            "AudioDB Query: $sqlQuery",
                        )
                    },
                )
            }
        } catch (e: Exception) {
            // BuildConfig not available, skip query callback
            LogUtils.d("Room", "BuildConfig not available, skipping query callback", e)
        }

        builder.addMigrations(AudioDatabaseMigrations.MIGRATION_2_3)
        builder.addMigrations(AudioDatabaseMigrations.MIGRATION_3_4)
        builder.addMigrations(AudioDatabaseMigrations.MIGRATION_4_5)
        builder.addMigrations(AudioDatabaseMigrations.MIGRATION_5_6)
        builder.addMigrations(AudioDatabaseMigrations.MIGRATION_6_7)

        return builder.build()
    }
}

/**
 * Hilt module for providing audio data repositories and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
public object AudioRepositoryModule {
    @Provides
    @Singleton
    public fun providePlaybackPositionDao(
        database: com.jabook.app.jabook.audio.data.local.database.AudioDatabase,
    ): com.jabook.app.jabook.audio.data.local.dao.PlaybackPositionDao = database.playbackPositionDao()

    @Provides
    @Singleton
    public fun provideListeningSessionDao(
        database: com.jabook.app.jabook.audio.data.local.database.AudioDatabase,
    ): com.jabook.app.jabook.audio.data.local.dao.ListeningSessionDao = database.listeningSessionDao()

    @Provides
    @Singleton
    public fun provideSavedPlayerStateDao(
        database: com.jabook.app.jabook.audio.data.local.database.AudioDatabase,
    ): com.jabook.app.jabook.audio.data.local.dao.SavedPlayerStateDao = database.savedPlayerStateDao()
}
