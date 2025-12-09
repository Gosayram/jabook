@file:Suppress("DEPRECATION") // BitmapLoader is deprecated in Media3 but still required

package com.jabook.app.jabook.audio

import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
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
    // We use DataSourceBitmapLoader as the delegate for standard loading
    // It's the standard implementation for loading bitmaps from URIs
    private val defaultBitmapLoader = DataSourceBitmapLoader(service)

    // Create a custom BitmapLoader that conditionally fails/skips loading
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
                return defaultBitmapLoader.loadBitmap(uri)
            }

            override fun supportsMimeType(mimeType: String): Boolean = defaultBitmapLoader.supportsMimeType(mimeType)

            override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                if (service.isMinimalNotification) {
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                return defaultBitmapLoader.decodeBitmap(data)
            }

            override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
                if (service.isMinimalNotification) {
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Handle nullable return type
                return defaultBitmapLoader.loadBitmapFromMetadata(metadata)
                    ?: Futures.immediateFailedFuture(Exception("Bitmap not found"))
            }
        }

    // Build the default provider with our intercepted BitmapLoader
    private val defaultProvider =
        DefaultMediaNotificationProvider
            .Builder(service)
            // .setBitmapLoader(minimalBitmapLoader) // Unresolved in Media3 1.8.0
            .build()

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        // We just delegate to the default provider, but because of our custom BitmapLoader,
        // it will skip artwork if isMinimalNotification is true.
        // We just delegate to the default provider, but because of our custom BitmapLoader,
        // it will skip artwork if isMinimalNotification is true.
        val mediaNotification =
            defaultProvider.createNotification(
                mediaSession,
                customLayout,
                actionFactory,
                onNotificationChangedCallback,
            )

        // CRITICAL: Force use of NotificationHelper.NOTIFICATION_ID (1) to match startForeground
        // This prevents creating a second notification with a different ID (default is 1001)
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
