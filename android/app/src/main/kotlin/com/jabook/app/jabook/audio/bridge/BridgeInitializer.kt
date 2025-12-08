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

package com.jabook.app.jabook.audio.bridge

import android.content.Context
import com.jabook.app.jabook.audio.data.local.database.AudioDatabase
import com.jabook.app.jabook.audio.data.local.datastore.AudioPreferences
import com.jabook.app.jabook.audio.data.repository.ChapterMetadataRepository
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.audio.data.repository.PlaylistRepository
import com.jabook.app.jabook.audio.data.repository.SavedPlayerStateRepository
import com.jabook.app.jabook.audio.domain.usecase.LoadPlaylistUseCase
import com.jabook.app.jabook.audio.domain.usecase.NavigateTrackUseCase
import com.jabook.app.jabook.audio.domain.usecase.RestorePlaybackUseCase
import com.jabook.app.jabook.audio.domain.usecase.SavePositionUseCase
import com.jabook.app.jabook.audio.domain.usecase.SyncChaptersUseCase
import io.flutter.embedding.engine.FlutterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Initializer for FlutterBridge.
 *
 * Creates and registers bridge components with Flutter engine.
 * This allows gradual migration from old API to new bridge architecture.
 */
object BridgeInitializer {
    /**
     * Initializes and registers the new FlutterBridge with Flutter engine.
     *
     * This creates a parallel bridge that works alongside the existing
     * AudioPlayerMethodHandler for gradual migration.
     *
     * @param context Application context (must be Hilt-enabled)
     * @param flutterEngine Flutter engine instance
     */
    fun initializeBridge(
        context: Context,
        flutterEngine: FlutterEngine,
        eventChannelHandler: EventChannelHandler,
    ) {
        try {
            // Get dependencies from Hilt
            val application = context.applicationContext
            if (application !is com.jabook.app.jabook.JabookApplication) {
                android.util.Log.w(
                    "BridgeInitializer",
                    "Application is not JabookApplication, cannot initialize bridge with Hilt",
                )
                return
            }

            // Create CoroutineScope for bridge operations
            val bridgeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

            // Get database and preferences from Hilt
            // Note: This is a workaround - ideally we'd inject these, but we're in a static context
            // For now, we'll create them manually to avoid circular dependencies
            val database =
                androidx.room.Room
                    .databaseBuilder(
                        context,
                        AudioDatabase::class.java,
                        "audio_database",
                    ).build()

            val audioPreferences = AudioPreferences(context)

            // Create DAOs
            val playbackPositionDao = database.playbackPositionDao()
            val playlistDao = database.playlistDao()
            val chapterMetadataDao = database.chapterMetadataDao()
            val savedPlayerStateDao = database.savedPlayerStateDao()

            // Create repositories
            val playbackPositionRepository = PlaybackPositionRepository(playbackPositionDao)
            val playlistRepository = PlaylistRepository(playlistDao)
            val chapterMetadataRepository = ChapterMetadataRepository(chapterMetadataDao)
            val savedPlayerStateRepository = SavedPlayerStateRepository(savedPlayerStateDao)

            // Create use cases
            // Note: ChapterMapper is an object, no need to instantiate
            val loadPlaylistUseCase =
                LoadPlaylistUseCase(
                    playlistRepository,
                    chapterMetadataRepository,
                )
            val savePositionUseCase = SavePositionUseCase(playbackPositionRepository)
            val restorePlaybackUseCase = RestorePlaybackUseCase(playbackPositionRepository)
            val navigateTrackUseCase = NavigateTrackUseCase()
            val syncChaptersUseCase =
                SyncChaptersUseCase(
                    chapterMetadataRepository,
                    playlistRepository,
                )

            // Create handlers
            val methodChannelHandler =
                MethodChannelHandler(
                    loadPlaylistUseCase,
                    savePositionUseCase,
                    restorePlaybackUseCase,
                    navigateTrackUseCase,
                    syncChaptersUseCase,
                    savedPlayerStateRepository,
                    bridgeScope,
                    context,
                )

            // Register MethodChannel (using different name for parallel operation)
            val methodChannel =
                io.flutter.plugin.common.MethodChannel(
                    flutterEngine.dartExecutor.binaryMessenger,
                    "com.jabook.app.jabook/audio_player_v2", // Different name for parallel operation
                )
            methodChannel.setMethodCallHandler(methodChannelHandler)

            // Register EventChannel
            val eventChannel =
                io.flutter.plugin.common.EventChannel(
                    flutterEngine.dartExecutor.binaryMessenger,
                    "com.jabook.app.jabook/audio_player_events_v2", // Different name for parallel operation
                )
            eventChannel.setStreamHandler(
                object : io.flutter.plugin.common.EventChannel.StreamHandler {
                    override fun onListen(
                        arguments: Any?,
                        events: io.flutter.plugin.common.EventChannel.EventSink,
                    ) {
                        eventChannelHandler.setEventSink(events)
                    }

                    override fun onCancel(arguments: Any?) {
                        eventChannelHandler.clearEventSink()
                    }
                },
            )

            android.util.Log.i("BridgeInitializer", "New FlutterBridge initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("BridgeInitializer", "Failed to initialize FlutterBridge", e)
        }
    }
}
