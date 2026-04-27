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

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.ComposeMainActivity
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Encapsulates the PlayerNotificationManager setup logic extracted from AudioPlayerService.
 *
 * This is only used as a fallback when MediaLibrarySession is not available.
 * When MediaLibrarySession is active, it handles notifications automatically.
 *
 * @param service The host [MediaLibraryService] (AudioPlayerService)
 * @param scope Coroutine scope for async operations
 * @param notificationHelper Helper for notification channel and fallback creation
 * @param foregroundNotificationCoordinator Coordinator for foreground service start
 * @param getActivePlayer Function to get the current active ExoPlayer
 * @param getMediaLibrarySession Function to get the current MediaLibrarySession
 */
@OptIn(UnstableApi::class)
internal class PlayerNotificationSetup(
    private val service: MediaLibraryService,
    private val scope: CoroutineScope,
    private val notificationHelper: NotificationHelper,
    private val foregroundNotificationCoordinator: ForegroundNotificationCoordinator,
    private val getActivePlayer: () -> ExoPlayer,
    private val getMediaLibrarySession: () -> MediaLibrarySession?,
) {

    /**
     * Creates and configures the [PlayerNotificationManager].
     *
     * @return The configured PlayerNotificationManager, or null if setup fails
     */
    fun setup(): PlayerNotificationManager? {
        val channelId = NotificationHelper.CHANNEL_ID
        val exoPlayer = getActivePlayer()

        val manager = PlayerNotificationManager
            .Builder(service, NotificationHelper.NOTIFICATION_ID, channelId)
            .setMediaDescriptionAdapter(
                object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player): CharSequence {
                        return player.mediaMetadata.title ?: "JaBook"
                    }

                    override fun createCurrentContentIntent(player: Player): PendingIntent? {
                        val immutableFlag =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                PendingIntent.FLAG_IMMUTABLE
                            } else {
                                0
                            }
                        return PendingIntent.getActivity(
                            service,
                            0,
                            Intent(service, ComposeMainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                data = android.net.Uri.parse("jabook://player")
                            },
                            immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    }

                    override fun getCurrentContentText(player: Player): CharSequence? {
                        return player.mediaMetadata.artist ?: player.mediaMetadata.albumTitle
                    }

                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback,
                    ): android.graphics.Bitmap? {
                        val artworkUri = player.mediaMetadata.artworkUri
                        if (artworkUri != null) {
                            scope.launch {
                                try {
                                    // Coil 3: hardware bitmaps are auto-managed on API 30+
                                    val request = ImageRequest.Builder(service)
                                        .data(artworkUri)
                                        .size(256, 256)
                                        .build()
                                    val result = SingletonImageLoader.get(service).execute(request)
                                    if (result is SuccessResult) {
                                        val bitmap = result.image.toBitmap()
                                        callback.onBitmap(bitmap)
                                    }
                                } catch (e: Exception) {
                                    LogUtils.w("PlayerNotificationSetup", "Failed to load artwork: ${e.message}")
                                }
                            }
                        }
                        return null
                    }
                },
            ).setNotificationListener(
                object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationPosted(
                        notificationId: Int,
                        notification: android.app.Notification,
                        ongoing: Boolean,
                    ) {
                        // Force notification to be non-dismissible while service is foreground
                        notification.flags =
                            notification.flags or android.app.Notification.FLAG_ONGOING_EVENT

                        foregroundNotificationCoordinator.startWithFallback(
                            service = service,
                            notificationId = notificationId,
                            primaryNotification = notification,
                            fallbackNotificationProvider = {
                                notificationHelper.createFallbackNotification()
                            },
                            event = "player_notification_posted",
                        )
                    }
                },
            ).setSmallIconResourceId(R.drawable.ic_notification_logo)
            .build()

        // Set up invalidation pipeline to debounce notification updates
        val invalidationPipeline =
            PlayerNotificationInvalidationPipeline(
                scope = scope,
                invalidate = { manager.invalidate() },
            )
        invalidationPipeline.register(player = exoPlayer)

        manager.setPlayer(exoPlayer)
        manager.setUseNextAction(true)
        manager.setUsePreviousAction(true)
        manager.setUsePlayPauseActions(true)
        manager.setUseStopAction(false)

        // Force immediate invalidate to ensure startForeground() is called within 5 seconds
        invalidationPipeline.forceInitialStateInvalidate()

        return manager
    }
}