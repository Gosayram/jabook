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

import android.os.Build
import android.content.Context
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Centralized error handler for audio player components.
 * 
 * This class provides consistent error handling and logging across
 * all audio player components, with special handling for Android 14+.
 */
object ErrorHandler {
    
    /**
     * Handles playback errors with Android 14+ specific considerations.
     * 
     * @param tag Log tag for error identification
     * @param error The playback exception that occurred
     * @param context Additional context information
     */
    fun handlePlaybackError(tag: String, error: PlaybackException?, context: String? = null) {
        val errorMessage = error?.message ?: "Unknown error"
        // Use 0 as default error code (unknown error) if error is null
        val errorCode = error?.errorCode ?: 0
        
        android.util.Log.e(tag, "Playback error: $errorMessage (code: $errorCode)", error)
        
        // Android 14+ specific error handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            handleAndroid14SpecificError(tag, errorCode, errorMessage, context)
        }
        
        // Log additional context if provided
        context?.let {
            android.util.Log.e(tag, "Error context: $it")
        }
    }
    
    /**
     * Handles Android 14+ specific errors.
     * 
     * @param tag Log tag
     * @param errorCode Error code from PlaybackException
     * @param errorMessage Error message
     * @param context Additional context
     */
    private fun handleAndroid14SpecificError(
        tag: String,
        errorCode: Int,
        errorMessage: String,
        context: String?
    ) {
        when (errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                android.util.Log.w(tag, "Android 14+ network error: $errorMessage")
                // Android 14+ has stricter network requirements
                logAndroid14NetworkIssues(tag)
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                android.util.Log.w(tag, "Android 14+ HTTP error: $errorMessage")
                // Android 14+ may have additional HTTP restrictions
            }
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                android.util.Log.w(tag, "Android 14+ audio track error: $errorMessage")
                // Android 14+ has stricter audio focus management
            }
        }
    }
    
    /**
     * Logs Android 14+ specific network issues.
     * 
     * @param tag Log tag
     */
    private fun logAndroid14NetworkIssues(tag: String) {
        android.util.Log.w(tag, "Android 14+ network considerations:")
        android.util.Log.w(tag, "- Check network permissions")
        android.util.Log.w(tag, "- Verify cleartext traffic policy")
        android.util.Log.w(tag, "- Check foreground service restrictions")
    }
    
    /**
     * Handles general exceptions with Android 14+ considerations.
     * 
     * @param tag Log tag
     * @param exception The exception that occurred
     * @param context Additional context information
     */
    fun handleGeneralError(tag: String, exception: Exception, context: String? = null) {
        val errorMessage = exception.message ?: "Unknown exception"
        android.util.Log.e(tag, "General error: $errorMessage", exception)
        
        // Android 14+ specific error handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            handleAndroid14SpecificException(tag, exception, context)
        }
        
        // Log additional context if provided
        context?.let {
            android.util.Log.e(tag, "Error context: $it")
        }
    }
    
    /**
     * Handles Android 14+ specific exceptions.
     * 
     * @param tag Log tag
     * @param exception The exception that occurred
     * @param context Additional context
     */
    private fun handleAndroid14SpecificException(
        tag: String,
        exception: Exception,
        context: String?
    ) {
        when (exception) {
            is SecurityException -> {
                android.util.Log.w(tag, "Android 14+ security exception: ${exception.message}")
                // Android 14+ has stricter security policies
                logAndroid14SecurityIssues(tag)
            }
            is IllegalStateException -> {
                android.util.Log.w(tag, "Android 14+ illegal state: ${exception.message}")
                // Android 14+ may have stricter state requirements
            }
            // Note: OutOfMemoryError is an Error, not Exception, so it won't reach here
            // If OutOfMemoryError handling is needed, it should be done at a higher level
        }
    }
    
    /**
     * Logs Android 14+ specific security issues.
     * 
     * @param tag Log tag
     */
    private fun logAndroid14SecurityIssues(tag: String) {
        android.util.Log.w(tag, "Android 14+ security considerations:")
        android.util.Log.w(tag, "- Check foreground service permissions")
        android.util.Log.w(tag, "- Verify notification permissions")
        android.util.Log.w(tag, "- Check media session permissions")
    }
    
    /**
     * Creates a coroutine exception handler for Android 14+.
     * 
     * @param tag Log tag for error identification
     * @return CoroutineExceptionHandler
     */
    fun createCoroutineExceptionHandler(tag: String): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, exception ->
            // Convert Throwable to Exception if needed
            when (exception) {
                is Exception -> handleGeneralError(tag, exception, "Coroutine exception")
                else -> handleGeneralError(tag, Exception(exception), "Coroutine exception (converted from Throwable)")
            }
        }
    }
    
    /**
     * Validates Android 14+ specific requirements.
     * 
     * @param context Application context
     * @return true if all requirements are met, false otherwise
     */
    fun validateAndroid14Requirements(context: android.content.Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true // Not Android 14+, no special requirements
        }
        
        android.util.Log.d("ErrorHandler", "Validating Android 14+ requirements")
        
        // Check notification permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                android.util.Log.e("ErrorHandler", "Android 14+: Notifications are disabled")
                return false
            }
        }
        
        // Check foreground service permission
        // Note: FOREGROUND_SERVICE_MEDIA_PLAYBACK doesn't exist in Android 14
        // Use basic FOREGROUND_SERVICE permission check instead
        if (!hasPermission(context, android.Manifest.permission.FOREGROUND_SERVICE)) {
            android.util.Log.e("ErrorHandler", "Android 14+: Missing FOREGROUND_SERVICE permission")
            return false
        }
        
        android.util.Log.d("ErrorHandler", "Android 14+ requirements validated successfully")
        return true
    }
    
    /**
     * Checks if the app has a specific permission.
     * 
     * @param context Application context
     * @param permission Permission to check
     * @return true if permission is granted, false otherwise
     */
    private fun hasPermission(context: android.content.Context, permission: String): Boolean {
        return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}