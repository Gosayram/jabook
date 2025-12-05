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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * BroadcastReceiver for handling Play/Pause actions through MediaSession using MediaController.
 *
 * This receiver uses MediaController to send commands directly to MediaSession,
 * which is more reliable than PendingIntent on Samsung and other devices.
 *
 * CRITICAL: This is the recommended approach for Play/Pause actions in Media3,
 * as it bypasses PendingIntent issues on Samsung One UI and other custom ROMs.
 *
 * The receiver creates a MediaController using SessionToken from AudioPlayerService
 * and uses it to send play/pause commands directly to MediaSession.
 */
class MediaSessionActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MediaSessionActionReceiver"
        const val ACTION_PLAY = "com.jabook.app.jabook.audio.MEDIA_SESSION_PLAY"
        const val ACTION_PAUSE = "com.jabook.app.jabook.audio.MEDIA_SESSION_PAUSE"
        private const val CONTROLLER_TIMEOUT_MS = 2000L
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        android.util.Log.i(
            TAG,
            "onReceive called! action: $action, intent: $intent, component: ${intent.component}",
        )

        when (action) {
            ACTION_PLAY -> {
                android.util.Log.i(TAG, "Processing PLAY command through MediaController")
                handlePlayCommand(context)
            }
            ACTION_PAUSE -> {
                android.util.Log.i(TAG, "Processing PAUSE command through MediaController")
                handlePauseCommand(context)
            }
            else -> {
                android.util.Log.w(TAG, "Unknown action: $action, available actions: $ACTION_PLAY, $ACTION_PAUSE")
            }
        }
    }

    /**
     * Handles Play command through MediaController.
     * Creates MediaController using SessionToken and sends play command directly to MediaSession.
     * Falls back to service intent if MediaController creation is too slow.
     */
    private fun handlePlayCommand(context: Context) {
        // Try MediaController first, but use shorter timeout for faster fallback
        var controllerFuture: ListenableFuture<MediaController>? = null
        try {
            // Create SessionToken for AudioPlayerService
            val sessionToken =
                SessionToken(
                    context,
                    ComponentName(context, AudioPlayerService::class.java),
                )

            // Create MediaController asynchronously
            controllerFuture =
                MediaController
                    .Builder(context, sessionToken)
                    .buildAsync()

            // Wait for controller to be ready (with shorter timeout for faster fallback)
            val controller =
                controllerFuture.get(500L, TimeUnit.MILLISECONDS)

            android.util.Log.d(
                TAG,
                "MediaController created successfully. Current state: playWhenReady=${controller.playWhenReady}, playbackState=${controller.playbackState}, mediaItemCount=${controller.mediaItemCount}",
            )

            // Send play command through MediaController
            // MediaController implements Player interface, use play() method (as in Media3 examples)
            controller.play()
            android.util.Log.i(
                TAG,
                "Play command sent successfully through MediaController. New state: playWhenReady=${controller.playWhenReady}, playbackState=${controller.playbackState}",
            )

            // Release controller after use
            MediaController.releaseFuture(controllerFuture)
        } catch (e: TimeoutException) {
            android.util.Log.w(TAG, "MediaController creation timeout, using service intent fallback", e)
            // Fallback to service intent (faster and more reliable)
            fallbackToServiceIntent(context, NotificationManager.ACTION_PLAY)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: ExecutionException) {
            android.util.Log.w(TAG, "Failed to create MediaController, using service intent fallback", e)
            // Fallback to service intent
            fallbackToServiceIntent(context, NotificationManager.ACTION_PLAY)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to handle PLAY command through MediaController, using service intent fallback", e)
            // Fallback to service intent
            fallbackToServiceIntent(context, NotificationManager.ACTION_PLAY)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }

    /**
     * Handles Pause command through MediaController.
     * Creates MediaController using SessionToken and sends pause command directly to MediaSession.
     * Falls back to service intent if MediaController creation is too slow.
     */
    private fun handlePauseCommand(context: Context) {
        // Try MediaController first, but use shorter timeout for faster fallback
        var controllerFuture: ListenableFuture<MediaController>? = null
        try {
            // Create SessionToken for AudioPlayerService
            val sessionToken =
                SessionToken(
                    context,
                    ComponentName(context, AudioPlayerService::class.java),
                )

            // Create MediaController asynchronously
            controllerFuture =
                MediaController
                    .Builder(context, sessionToken)
                    .buildAsync()

            // Wait for controller to be ready (with shorter timeout for faster fallback)
            val controller =
                controllerFuture.get(500L, TimeUnit.MILLISECONDS)

            android.util.Log.d(
                TAG,
                "MediaController created successfully. Current state: playWhenReady=${controller.playWhenReady}, playbackState=${controller.playbackState}",
            )

            // Send pause command through MediaController
            // MediaController implements Player interface, use pause() method (as in Media3 examples)
            controller.pause()
            android.util.Log.i(
                TAG,
                "Pause command sent successfully through MediaController. New state: playWhenReady=${controller.playWhenReady}, playbackState=${controller.playbackState}",
            )

            // Release controller after use
            MediaController.releaseFuture(controllerFuture)
        } catch (e: TimeoutException) {
            android.util.Log.w(TAG, "MediaController creation timeout, using service intent fallback", e)
            // Fallback to service intent (faster and more reliable)
            fallbackToServiceIntent(context, NotificationManager.ACTION_PAUSE)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: ExecutionException) {
            android.util.Log.w(TAG, "Failed to create MediaController, using service intent fallback", e)
            // Fallback to service intent
            fallbackToServiceIntent(context, NotificationManager.ACTION_PAUSE)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to handle PAUSE command through MediaController, using service intent fallback", e)
            // Fallback to service intent
            fallbackToServiceIntent(context, NotificationManager.ACTION_PAUSE)
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }

    /**
     * Fallback method: sends intent to AudioPlayerService if MediaController fails.
     * This ensures commands are still processed even if MediaController creation fails.
     */
    private fun fallbackToServiceIntent(
        context: Context,
        action: String,
    ) {
        try {
            android.util.Log.w(TAG, "Falling back to service intent for action: $action")
            val serviceIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    this.action = action
                    putExtra("use_media_session", true)
                }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to send fallback service intent", e)
        }
    }
}
