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
import android.content.Intent
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel handler for audio player operations.
 *
 * This class handles all method calls from Flutter and delegates
 * them to the AudioPlayerService.
 */
class AudioPlayerMethodHandler(
    private val context: Context
) : MethodChannel.MethodCallHandler {
    
    /**
     * Gets AudioPlayerService instance, ensuring service is started.
     * Returns null if service is not available.
     */
    private fun getService(): AudioPlayerService? {
        var service = AudioPlayerService.getInstance()
        if (service == null) {
            // Service not running, start it
            val intent = Intent(context, AudioPlayerService::class.java)
            try {
                context.startForegroundService(intent)
                // Wait a bit for service to initialize
                Thread.sleep(100)
                service = AudioPlayerService.getInstance()
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerMethodHandler", "Failed to start service", e)
            }
        }
        return service
    }
    
    /**
     * Executes method call with retry logic if service is not ready.
     */
    private fun executeWithRetry(
        maxRetries: Int = 3,
        delayMs: Long = 200,
        action: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        var retries = 0
        while (retries < maxRetries) {
            try {
                val service = getService()
                if (service != null) {
                    action()
                    return
                } else {
                    retries++
                    if (retries < maxRetries) {
                        Thread.sleep(delayMs)
                    }
                }
            } catch (e: Exception) {
                if (retries >= maxRetries - 1) {
                    onError(e)
                    return
                }
                retries++
                Thread.sleep(delayMs)
            }
        }
        onError(Exception("Service not available after $maxRetries retries"))
    }
    
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "initialize" -> {
                    val service = getService()
                    if (service != null) {
                        result.success(true)
                    } else {
                        result.error("SERVICE_UNAVAILABLE", "Failed to initialize audio service", null)
                    }
                }
                "setPlaylist" -> {
                    val filePaths = call.argument<List<String>>("filePaths") ?: emptyList()
                    val metadata = call.argument<Map<String, String>>("metadata")
                    if (filePaths.isEmpty()) {
                        result.error("INVALID_ARGUMENT", "File paths list cannot be empty", null)
                        return
                    }
                    executeWithRetry(
                        action = {
                            getService()?.setPlaylist(filePaths, metadata)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to set playlist", null)
                        }
                    )
                }
                "play" -> {
                    executeWithRetry(
                        action = {
                            getService()?.play()
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to play", null)
                        }
                    )
                }
                "pause" -> {
                    executeWithRetry(
                        action = {
                            getService()?.pause()
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to pause", null)
                        }
                    )
                }
                "stop" -> {
                    executeWithRetry(
                        action = {
                            getService()?.stop()
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to stop", null)
                        }
                    )
                }
                "seek" -> {
                    // Handle both Int and Long types from Flutter MethodChannel
                    val positionMsArg = call.argument<Any>("positionMs")
                    val positionMs: Long = when (positionMsArg) {
                        is Long -> positionMsArg
                        is Int -> positionMsArg.toLong()
                        is Number -> positionMsArg.toLong()
                        null -> {
                            result.error("INVALID_ARGUMENT", "positionMs is required", null)
                            return
                        }
                        else -> {
                            result.error("INVALID_ARGUMENT", "positionMs must be a number", null)
                            return
                        }
                    }
                    
                    executeWithRetry(
                        action = {
                            getService()?.seekTo(positionMs)
                            result.success(true)
                        },
                        onError = { e ->
                            android.util.Log.e("AudioPlayerMethodHandler", "Failed to seek: positionMs=$positionMs", e)
                            result.error("EXCEPTION", e.message ?: "Failed to seek", null)
                        }
                    )
                }
                "setSpeed" -> {
                    val speed = call.argument<Double>("speed")?.toFloat() ?: 1.0f
                    executeWithRetry(
                        action = {
                            getService()?.setSpeed(speed)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to set speed", null)
                        }
                    )
                }
                "setRepeatMode" -> {
                    val repeatMode = call.argument<Int>("repeatMode") ?: 0
                    executeWithRetry(
                        action = {
                            getService()?.setRepeatMode(repeatMode)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to set repeat mode", null)
                        }
                    )
                }
                "getRepeatMode" -> {
                    val repeatMode = getService()?.getRepeatMode() ?: 0
                    result.success(repeatMode)
                }
                "setShuffleModeEnabled" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: false
                    executeWithRetry(
                        action = {
                            getService()?.setShuffleModeEnabled(enabled)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to set shuffle mode", null)
                        }
                    )
                }
                "getShuffleModeEnabled" -> {
                    val enabled = getService()?.getShuffleModeEnabled() ?: false
                    result.success(enabled)
                }
                "getCurrentMediaItemInfo" -> {
                    val info = getService()?.getCurrentMediaItemInfo() ?: emptyMap()
                    result.success(info)
                }
                "getPlaylistInfo" -> {
                    val info = getService()?.getPlaylistInfo() ?: emptyMap()
                    result.success(info)
                }
                "getPosition" -> {
                    val position = getService()?.getCurrentPosition() ?: 0L
                    result.success(position)
                }
                "getDuration" -> {
                    val duration = getService()?.getDuration() ?: 0L
                    result.success(duration)
                }
                "getState" -> {
                    val state = getService()?.getPlayerState() ?: emptyMap()
                    result.success(state)
                }
                "next" -> {
                    executeWithRetry(
                        action = {
                            getService()?.next()
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to skip next", null)
                        }
                    )
                }
                "previous" -> {
                    executeWithRetry(
                        action = {
                            getService()?.previous()
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to skip previous", null)
                        }
                    )
                }
                "seekToTrack" -> {
                    val index = call.argument<Int>("index") ?: 0
                    executeWithRetry(
                        action = {
                            getService()?.seekToTrack(index)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to seek to track", null)
                        }
                    )
                }
                "updateMetadata" -> {
                    val metadata = call.argument<Map<String, String>>("metadata")
                    if (metadata != null) {
                        executeWithRetry(
                            action = {
                                getService()?.updateMetadata(metadata)
                                result.success(true)
                            },
                            onError = { e ->
                                result.error("EXCEPTION", e.message ?: "Failed to update metadata", null)
                            }
                        )
                    } else {
                        result.success(true)
                    }
                }
                "seekToTrackAndPosition" -> {
                    val trackIndex = call.argument<Int>("trackIndex") ?: 0
                    
                    // Handle both Int and Long types from Flutter MethodChannel
                    val positionMsArg = call.argument<Any>("positionMs")
                    val positionMs: Long = when (positionMsArg) {
                        is Long -> positionMsArg
                        is Int -> positionMsArg.toLong()
                        is Number -> positionMsArg.toLong()
                        null -> {
                            result.error("INVALID_ARGUMENT", "positionMs is required", null)
                            return
                        }
                        else -> {
                            result.error("INVALID_ARGUMENT", "positionMs must be a number", null)
                            return
                        }
                    }
                    
                    executeWithRetry(
                        action = {
                            getService()?.seekToTrackAndPosition(trackIndex, positionMs)
                            result.success(true)
                        },
                        onError = { e ->
                            android.util.Log.e("AudioPlayerMethodHandler", "Failed to seek to track and position: trackIndex=$trackIndex, positionMs=$positionMs", e)
                            result.error("EXCEPTION", e.message ?: "Failed to seek to track and position", null)
                        }
                    )
                }
                "rewind" -> {
                    val seconds = call.argument<Int>("seconds") ?: 15
                    executeWithRetry(
                        action = {
                            getService()?.rewind(seconds)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to rewind", null)
                        }
                    )
                }
                "forward" -> {
                    val seconds = call.argument<Int>("seconds") ?: 30
                    executeWithRetry(
                        action = {
                            getService()?.forward(seconds)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to forward", null)
                        }
                    )
                }
                "setPlaybackProgress" -> {
                    val filePaths = call.argument<List<String>>("filePaths") ?: emptyList()
                    val progressSeconds = call.argument<Double>("progressSeconds")
                    
                    executeWithRetry(
                        action = {
                            getService()?.setPlaybackProgress(filePaths, progressSeconds)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to set playback progress", null)
                        }
                    )
                }
                "startTimer" -> {
                    val delayInSeconds = call.argument<Double>("delayInSeconds") ?: 0.0
                    val option = call.argument<Int>("option") ?: 0
                    executeWithRetry(
                        action = {
                            getService()?.startTimer(delayInSeconds, option)
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to start timer", null)
                        }
                    )
                }
                "stopTimer" -> {
                    executeWithRetry(
                        action = {
                            getService()?.stopTimer()
                            result.success(true)
                        },
                        onError = { e ->
                            result.error("EXCEPTION", e.message ?: "Failed to stop timer", null)
                        }
                    )
                }
                "dispose" -> {
                    // Service will continue running in foreground
                    // Just acknowledge disposal
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerMethodHandler", "Error handling method call: ${call.method}", e)
            result.error("EXCEPTION", e.message ?: "Unknown error", null)
        }
    }
}

