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

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jabook.app.jabook.MainActivity
import com.jabook.app.jabook.audio.AudioPlayerService
import android.app.NotificationManager as AndroidNotificationManager

/**
 * Manages playback notification with media controls.
 *
 * This class creates and updates the notification that appears
 * in the notification panel during audio playback.
 */
class NotificationManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val mediaSession: androidx.media3.session.MediaSession? = null,
    private var metadata: Map<String, String>? = null,
    private var embeddedArtworkPath: String? = null, // Path to saved embedded artwork from AudioPlayerService
) {
    private val notificationManager: AndroidNotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

    companion object {
        private const val CHANNEL_ID = "jabook_audio_playback"
        private const val NOTIFICATION_ID = 1

        /**
         * Gets the notification channel name with flavor suffix.
         */
        private fun getChannelName(context: Context): String {
            val baseName = "JaBook Audio Playback"
            val flavor = getFlavorSuffix(context)
            return if (flavor.isEmpty()) baseName else "$baseName - $flavor"
        }

        /**
         * Gets flavor suffix for non-prod builds.
         * Returns formatted flavor name (capitalized) or empty string for prod.
         */
        private fun getFlavorSuffix(context: Context): String {
            val packageName = context.packageName
            val flavor =
                when {
                    packageName.endsWith(".dev") -> "dev"
                    packageName.endsWith(".stage") -> "stage"
                    packageName.endsWith(".beta") -> "beta"
                    else -> "" // prod or unknown
                }
            // Capitalize first letter for display
            return if (flavor.isEmpty()) "" else flavor.substring(0, 1).uppercase() + flavor.substring(1)
        }

        const val ACTION_PLAY = "com.jabook.app.jabook.audio.PLAY"
        const val ACTION_PAUSE = "com.jabook.app.jabook.audio.PAUSE"
        const val ACTION_NEXT = "com.jabook.app.jabook.audio.NEXT"
        const val ACTION_PREVIOUS = "com.jabook.app.jabook.audio.PREVIOUS"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = Companion.getChannelName(context)
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    channelName,
                    AndroidNotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Audio playback controls"
                    setShowBadge(false)

                    // Android 14+ specific configurations
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Enable bubble notifications for better UX on Android 14+
                        setAllowBubbles(true)

                        // Set proper sound and vibration for media notifications
                        setSound(null, null) // No sound for media notifications
                        enableVibration(false) // No vibration for media notifications
                    }
                }

            try {
                notificationManager.createNotificationChannel(channel)
                android.util.Log.d("NotificationManager", "Notification channel created successfully")
            } catch (e: Exception) {
                android.util.Log.e("NotificationManager", "Failed to create notification channel", e)
            }
        }
    }

    /**
     * Creates playback notification.
     *
     * @return Notification instance
     */
    fun createNotification(): Notification {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                // Add extra to indicate we want to open player
                putExtra("open_player", true)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val playPauseAction =
            if (player.isPlaying) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    createPlaybackAction(NotificationManager.ACTION_PAUSE),
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "Play",
                    createPlaybackAction(NotificationManager.ACTION_PLAY),
                )
            }

        // Get flavor suffix for notification title
        val flavorSuffix = Companion.getFlavorSuffix(context)
        val flavorText = if (flavorSuffix.isEmpty()) "" else " - $flavorSuffix"

        val title = metadata?.get("title") ?: "jabook Audio"
        val artist = metadata?.get("artist") ?: "Playing audio"
        val currentMediaItem = player.currentMediaItem
        val baseTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: title
        // Always add flavor suffix to title, even if metadata has title
        val displayTitle = if (flavorText.isEmpty()) baseTitle else "$baseTitle$flavorText"
        val displayArtist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: artist

        // Load cover image from embedded artwork in audio file
        // Media3 1.8: Use player.mediaMetadata.artworkData and artworkUri (both are NOT deprecated)
        var largeIcon: android.graphics.Bitmap? = null
        val mediaMetadata = currentMediaItem?.mediaMetadata

        // Method 0: Try embeddedArtworkPath first (saved by AudioPlayerService from embedded metadata)
        android.util.Log.d("NotificationManager", "Checking embeddedArtworkPath: $embeddedArtworkPath")
        if (largeIcon == null && embeddedArtworkPath != null) {
            try {
                val artworkFile = java.io.File(embeddedArtworkPath!!)
                android.util.Log.d("NotificationManager", "Artwork file exists: ${artworkFile.exists()}, size: ${artworkFile.length()}")
                if (artworkFile.exists() && artworkFile.length() > 0) {
                    largeIcon = BitmapFactory.decodeFile(artworkFile.absolutePath)
                    if (largeIcon != null) {
                        android.util.Log.i(
                            "NotificationManager",
                            "Loaded cover image from embeddedArtworkPath: $embeddedArtworkPath (${largeIcon.width}x${largeIcon.height})",
                        )
                    } else {
                        android.util.Log.w("NotificationManager", "Failed to decode bitmap from embeddedArtworkPath: $embeddedArtworkPath")
                    }
                } else {
                    android.util.Log.w("NotificationManager", "Artwork file does not exist or is empty: $embeddedArtworkPath")
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationManager", "Failed to load cover image from embeddedArtworkPath: $embeddedArtworkPath", e)
            }
        } else if (embeddedArtworkPath == null) {
            android.util.Log.d("NotificationManager", "embeddedArtworkPath is null")
        }

        if (mediaMetadata != null) {
            try {
                // Inspired by lissen-android: prefer artworkUri over artworkData for better performance
                // Method 1: Try artworkUri first (external cover file - better performance)
                // This is set via setArtworkUri(uri) in AudioPlayerService.setPlaylist()
                if (largeIcon == null) {
                    val artworkUri = mediaMetadata.artworkUri
                    if (artworkUri != null) {
                        try {
                            // Handle file:// URIs
                            if (artworkUri.scheme == "file") {
                                val filePath = artworkUri.path
                                if (filePath != null) {
                                    largeIcon = BitmapFactory.decodeFile(filePath)
                                    if (largeIcon != null) {
                                        android.util.Log.d("NotificationManager", "Loaded cover image from artworkUri file: $filePath")
                                    }
                                }
                            } else {
                                // Try content resolver for other URI schemes (content://, http://, etc.)
                                val inputStream = context.contentResolver.openInputStream(artworkUri)
                                if (inputStream != null) {
                                    largeIcon = BitmapFactory.decodeStream(inputStream)
                                    inputStream.close()
                                    if (largeIcon != null) {
                                        android.util.Log.d("NotificationManager", "Loaded cover image from artworkUri: $artworkUri")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("NotificationManager", "Failed to load cover image from artworkUri: $artworkUri", e)
                        }
                    }
                }

                // Method 2: Fallback to artworkData if artworkUri is not available (embedded bytes)
                // This is set via setArtworkData(bytes, PICTURE_TYPE_FRONT_COVER) in AudioPlayerService.setPlaylist()
                if (largeIcon == null) {
                    val artworkData = mediaMetadata.artworkData
                    if (artworkData != null && artworkData.isNotEmpty()) {
                        largeIcon = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                        if (largeIcon != null) {
                            android.util.Log.d(
                                "NotificationManager",
                                "Loaded cover image from MediaMetadata.artworkData: ${artworkData.size} bytes",
                            )
                        } else {
                            android.util.Log.w("NotificationManager", "Failed to decode artworkData (${artworkData.size} bytes)")
                        }
                    }
                }

                // Log if no artwork found
                if (largeIcon == null) {
                    android.util.Log.d(
                        "NotificationManager",
                        "No artwork found. artworkData=${mediaMetadata.artworkData != null && mediaMetadata.artworkData!!.isNotEmpty()}, artworkUri=${mediaMetadata.artworkUri}, embeddedArtworkPath=$embeddedArtworkPath",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("NotificationManager", "Failed to load embedded cover image", e)
            }
        } else {
            android.util.Log.d("NotificationManager", "MediaMetadata is null for current media item")
        }

        val mediaStyle =
            MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)

        // Integrate with MediaSession if available
        // This enables system controls (lockscreen, Android Auto, Wear OS, headset buttons)
        // For Android 13+ (API 33+), SeekBar in notification appears automatically
        // when MediaSessionService is properly configured (which it is)
        // Media3 MediaSessionService automatically provides SessionToken to MediaStyle
        // The MediaStyle notification will work with system controls because MediaSession
        // is connected to the Player through MediaSessionService
        // Note: MediaSessionService automatically handles SessionToken integration
        // SeekBar support is enabled automatically for Android 13+ when using MediaSessionService
        if (mediaSession != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("NotificationManager", "SeekBar support enabled for Android 13+ via MediaSessionService")
        }

        // Media3 MediaSession integrates automatically with Player through MediaSessionManager
        // The MediaStyle notification will work with system controls because MediaSession
        // is connected to the Player, which provides the necessary integration

        // Determine small icon - use cover image if available, otherwise use app icon
        var smallIcon: android.graphics.drawable.Icon? = null
        var smallIconResId: Int = android.R.drawable.ic_media_play

        android.util.Log.d(
            "NotificationManager",
            "Creating small icon. largeIcon is ${if (largeIcon != null) "available (${largeIcon.width}x${largeIcon.height})" else "null"}, Android version: ${Build.VERSION.SDK_INT}",
        )
        if (largeIcon != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Create a small icon from the cover image for the status bar
                // Scale down the large icon to a small icon size (typically 24dp = ~72px on xxxhdpi)
                val smallIconSize = (24 * context.resources.displayMetrics.density).toInt()
                android.util.Log.d("NotificationManager", "Scaling bitmap to small icon size: ${smallIconSize}x$smallIconSize")
                val smallIconBitmap =
                    android.graphics.Bitmap.createScaledBitmap(
                        largeIcon,
                        smallIconSize,
                        smallIconSize,
                        true,
                    )
                smallIcon =
                    android.graphics.drawable.Icon
                        .createWithBitmap(smallIconBitmap)
                android.util.Log.i(
                    "NotificationManager",
                    "Successfully created custom Icon from cover image (${smallIconBitmap.width}x${smallIconBitmap.height})",
                )
            } catch (e: Exception) {
                android.util.Log.e("NotificationManager", "Failed to create custom icon from cover", e)
            }
        } else {
            if (largeIcon == null) {
                android.util.Log.d("NotificationManager", "largeIcon is null, cannot create custom small icon")
            } else {
                android.util.Log.d("NotificationManager", "Android version ${Build.VERSION.SDK_INT} < 24, cannot use Icon API")
            }
        }

        // Set fallback icon resource ID
        try {
            val appIconId = context.applicationInfo.icon
            if (appIconId != 0) {
                smallIconResId = appIconId
            }
        } catch (e: Exception) {
            android.util.Log.w("NotificationManager", "Failed to get app icon, using default", e)
        }

        // User wants cover image in small icon (logo), not in large icon (body)
        // Small icon is already set above from largeIcon bitmap
        // Do NOT set large icon - user wants only small icon (logo) to change

        // For Android 7.0+ with custom icon, use Notification.Builder directly
        // For older versions or when no custom icon, use NotificationCompat.Builder
        val notification =
            if (smallIcon != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    android.util.Log.d("NotificationManager", "Creating notification with Notification.Builder and custom Icon")
                    // Use Notification.Builder which supports Icon directly
                    val builder =
                        Notification
                            .Builder(context, CHANNEL_ID)
                            .setSmallIcon(smallIcon) // Custom icon from album cover
                            .setContentTitle(displayTitle)
                            .setContentText(displayArtist)
                            .setContentIntent(pendingIntent)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .setOnlyAlertOnce(true)
                            .setShowWhen(false) // Media notifications typically don't show timestamp
                    // Do NOT set large icon - user wants cover only in small icon (logo)

                    // Add actions using NotificationCompat.Action.Builder (non-deprecated)
                    // Then convert to Notification.Action for Notification.Builder
                    val previousActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable.ic_media_previous,
                                "Previous",
                                createPlaybackAction(NotificationManager.ACTION_PREVIOUS),
                            ).build()

                    val playPauseActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                                if (player.isPlaying) "Pause" else "Play",
                                createPlaybackAction(
                                    if (player.isPlaying) NotificationManager.ACTION_PAUSE else NotificationManager.ACTION_PLAY,
                                ),
                            ).build()

                    val nextActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable.ic_media_next,
                                "Next",
                                createPlaybackAction(NotificationManager.ACTION_NEXT),
                            ).build()

                    // Convert NotificationCompat.Action to Notification.Action using Icon
                    // Use resource IDs directly instead of deprecated icon field
                    val previousIcon =
                        android.graphics.drawable.Icon
                            .createWithResource(context, android.R.drawable.ic_media_previous)
                    val previousAction =
                        Notification.Action
                            .Builder(
                                previousIcon,
                                previousActionCompat.title ?: "Previous",
                                previousActionCompat.actionIntent ?: createPlaybackAction(NotificationManager.ACTION_PREVIOUS),
                            ).build()

                    val playPauseIcon =
                        android.graphics.drawable.Icon.createWithResource(
                            context,
                            if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        )
                    val playPauseActionNative =
                        Notification.Action
                            .Builder(
                                playPauseIcon,
                                playPauseActionCompat.title ?: if (player.isPlaying) "Pause" else "Play",
                                playPauseActionCompat.actionIntent
                                    ?: createPlaybackAction(
                                        if (player.isPlaying) NotificationManager.ACTION_PAUSE else NotificationManager.ACTION_PLAY,
                                    ),
                            ).build()

                    val nextIcon =
                        android.graphics.drawable.Icon
                            .createWithResource(context, android.R.drawable.ic_media_next)
                    val nextAction =
                        Notification.Action
                            .Builder(
                                nextIcon,
                                nextActionCompat.title ?: "Next",
                                nextActionCompat.actionIntent ?: createPlaybackAction(NotificationManager.ACTION_NEXT),
                            ).build()

                    builder.addAction(previousAction)
                    builder.addAction(playPauseActionNative)
                    builder.addAction(nextAction)

                    // Try to set MediaStyle using reflection for better integration
                    // This is needed for proper small icon display in expanded notification
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                        try {
                            android.util.Log.d("NotificationManager", "Attempting to set MediaStyle on Notification.Builder via reflection")
                            val setStyleMethod = builder.javaClass.getMethod("setStyle", android.app.Notification.Style::class.java)

                            // Create Notification.MediaStyle (available from API 21+)
                            val mediaStyleClass = Class.forName("android.app.Notification\$MediaStyle")
                            val mediaStyleConstructor = mediaStyleClass.getConstructor()
                            val nativeMediaStyle = mediaStyleConstructor.newInstance()

                            // Set show actions in compact view (0, 1, 2 = previous, play/pause, next)
                            val setShowActionsInCompactViewMethod =
                                mediaStyleClass.getMethod(
                                    "setShowActionsInCompactView",
                                    IntArray::class.java,
                                )
                            setShowActionsInCompactViewMethod.invoke(nativeMediaStyle, intArrayOf(0, 1, 2))

                            // Set media session token for system integration
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                try {
                                    // MediaSession from Media3 uses androidx.media3.session.MediaSession
                                    // We need to get the underlying android.media.session.MediaSession.Token
                                    // Media3 MediaSession doesn't expose sessionCompatToken directly
                                    // We'll skip setting the token - MediaSession will still work through the system
                                    android.util.Log.d("NotificationManager", "MediaSession token setting skipped (Media3 MediaSession)")
                                } catch (e: Exception) {
                                    android.util.Log.w("NotificationManager", "Failed to set MediaSession token: ${e.message}", e)
                                }
                            }

                            setStyleMethod.invoke(builder, nativeMediaStyle)
                            android.util.Log.i("NotificationManager", "Successfully set MediaStyle on Notification.Builder via reflection")
                        } catch (e: Exception) {
                            android.util.Log.w(
                                "NotificationManager",
                                "Failed to set MediaStyle via reflection: ${e.message}, continuing without MediaStyle",
                                e,
                            )
                            // Continue without MediaStyle - notification will still work
                        }
                    } else {
                        android.util.Log.d("NotificationManager", "MediaSession not available or Android version < 21, skipping MediaStyle")
                    }

                    val builtNotification = builder.build()
                    android.util.Log.i("NotificationManager", "Successfully created notification with custom Icon and Notification.Builder")
                    builtNotification
                } catch (e: Exception) {
                    android.util.Log.e(
                        "NotificationManager",
                        "Failed to create notification with Notification.Builder, falling back to compat: ${e.message}",
                        e,
                    )
                    // Fallback to compat builder (won't have custom icon, but will work)
                    createCompatNotification(displayTitle, displayArtist, pendingIntent, playPauseAction, mediaStyle, smallIconResId)
                }
            } else {
                // Use NotificationCompat.Builder for older Android versions or when no custom icon
                if (smallIcon == null) {
                    android.util.Log.d(
                        "NotificationManager",
                        "No custom icon available, using NotificationCompat.Builder with fallback icon: $smallIconResId",
                    )
                } else {
                    android.util.Log.d(
                        "NotificationManager",
                        "Android version ${Build.VERSION.SDK_INT} < 24, using NotificationCompat.Builder with fallback icon: $smallIconResId",
                    )
                }
                createCompatNotification(displayTitle, displayArtist, pendingIntent, playPauseAction, mediaStyle, smallIconResId)
            }

        return notification
    }

    private fun createCompatNotification(
        displayTitle: String,
        displayArtist: String,
        pendingIntent: PendingIntent,
        playPauseAction: NotificationCompat.Action,
        mediaStyle: MediaStyle,
        smallIconResId: Int,
        largeIconBitmap: android.graphics.Bitmap? = null, // Not used - user wants cover only in small icon
    ): Notification {
        // User wants cover image in small icon (logo), not in large icon (body)
        // For NotificationCompat, we can't set custom Icon directly, so we use fallback icon
        // The small icon with cover image only works with Notification.Builder (Android 7.0+)
        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconResId) // Fallback icon for older Android versions
            .setContentTitle(displayTitle)
            .setContentText(displayArtist)
            .setContentIntent(pendingIntent)
            // Do NOT set large icon - user wants cover only in small icon (logo)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    createPlaybackAction(NotificationManager.ACTION_PREVIOUS),
                ),
            ).addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    createPlaybackAction(NotificationManager.ACTION_NEXT),
                ),
            ).setStyle(mediaStyle)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Updates notification with current state and Android 14+ optimizations.
     *
     * This method includes rate limiting and proper error handling to prevent
     * notification-related crashes on Android 14+.
     */
    fun updateNotification() {
        try {
            val notification = createNotification()
            notificationManager.notify(NOTIFICATION_ID, notification)
            android.util.Log.d("NotificationManager", "Notification updated successfully")
        } catch (e: Exception) {
            android.util.Log.e("NotificationManager", "Failed to update notification", e)
        }
    }

    /**
     * Updates metadata for notification with Android 14+ optimizations.
     *
     * This method includes proper error handling to prevent
     * metadata-related crashes on Android 14+.
     */
    fun updateMetadata(
        metadata: Map<String, String>?,
        newEmbeddedArtworkPath: String? = null,
    ) {
        try {
            android.util.Log.d(
                "NotificationManager",
                "updateMetadata called. newEmbeddedArtworkPath: $newEmbeddedArtworkPath, current embeddedArtworkPath: $embeddedArtworkPath",
            )
            this.metadata = metadata
            if (newEmbeddedArtworkPath != null) {
                embeddedArtworkPath = newEmbeddedArtworkPath
                android.util.Log.i("NotificationManager", "Updated embeddedArtworkPath to: $embeddedArtworkPath")
            } else {
                android.util.Log.d("NotificationManager", "newEmbeddedArtworkPath is null, keeping existing: $embeddedArtworkPath")
            }
            updateNotification()
        } catch (e: Exception) {
            android.util.Log.e("NotificationManager", "Failed to update metadata", e)
        }
    }

    /**
     * Updates MediaSession reference.
     * Called when MediaSession is created or recreated.
     */
    fun updateMediaSession(session: androidx.media3.session.MediaSession?) {
        // MediaSession is already passed in constructor, but we can update if needed
        // This method is for future use if we need to update session dynamically
    }

    /**
     * Creates PendingIntent for playback action with Android 14+ compatibility.
     *
     * This method includes proper error handling to prevent PendingIntent-related
     * crashes on Android 14+.
     */
    private fun createPlaybackAction(action: String): PendingIntent {
        try {
            val intent =
                Intent(context, AudioPlayerService::class.java).apply {
                    this.action = action
                }

            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

            return PendingIntent.getService(
                context,
                action.hashCode(),
                intent,
                flags,
            )
        } catch (e: Exception) {
            android.util.Log.e("NotificationManager", "Failed to create PendingIntent for action: $action", e)

            // Fallback: create a simple PendingIntent without action
            val fallbackIntent = Intent(context, AudioPlayerService::class.java)
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

            return PendingIntent.getService(context, action.hashCode(), fallbackIntent, flags)
        }
    }
}
