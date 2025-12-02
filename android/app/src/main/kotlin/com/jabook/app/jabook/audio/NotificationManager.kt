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
import androidx.media3.common.util.Util
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
    private var player: ExoPlayer, // Changed to var to allow updating player reference
    private val mediaSession: androidx.media3.session.MediaSession? = null,
    private var metadata: Map<String, String>? = null,
    private var embeddedArtworkPath: String? = null, // Path to saved embedded artwork from AudioPlayerService
    private var rewindSeconds: Long, // Must be provided from MediaSessionManager to use actual settings (book-specific or global)
    private var forwardSeconds: Long, // Must be provided from MediaSessionManager to use actual settings (book-specific or global)
) {
    /**
     * Updates the player reference. This is needed when the player is recreated.
     */
    fun updatePlayer(newPlayer: ExoPlayer) {
        player = newPlayer
        android.util.Log.d("NotificationManager", "Player reference updated")
    }

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
        const val ACTION_REWIND = "com.jabook.app.jabook.audio.REWIND"
        const val ACTION_FORWARD = "com.jabook.app.jabook.audio.FORWARD"
        const val ACTION_STOP = "com.jabook.app.jabook.audio.STOP"
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

        // Use Media3 Util.shouldShowPlayButton() for correct Play/Pause button determination
        // This is the same logic used in PlayerNotificationManager from Media3
        // Use createPlaybackAction() for consistency with all other actions (Previous, Next, Rewind, Forward, Stop)
        // CRITICAL: Read player state directly from player to ensure we get the latest state
        // This avoids race conditions where updateNotification() is called before state is updated
        // Also check isPlaying for more accurate state detection
        val currentPlayWhenReady = player.playWhenReady
        val currentPlaybackState = player.playbackState
        val currentIsPlaying = player.isPlaying
        val shouldShowPlay = Util.shouldShowPlayButton(player, true)
        android.util.Log.d(
            "NotificationManager",
            "Determining Play/Pause button: playWhenReady=$currentPlayWhenReady, isPlaying=$currentIsPlaying, playbackState=$currentPlaybackState, shouldShowPlay=$shouldShowPlay",
        )
        val playPauseAction =
            if (shouldShowPlay) {
                android.util.Log.d("NotificationManager", "Creating PLAY button action (shouldShowPlay=true)")
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "Play",
                    createPlaybackAction(ACTION_PLAY),
                )
            } else {
                android.util.Log.d("NotificationManager", "Creating PAUSE button action (shouldShowPlay=false)")
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    createPlaybackAction(ACTION_PAUSE),
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
                // Compact view: Rewind (1), Play/Pause (2), Forward (3)
                .setShowActionsInCompactView(1, 2, 3)

        // CRITICAL: Set MediaSession token for proper integration with system controls
        // This enables controls in Quick Settings, lockscreen, Android Auto, Wear OS, headset buttons
        // Without this, buttons in Quick Settings won't work
        // Note: androidx.media.app.NotificationCompat.MediaStyle requires MediaSessionCompat.Token
        // but Media3 uses SessionToken. We need to convert or use reflection to get the underlying token
        if (mediaSession != null) {
            try {
                // Media3 MediaSession: use public getToken() method to get SessionToken
                // For androidx.media.app.NotificationCompat.MediaStyle, we need to get the underlying
                // android.media.session.MediaSession.Token from SessionToken
                val sessionToken = mediaSession.getToken()

                // Try to get the underlying native token using reflection
                // SessionToken wraps the native android.media.session.MediaSession.Token
                try {
                    val sessionTokenClass = sessionToken.javaClass
                    val getTokenMethod = sessionTokenClass.getMethod("getToken")
                    val nativeToken = getTokenMethod.invoke(sessionToken) as? android.media.session.MediaSession.Token

                    if (nativeToken != null) {
                        // Create MediaSessionCompat.Token from native token using reflection
                        // According to documentation: MediaSessionCompat.Token.fromToken(token: Any!)
                        // accepts android.media.session.MediaSession.Token and returns MediaSessionCompat.Token
                        try {
                            // Get MediaSessionCompat.Token class
                            val compatTokenClass = Class.forName("androidx.media.session.MediaSessionCompat\$Token")
                            // Get static method fromToken(token: Any!)
                            val fromTokenMethod = compatTokenClass.getMethod("fromToken", Any::class.java)
                            // Call fromToken with native token to create MediaSessionCompat.Token
                            val compatToken = fromTokenMethod.invoke(null, nativeToken)
                            // Use reflection to call setMediaSession() with MediaSessionCompat.Token
                            // This is needed because reflection returns Any! but setMediaSession expects MediaSessionCompat.Token!
                            val setMediaSessionMethod = mediaStyle.javaClass.getMethod("setMediaSession", compatTokenClass)
                            setMediaSessionMethod.invoke(mediaStyle, compatToken)
                            android.util.Log.d("NotificationManager", "MediaStyle configured with MediaSession token for system controls")
                        } catch (e: Exception) {
                            android.util.Log.e("NotificationManager", "Failed to create or set MediaSessionCompat.Token: ${e.message}", e)
                            // Fallback: try to set native token directly (may not work but worth trying)
                            try {
                                val setMediaSessionMethod =
                                    mediaStyle.javaClass.getMethod(
                                        "setMediaSession",
                                        android.media.session.MediaSession.Token::class.java,
                                    )
                                setMediaSessionMethod.invoke(mediaStyle, nativeToken)
                                android.util.Log.d(
                                    "NotificationManager",
                                    "MediaStyle configured with native token using reflection fallback",
                                )
                            } catch (e2: Exception) {
                                android.util.Log.e("NotificationManager", "Failed to set native token: ${e2.message}")
                            }
                        }
                    } else {
                        android.util.Log.w("NotificationManager", "Failed to get native token from SessionToken")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("NotificationManager", "Failed to extract native token, trying direct approach: ${e.message}")
                    // Fallback: try to use SessionToken directly (may work in some Media3 versions)
                    // This will likely fail but worth trying
                    try {
                        val sessionTokenField = mediaStyle.javaClass.getDeclaredField("mToken")
                        sessionTokenField.isAccessible = true
                        sessionTokenField.set(mediaStyle, sessionToken)
                        android.util.Log.d("NotificationManager", "MediaStyle configured with SessionToken using reflection")
                    } catch (e2: Exception) {
                        android.util.Log.e("NotificationManager", "Failed to set MediaSession token: ${e2.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationManager", "Failed to set MediaSession token in MediaStyle: ${e.message}", e)
            }
        } else {
            android.util.Log.w("NotificationManager", "MediaSession is null, MediaStyle won't work with system controls")
        }

        // For Android 13+ (API 33+), SeekBar in notification appears automatically
        // when MediaSessionService is properly configured with SessionToken
        if (mediaSession != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("NotificationManager", "SeekBar support enabled for Android 13+ via MediaSessionService")
        }

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

                    // Use Media3 Util.shouldShowPlayButton() for correct Play/Pause button determination
                    // This is the same logic used in PlayerNotificationManager from Media3
                    // Use createPlaybackAction() for consistency with all other actions (Previous, Next, Rewind, Forward, Stop)
                    // CRITICAL: Read player state directly from player to ensure we get the latest state
                    val shouldShowPlay = Util.shouldShowPlayButton(player, true)
                    val playPauseActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                if (shouldShowPlay) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                                if (shouldShowPlay) "Play" else "Pause",
                                createPlaybackAction(
                                    if (shouldShowPlay) ACTION_PLAY else ACTION_PAUSE,
                                ),
                            ).build()

                    val nextActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable.ic_media_next,
                                "Next",
                                createPlaybackAction(NotificationManager.ACTION_NEXT),
                            ).build()

                    val rewindActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable.ic_media_rew,
                                "Rewind ${rewindSeconds}s",
                                createPlaybackAction(NotificationManager.ACTION_REWIND),
                            ).build()

                    val forwardActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable.ic_media_ff,
                                "Forward ${forwardSeconds}s",
                                createPlaybackAction(NotificationManager.ACTION_FORWARD),
                            ).build()

                    val stopActionCompat =
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable.ic_menu_close_clear_cancel,
                                "Stop",
                                createPlaybackAction(NotificationManager.ACTION_STOP),
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

                    // Use Media3 Util.shouldShowPlayButton() for correct Play/Pause button determination
                    // Reuse shouldShowPlay from above
                    // Use createPlaybackAction() for consistency with all other actions (Previous, Next, Rewind, Forward, Stop)
                    val playPauseIcon =
                        android.graphics.drawable.Icon.createWithResource(
                            context,
                            if (shouldShowPlay) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                        )
                    val playPauseActionNative =
                        Notification.Action
                            .Builder(
                                playPauseIcon,
                                playPauseActionCompat.title ?: if (shouldShowPlay) "Play" else "Pause",
                                playPauseActionCompat.actionIntent
                                    ?: createPlaybackAction(
                                        if (shouldShowPlay) ACTION_PLAY else ACTION_PAUSE,
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

                    val rewindIcon =
                        android.graphics.drawable.Icon
                            .createWithResource(context, android.R.drawable.ic_media_rew)
                    val rewindAction =
                        Notification.Action
                            .Builder(
                                rewindIcon,
                                rewindActionCompat.title ?: "Rewind",
                                rewindActionCompat.actionIntent ?: createPlaybackAction(NotificationManager.ACTION_REWIND),
                            ).build()

                    val forwardIcon =
                        android.graphics.drawable.Icon
                            .createWithResource(context, android.R.drawable.ic_media_ff)
                    val forwardAction =
                        Notification.Action
                            .Builder(
                                forwardIcon,
                                forwardActionCompat.title ?: "Forward",
                                forwardActionCompat.actionIntent ?: createPlaybackAction(NotificationManager.ACTION_FORWARD),
                            ).build()

                    val stopIcon =
                        android.graphics.drawable.Icon
                            .createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel)
                    val stopAction =
                        Notification.Action
                            .Builder(
                                stopIcon,
                                stopActionCompat.title ?: "Stop",
                                stopActionCompat.actionIntent ?: createPlaybackAction(NotificationManager.ACTION_STOP),
                            ).build()

                    // Add actions in order: Previous, Rewind, Play/Pause, Forward, Next, Stop
                    builder.addAction(previousAction) // 0
                    builder.addAction(rewindAction) // 1
                    builder.addAction(playPauseActionNative) // 2
                    builder.addAction(forwardAction) // 3
                    builder.addAction(nextAction) // 4
                    builder.addAction(stopAction) // 5

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

                            // Set show actions in compact view (1, 2, 3 = rewind, play/pause, forward)
                            val setShowActionsInCompactViewMethod =
                                mediaStyleClass.getMethod(
                                    "setShowActionsInCompactView",
                                    IntArray::class.java,
                                )
                            setShowActionsInCompactViewMethod.invoke(nativeMediaStyle, intArrayOf(1, 2, 3))

                            // Set media session token for system integration (CRITICAL for Quick Settings)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
                                try {
                                    // Media3 MediaSession: use public getToken() method to get SessionToken
                                    // Reference: https://developer.android.com/reference/kotlin/androidx/media3/session/SessionToken
                                    val sessionToken = mediaSession.getToken()

                                    // Get the underlying android.media.session.MediaSession.Token
                                    // Media3 SessionToken wraps the native token - use reflection to get native token
                                    // for native MediaStyle which requires android.media.session.MediaSession.Token
                                    val setMediaSessionMethod =
                                        mediaStyleClass.getMethod(
                                            "setMediaSession",
                                            android.media.session.MediaSession.Token::class.java,
                                        )

                                    // Media3 SessionToken has a getToken() method that returns the native token
                                    // This requires reflection as it's an internal API
                                    val sessionTokenClass = sessionToken.javaClass
                                    val getNativeTokenMethod = sessionTokenClass.getMethod("getToken")
                                    val nativeToken = getNativeTokenMethod.invoke(sessionToken)
                                    setMediaSessionMethod.invoke(nativeMediaStyle, nativeToken)

                                    android.util.Log.d("NotificationManager", "MediaSession token set successfully for native MediaStyle")
                                } catch (e: Exception) {
                                    android.util.Log.w(
                                        "NotificationManager",
                                        "Failed to set MediaSession token in native MediaStyle: ${e.message}",
                                        e,
                                    )
                                    // Try alternative approach: try to use SessionToken directly
                                    try {
                                        // Alternative: try to use SessionToken as-is (may work in some cases)
                                        val sessionToken = mediaSession.getToken()
                                        val setMediaSessionMethod =
                                            mediaStyleClass.getMethod(
                                                "setMediaSession",
                                                android.media.session.MediaSession.Token::class.java,
                                            )
                                        // This will likely fail, but worth trying
                                        setMediaSessionMethod.invoke(nativeMediaStyle, sessionToken)
                                        android.util.Log.d("NotificationManager", "MediaSession token set using alternative method")
                                    } catch (e2: Exception) {
                                        android.util.Log.w(
                                            "NotificationManager",
                                            "Alternative MediaSession token method also failed: ${e2.message}",
                                        )
                                    }
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
                    createCompatNotification(
                        displayTitle,
                        displayArtist,
                        pendingIntent,
                        playPauseAction,
                        mediaStyle,
                        smallIconResId,
                        rewindSeconds,
                        forwardSeconds,
                    )
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
                createCompatNotification(
                    displayTitle,
                    displayArtist,
                    pendingIntent,
                    playPauseAction,
                    mediaStyle,
                    smallIconResId,
                    rewindSeconds,
                    forwardSeconds,
                )
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
        rewindSeconds: Long,
        forwardSeconds: Long,
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
            // Add actions in order: Previous, Rewind, Play/Pause, Forward, Next, Stop
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    createPlaybackAction(NotificationManager.ACTION_PREVIOUS),
                ),
            ).addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_rew,
                    "Rewind ${rewindSeconds}s",
                    createPlaybackAction(NotificationManager.ACTION_REWIND),
                ),
            ).addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_ff,
                    "Forward ${forwardSeconds}s",
                    createPlaybackAction(NotificationManager.ACTION_FORWARD),
                ),
            ).addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    createPlaybackAction(NotificationManager.ACTION_NEXT),
                ),
            ).addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    createPlaybackAction(NotificationManager.ACTION_STOP),
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
     * Updates skip durations for rewind and forward actions.
     *
     * This method is called when skip durations change (either book-specific or global settings).
     * The notification is immediately updated to reflect the new values.
     *
     * @param rewindSeconds Duration in seconds for rewind action (from book settings or global settings)
     * @param forwardSeconds Duration in seconds for forward action (from book settings or global settings)
     */
    fun updateSkipDurations(
        rewindSeconds: Long,
        forwardSeconds: Long,
    ) {
        this.rewindSeconds = rewindSeconds
        this.forwardSeconds = forwardSeconds
        // Immediately update notification to show new skip durations
        updateNotification()
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
     *
     * CRITICAL: For Play/Pause actions, uses unique request codes to avoid conflicts
     * on Samsung and other devices that may have issues with PendingIntent handling.
     * All actions (Play, Pause, Next, Previous, Rewind, Forward, Stop) use this method
     * for consistency - all commands go through AudioPlayerService.onStartCommand().
     */
    private fun createPlaybackAction(action: String): PendingIntent {
        try {
            val intent =
                Intent(context, AudioPlayerService::class.java).apply {
                    this.action = action
                }

            // Use unique request codes for each action to avoid conflicts
            // This is especially important for Play/Pause on Samsung devices
            val requestCode =
                when (action) {
                    ACTION_PLAY -> 1001
                    ACTION_PAUSE -> 1002
                    ACTION_NEXT -> 1003
                    ACTION_PREVIOUS -> 1004
                    ACTION_REWIND -> 1005
                    ACTION_FORWARD -> 1006
                    ACTION_STOP -> 1007
                    else -> action.hashCode()
                }

            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

            android.util.Log.d(
                "NotificationManager",
                "Creating PendingIntent for action: $action, requestCode: $requestCode, flags: $flags",
            )

            val pendingIntent =
                PendingIntent.getService(
                    context,
                    requestCode,
                    intent,
                    flags,
                )

            android.util.Log.d("NotificationManager", "Successfully created PendingIntent for action: $action")
            return pendingIntent
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

            android.util.Log.w("NotificationManager", "Using fallback PendingIntent for action: $action")
            return PendingIntent.getService(context, action.hashCode(), fallbackIntent, flags)
        }
    }
}
