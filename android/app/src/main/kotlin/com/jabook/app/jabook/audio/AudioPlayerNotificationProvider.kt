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

@file:Suppress("DEPRECATION") // BitmapLoader is deprecated in Media3 but still required

package com.jabook.app.jabook.audio

import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.BitmapLoader // Deprecated but still used in Media3 1.8.0
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Custom MediaNotification.Provider to handle "Minimal Notification" mode.
 * We use the DefaultMediaNotificationProvider but can intercept Bitmap loading.
 */
@OptIn(UnstableApi::class)
class AudioPlayerNotificationProvider(
    private val service: AudioPlayerService,
) : MediaNotification.Provider {
    // Use GlideBitmapLoader for better performance and caching (inspired by Easybook)
    // Glide provides superior memory management and async loading compared to DataSourceBitmapLoader
    private val glideBitmapLoader = GlideBitmapLoader(service)

    // Create a custom BitmapLoader that conditionally fails/skips loading for minimal mode
    // Note: BitmapLoader is deprecated in Media3 but still required for compatibility
    @Suppress("DEPRECATION")
    private val minimalBitmapLoader =
        object : BitmapLoader {
            override fun loadBitmap(uri: android.net.Uri): ListenableFuture<Bitmap> {
                if (service.isMinimalNotification) {
                    // Return failed future to skip artwork loading
                    // This causes DefaultMediaNotificationProvider to use fallback/no artwork
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Use Glide for better performance
                return glideBitmapLoader.loadBitmap(uri)
            }

            override fun supportsMimeType(mimeType: String): Boolean = glideBitmapLoader.supportsMimeType(mimeType)

            override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                if (service.isMinimalNotification) {
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Use Glide for better performance
                return glideBitmapLoader.decodeBitmap(data)
            }

            override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
                if (service.isMinimalNotification) {
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Use Glide for better performance
                return glideBitmapLoader.loadBitmapFromMetadata(metadata)
            }
        }

    // Build the default provider with explicit channel configuration
    // Note: DefaultMediaNotificationProvider.Builder only supports setChannelId() in Media3
    // Small icon is configured via drawable resource override (media3_notification_small_icon.xml)
    private val defaultProvider: DefaultMediaNotificationProvider =
        DefaultMediaNotificationProvider
            .Builder(service)
            .setChannelId(NotificationHelper.CHANNEL_ID)
            // .setBitmapLoader(minimalBitmapLoader) // Unresolved in Media3 1.8.0
            .build()
            .also {
                android.util.Log.i(
                    "AudioPlayerNotificationProvider",
                    "DefaultMediaNotificationProvider built with Channel ID: ${NotificationHelper.CHANNEL_ID}",
                )
            }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        // Use DefaultMediaNotificationProvider which handles MediaStyle automatically
        // This ensures Quick Settings controls support (Android 11+) and SeekBar (Android 13+)
        android.util.Log.d("AudioPlayerNotificationProvider", "createNotification called. Session: ${mediaSession.token}")
        val mediaNotification =
            defaultProvider.createNotification(
                mediaSession,
                customLayout,
                actionFactory,
                onNotificationChangedCallback,
            )
        android.util.Log.d("AudioPlayerNotificationProvider", "Notification created: ${mediaNotification.notification}")

        // Ensure we use consistently the same notification ID
        return MediaNotification(
            NotificationHelper.NOTIFICATION_ID,
            mediaNotification.notification,
        )
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = defaultProvider.handleCustomCommand(session, action, extras)
}
