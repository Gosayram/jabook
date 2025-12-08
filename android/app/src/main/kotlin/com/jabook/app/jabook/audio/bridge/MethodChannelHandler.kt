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
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.SavedPlayerStateRepository
import com.jabook.app.jabook.audio.domain.usecase.LoadPlaylistUseCase
import com.jabook.app.jabook.audio.domain.usecase.NavigateTrackUseCase
import com.jabook.app.jabook.audio.domain.usecase.RestorePlaybackUseCase
import com.jabook.app.jabook.audio.domain.usecase.SavePositionUseCase
import com.jabook.app.jabook.audio.domain.usecase.SyncChaptersUseCase
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Handler for MethodChannel calls from Flutter.
 *
 * Thin layer that routes calls to use cases.
 * All business logic is in use cases, not here.
 */
class MethodChannelHandler
    @Inject
    constructor(
        private val loadPlaylistUseCase: LoadPlaylistUseCase,
        private val savePositionUseCase: SavePositionUseCase,
        private val restorePlaybackUseCase: RestorePlaybackUseCase,
        private val navigateTrackUseCase: NavigateTrackUseCase,
        private val syncChaptersUseCase: SyncChaptersUseCase,
        private val savedPlayerStateRepository: SavedPlayerStateRepository,
        private val coroutineScope: CoroutineScope,
        private val context: Context,
    ) : MethodChannel.MethodCallHandler {
        override fun onMethodCall(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            when (call.method) {
                "loadPlaylist" -> handleLoadPlaylist(call, result)
                "setPlaylist" -> handleSetPlaylist(call, result)
                "savePosition" -> handleSavePosition(call, result)
                "restorePlayback" -> handleRestorePlayback(call, result)
                "restorePosition" -> handleRestorePosition(call, result)
                "navigateTrack" -> handleNavigateTrack(call, result)
                "seekToTrack" -> handleSeekToTrack(call, result)
                "updateMetadata" -> handleUpdateMetadata(call, result)
                "initialize" -> handleInitialize(call, result)
                "syncChapters" -> handleSyncChapters(call, result)
                "play" -> handlePlay(call, result)
                "pause" -> handlePause(call, result)
                "seek" -> handleSeek(call, result)
                "setSpeed" -> handleSetSpeed(call, result)
                "next" -> handleNext(call, result)
                "previous" -> handlePrevious(call, result)
                "stop" -> handleStop(call, result)
                "stopServiceAndExit" -> handleStopServiceAndExit(call, result)
                "restoreFullState" -> handleRestoreFullState(call, result)
                "updateSavedStateSettings" -> handleUpdateSavedStateSettings(call, result)
                else -> result.notImplemented()
            }
        }

        private fun handleLoadPlaylist(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val bookId =
                call.argument<String>("bookId")
                    ?: return result.error("INVALID_ARGUMENT", "bookId is required", null)

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // LoadPlaylistUseCase returns Flow, so we need to get first value
                    val flowResult = loadPlaylistUseCase(bookId)
                    val playlistResult = flowResult.first()
                    withContext(Dispatchers.Main) {
                        when (playlistResult) {
                            is Result.Success -> {
                                val playlist = playlistResult.data
                                result.success(
                                    mapOf(
                                        "bookId" to playlist.bookId,
                                        "bookTitle" to playlist.bookTitle,
                                        "currentIndex" to playlist.currentIndex,
                                        "chapters" to
                                            playlist.chapters.map { chapter ->
                                                mapOf(
                                                    "id" to chapter.id,
                                                    "title" to chapter.title,
                                                    "fileIndex" to chapter.fileIndex,
                                                    "filePath" to chapter.filePath,
                                                    "startTime" to chapter.startTime,
                                                    "endTime" to chapter.endTime,
                                                    "duration" to chapter.duration,
                                                )
                                            },
                                    ),
                                )
                            }
                            is Result.Error -> {
                                result.error("LOAD_ERROR", playlistResult.exception.message, null)
                            }
                            is Result.Loading -> {
                                result.error("LOADING", "Playlist is still loading", null)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        result.error("EXCEPTION", e.message, null)
                    }
                }
            }
        }

        private fun handleSavePosition(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val bookId =
                call.argument<String>("bookId")
                    ?: return result.error("INVALID_ARGUMENT", "bookId is required", null)
            val trackIndex =
                call.argument<Int>("trackIndex")
                    ?: return result.error("INVALID_ARGUMENT", "trackIndex is required", null)
            val position =
                call.argument<Long>("position")
                    ?: return result.error("INVALID_ARGUMENT", "position is required", null)

            coroutineScope.launch(Dispatchers.IO) {
                val saveResult = savePositionUseCase(bookId, trackIndex, position)
                withContext(Dispatchers.Main) {
                    when (saveResult) {
                        is Result.Success -> result.success(true)
                        is Result.Error -> result.error("SAVE_ERROR", saveResult.exception.message, null)
                        is Result.Loading -> result.error("LOADING", "Save is in progress", null)
                    }
                }
            }
        }

        private fun handleRestorePlayback(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val bookId =
                call.argument<String>("bookId")
                    ?: return result.error("INVALID_ARGUMENT", "bookId is required", null)

            coroutineScope.launch(Dispatchers.IO) {
                val restoreResult = restorePlaybackUseCase(bookId)
                withContext(Dispatchers.Main) {
                    when (restoreResult) {
                        is Result.Success -> {
                            val playbackState = restoreResult.data
                            if (playbackState != null) {
                                result.success(
                                    mapOf(
                                        "trackIndex" to playbackState.currentTrackIndex,
                                        "position" to playbackState.currentPosition,
                                        "playbackSpeed" to playbackState.playbackSpeed,
                                    ),
                                )
                            } else {
                                result.success(null) // No saved position
                            }
                        }
                        is Result.Error -> result.error("RESTORE_ERROR", restoreResult.exception.message, null)
                        is Result.Loading -> result.error("LOADING", "Restore is in progress", null)
                    }
                }
            }
        }

        private fun handleNavigateTrack(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            // This would require the current playlist, which should be passed or retrieved
            // For now, this is a placeholder that will be implemented during integration
            result.notImplemented()
        }

        private fun handleSyncChapters(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val bookId =
                call.argument<String>("bookId")
                    ?: return result.error("INVALID_ARGUMENT", "bookId is required", null)
            val bookTitle =
                call.argument<String>("bookTitle")
                    ?: return result.error("INVALID_ARGUMENT", "bookTitle is required", null)
            val chapters =
                call.argument<List<Map<String, Any>>>("chapters")
                    ?: return result.error("INVALID_ARGUMENT", "chapters is required", null)
            val filePaths =
                call.argument<List<String>>("filePaths")
                    ?: return result.error("INVALID_ARGUMENT", "filePaths is required", null)

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Convert chapters from Flutter format to domain models
                    val domainChapters =
                        chapters.mapIndexed { index, chapterMap ->
                            com.jabook.app.jabook.audio.core.model.Chapter(
                                id = chapterMap["id"] as? String ?: "${bookId}_$index",
                                title = chapterMap["title"] as? String ?: "",
                                fileIndex = chapterMap["fileIndex"] as? Int ?: index,
                                filePath = chapterMap["filePath"] as? String,
                                startTime = (chapterMap["startTime"] as? Number)?.toLong() ?: 0L,
                                endTime = (chapterMap["endTime"] as? Number)?.toLong(),
                                duration = (chapterMap["duration"] as? Number)?.toLong(),
                            )
                        }

                    val syncResult = syncChaptersUseCase(bookId, bookTitle, domainChapters, filePaths)
                    withContext(Dispatchers.Main) {
                        when (syncResult) {
                            is Result.Success -> result.success(true)
                            is Result.Error -> result.error("SYNC_ERROR", syncResult.exception.message, null)
                            is Result.Loading -> result.error("LOADING", "Sync is in progress", null)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        result.error("EXCEPTION", e.message, null)
                    }
                }
            }
        }

        private fun handleSetPlaylist(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val filePaths = call.argument<List<String>>("filePaths") ?: emptyList()
            val metadata = call.argument<Map<String, String>>("metadata")
            val initialTrackIndex = call.argument<Int?>("initialTrackIndex")
            val initialPositionArg = call.argument<Any?>("initialPosition")
            val initialPosition: Long? =
                when (initialPositionArg) {
                    is Long -> initialPositionArg
                    is Int -> initialPositionArg.toLong()
                    is Number -> initialPositionArg.toLong()
                    null -> null
                    else -> null
                }
            val groupPath = call.argument<String?>("groupPath")

            if (filePaths.isEmpty()) {
                result.error("INVALID_ARGUMENT", "File paths list cannot be empty", null)
                return
            }

            // Use old AudioPlayerService for now (temporary during migration)
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                // Start service if not running
                val intent = android.content.Intent(context, AudioPlayerService::class.java)
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    result.error("SERVICE_UNAVAILABLE", "Failed to start service: ${e.message}", null)
                    return
                }
                // Wait a bit for service to start
                var retries = 0
                var serviceReady = false
                while (retries < 10 && !serviceReady) {
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.delay(100)
                    }
                    val startedService = AudioPlayerService.getInstance()
                    if (startedService != null && startedService.isFullyInitialized()) {
                        serviceReady = true
                        startedService.setPlaylist(
                            filePaths,
                            metadata,
                            initialTrackIndex,
                            initialPosition,
                            groupPath,
                        ) { success, exception ->
                            if (success) {
                                result.success(true)
                            } else {
                                val errorMessage = exception?.message ?: "Failed to set playlist"
                                result.error("EXCEPTION", errorMessage, null)
                            }
                        }
                        return
                    }
                    retries++
                }
                result.error("SERVICE_UNAVAILABLE", "Service not ready after starting", null)
                return
            }

            if (!service.isFullyInitialized()) {
                result.error("SERVICE_UNAVAILABLE", "Service is not fully initialized", null)
                return
            }

            service.setPlaylist(
                filePaths,
                metadata,
                initialTrackIndex,
                initialPosition,
                groupPath,
            ) { success, exception ->
                if (success) {
                    result.success(true)
                } else {
                    val errorMessage = exception?.message ?: "Failed to set playlist"
                    result.error("EXCEPTION", errorMessage, null)
                }
            }
        }

        private fun handleRestorePosition(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val groupPath =
                call.argument<String>("groupPath")
                    ?: return result.error("INVALID_ARGUMENT", "groupPath is required", null)
            val fileCount = call.argument<Int?>("fileCount")

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // Use old AudioPlayerService for now
                    val service = AudioPlayerService.getInstance()
                    if (service == null) {
                        withContext(Dispatchers.Main) {
                            result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                        }
                        return@launch
                    }

                    // Use playerPersistenceManager to restore position
                    val position = service.playerPersistenceManager.restorePosition(groupPath, fileCount)
                    withContext(Dispatchers.Main) {
                        if (position != null) {
                            result.success(
                                mapOf(
                                    "trackIndex" to position["trackIndex"],
                                    "positionMs" to position["positionMs"],
                                ),
                            )
                        } else {
                            result.success(null)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        result.error("EXCEPTION", e.message, null)
                    }
                }
            }
        }

        private fun handleSeekToTrack(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val trackIndex =
                call.argument<Int>("trackIndex")
                    ?: return result.error("INVALID_ARGUMENT", "trackIndex is required", null)

            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }

            try {
                service.seekToTrack(trackIndex)
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleUpdateMetadata(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val metadata =
                call.argument<Map<String, String>>("metadata")
                    ?: return result.error("INVALID_ARGUMENT", "metadata is required", null)

            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }

            try {
                service.updateMetadata(metadata)
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleInitialize(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                // Start service if not running
                val intent = android.content.Intent(context, AudioPlayerService::class.java)
                try {
                    context.startForegroundService(intent)
                    // Wait for service to initialize
                    var retries = 0
                    while (retries < 20) {
                        kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.delay(100)
                        }
                        val startedService = AudioPlayerService.getInstance()
                        if (startedService != null && startedService.isFullyInitialized()) {
                            result.success(true)
                            return
                        }
                        retries++
                    }
                    result.error("SERVICE_UNAVAILABLE", "Service not ready after starting", null)
                } catch (e: Exception) {
                    result.error("SERVICE_UNAVAILABLE", "Failed to start service: ${e.message}", null)
                }
                return
            }

            if (service.isFullyInitialized()) {
                result.success(true)
            } else {
                result.error("SERVICE_UNAVAILABLE", "Service is not fully initialized", null)
            }
        }

        private fun handlePlay(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.play()
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handlePause(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.pause()
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleSeek(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val position =
                call.argument<Long>("position")
                    ?: return result.error("INVALID_ARGUMENT", "position is required", null)

            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.seekTo(position)
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleSetSpeed(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val speed =
                call.argument<Double>("speed")?.toFloat()
                    ?: return result.error("INVALID_ARGUMENT", "speed is required", null)

            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.setPlaybackSpeed(speed)
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleNext(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.next()
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handlePrevious(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.previous()
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleStop(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                result.error("SERVICE_UNAVAILABLE", "Service is not available", null)
                return
            }
            try {
                service.stop()
                result.success(true)
            } catch (e: Exception) {
                result.error("EXCEPTION", e.message, null)
            }
        }

        private fun handleStopServiceAndExit(
            call: MethodCall,
            result: MethodChannel.Result,
        ) {
            android.util.Log.i("MethodChannelHandler", "stopServiceAndExit called from Flutter")
            try {
                // Check if service is initialized before sending exit intent
                // This prevents accidental exit during service initialization
                val service = AudioPlayerService.getInstance()
                val isInitialized = service?.isFullyInitialized() ?: false
                android.util.Log.d(
                    "MethodChannelHandler",
                    "stopServiceAndExit: service=${service != null}, isFullyInitialized=$isInitialized",
                )
                if (service != null && isInitialized) {
                    // Service is ready, send exit intent
                    val exitIntent =
                        android.content.Intent(context, AudioPlayerService::class.java).apply {
                            action = AudioPlayerService.ACTION_EXIT_APP
                        }
                    // Service is already running, just send intent
                    android.util.Log.d("MethodChannelHandler", "Sending ACTION_EXIT_APP intent to service")
                    context.startService(exitIntent)
                    android.util.Log.i("MethodChannelHandler", "Exit intent sent to initialized service")
                    result.success(true)
                } else {
                    android.util.Log.w(
                        "MethodChannelHandler",
                        "stopServiceAndExit called but service not initialized yet (service=${service != null}, initialized=$isInitialized), ignoring to prevent white screen",
                    )
                    result.success(false) // Return false to indicate exit was not processed
                }
            } catch (e: Exception) {
                android.util.Log.e("MethodChannelHandler", "Error in stopServiceAndExit: ${e.message}", e)
                result.error("EXCEPTION", e.message ?: "Failed to stop service and exit", null)
            }
        }
    }
