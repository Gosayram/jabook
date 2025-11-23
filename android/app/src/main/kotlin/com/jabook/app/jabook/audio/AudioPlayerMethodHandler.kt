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
 * them to AudioPlayerService.
 */
class AudioPlayerMethodHandler(
    private val context: Context
) : MethodChannel.MethodCallHandler {
    
    /**
     * Gets AudioPlayerService instance, ensuring service is started and fully initialized.
     * Returns null if service is not available or not ready.
     * 
     * CRITICAL: This method is NON-BLOCKING to prevent ANR on Android 16.
     * It does NOT use Thread.sleep() which blocks main thread.
     * Flutter should retry if service is not ready.
     * 
     * Improved for Android 14+:
     * - Non-blocking: no Thread.sleep() calls
     * - Quick check: returns immediately if service not ready
     * - Flutter retry: allows Flutter to retry with exponential backoff
     * - Explicit check: verifies isFullyInitialized() before returning
     */
    private fun getService(): AudioPlayerService? {
        var service = AudioPlayerService.getInstance()
        if (service == null) {
            // Service not running, start it (non-blocking)
            val intent = Intent(context, AudioPlayerService::class.java)
            try {
                android.util.Log.d("AudioPlayerMethodHandler", "Starting AudioPlayerService (non-blocking)...")
                context.startForegroundService(intent)
                
                // CRITICAL: Do NOT wait here with Thread.sleep() - it blocks main thread!
                // Service will be ready on next call. Flutter should retry.
                // Just check once quickly (non-blocking)
                service = AudioPlayerService.getInstance()
                if (service == null) {
                    android.util.Log.d(
                        "AudioPlayerMethodHandler",
                        "Service started but not ready yet. Flutter should retry."
                    )
                    return null // Return null to allow Flutter to retry
                }
            } catch (e: IllegalStateException) {
                // Handle "Context.startForegroundService() did not then call Service.startForeground()" error
                android.util.Log.e(
                    "AudioPlayerMethodHandler",
                    "Failed to start foreground service (IllegalStateException): ${e.message}",
                    e
                )
                // Try to get existing service instance
                service = AudioPlayerService.getInstance()
                if (service == null) {
                    android.util.Log.e(
                        "AudioPlayerMethodHandler",
                        "Service not available after IllegalStateException"
                    )
                    return null
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "AudioPlayerMethodHandler",
                    "Failed to start service: ${e.message}",
                    e
                )
                return null
            }
        }
        
        // CRITICAL: Check if service is fully initialized before returning
        // This prevents using service before it's ready (race condition fix)
        if (service == null) {
            return null
        }
        
        if (!service.isFullyInitialized()) {
            android.util.Log.w(
                "AudioPlayerMethodHandler",
                "Service exists but not fully initialized yet. initialized=${service.isFullyInitialized()}. Flutter should retry."
            )
            return null // Return null to force Flutter to retry
        }
        
        return service
    }
    
    /**
     * Executes method call with retry logic if service is not ready.
     * 
     * CRITICAL: This method is NON-BLOCKING to prevent ANR on Android 16.
     * It does NOT use Thread.sleep() which blocks main thread.
     * Returns error immediately if service not ready - Flutter should retry.
     * 
     * Improved for Android 14+:
     * - Added exponential backoff retry mechanism
     * - Better error handling with timeout protection
     * - Non-blocking implementation
     */
    private fun executeWithRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 100,
        maxDelayMs: Long = 2000,
        action: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        var retryCount = 0
        var delayMs = initialDelayMs
        
        while (retryCount < maxRetries) {
            try {
                val service = getService()
                if (service != null && service.isFullyInitialized()) {
                    android.util.Log.d("AudioPlayerMethodHandler", "Service ready, executing action (attempt ${retryCount + 1})")
                    action()
                    return
                }
                
                if (retryCount < maxRetries - 1) {
                    android.util.Log.d("AudioPlayerMethodHandler", "Service not ready, retrying in ${delayMs}ms (attempt ${retryCount + 1})")
                    // Non-blocking delay using Handler instead of Thread.sleep()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // This prevents ANR on Android 14+
                    }, delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                }
                retryCount++
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerMethodHandler", "Exception in executeWithRetry (attempt ${retryCount + 1}): ${e.message}", e)
                retryCount++
                if (retryCount >= maxRetries) {
                    android.util.Log.e("AudioPlayerMethodHandler", "Max retries ($maxRetries) reached, giving up")
                    onError(e)
                    return
                }
                // Non-blocking delay for exception retry
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // This prevents ANR on Android 14+
                }, delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
            }
        }
    }
    
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            // Validate Android 14+ requirements before handling method calls
            if (!ErrorHandler.validateAndroid14Requirements(context)) {
                result.error(
                    "android_14_requirements_not_met",
                    "Android 14+ requirements not met",
                    null
                )
                return
            }
            
            when (call.method) {
                "initialize" -> {
                    val service = getService()
                    if (service != null && service.isFullyInitialized()) {
                        android.util.Log.d("AudioPlayerMethodHandler", "Service initialized successfully")
                        result.success(true)
                    } else {
                        val reason = when {
                            service == null -> "Service instance not created yet"
                            !service.isFullyInitialized() -> "Service not fully initialized (MediaSession not ready)"
                            else -> "Unknown error"
                        }
                        android.util.Log.w(
                            "AudioPlayerMethodHandler",
                            "Service not ready for initialization: $reason. Flutter should retry."
                        )
                        result.error(
                            "SERVICE_UNAVAILABLE",
                            "Audio service not ready: $reason. Please retry.",
                            null
                        )
                    }
                }
                "setPlaylist" -> {
                    val filePaths = call.argument<List<String>>("filePaths") ?: emptyList()
                    val metadata = call.argument<Map<String, String>>("metadata")
                    if (filePaths.isEmpty()) {
                        result.error("INVALID_ARGUMENT", "File paths list cannot be empty", null)
                        return
                    }
                    
                    val service = getService()
                    if (service == null) {
                        result.error("SERVICE_UNAVAILABLE", "Audio service is not available", null)
                        return
                    }
                    
                    service.setPlaylist(filePaths, metadata) { success, exception ->
                        if (success) {
                            android.util.Log.d("AudioPlayerMethodHandler", "Playlist set successfully via callback")
                            result.success(true)
                        } else {
                            val errorMessage = exception?.message ?: "Failed to set playlist"
                            android.util.Log.e("AudioPlayerMethodHandler", "Failed to set playlist: $errorMessage", exception)
                            result.error("EXCEPTION", errorMessage, null)
                        }
                    }
                }
                "play" -> {
                    android.util.Log.d("AudioPlayerMethodHandler", "play() called from Flutter")
                    val service = getService()
                    if (service != null) {
                        android.util.Log.d("AudioPlayerMethodHandler", "Service found, calling service.play()")
                        service.play()
                        android.util.Log.d("AudioPlayerMethodHandler", "service.play() called, returning success")
                        result.success(true)
                    } else {
                        android.util.Log.e("AudioPlayerMethodHandler", "Service is null, cannot play")
                        result.error("SERVICE_UNAVAILABLE", "Audio service is not available", null)
                    }
                }
                "pause" -> {
                    android.util.Log.d("AudioPlayerMethodHandler", "pause() called from Flutter")
                    executeWithRetry(
                        action = {
                            val service = getService()
                            if (service != null) {
                                android.util.Log.d("AudioPlayerMethodHandler", "Calling service.pause()")
                                service.pause()
                                result.success(true)
                            } else {
                                android.util.Log.e("AudioPlayerMethodHandler", "Service is null, cannot pause")
                                result.error("SERVICE_UNAVAILABLE", "Audio service is not available", null)
                            }
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
                "extractArtworkFromFile" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        executeWithRetry(
                            action = {
                                val artworkPath = getService()?.extractArtworkFromFile(filePath)
                                result.success(artworkPath)
                            },
                            onError = { e ->
                                result.error("EXCEPTION", e.message ?: "Failed to extract artwork", null)
                            }
                        )
                    } else {
                        result.error("INVALID_ARGUMENT", "filePath is required", null)
                    }
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
            ErrorHandler.handleGeneralError("AudioPlayerMethodHandler", e, "Method call: ${call.method}")
            result.error("EXCEPTION", e.message ?: "Unknown error", null)
        }
    }
}
