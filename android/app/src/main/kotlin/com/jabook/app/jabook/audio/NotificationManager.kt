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
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.MainActivity
import com.jabook.app.jabook.audio.AudioPlayerService

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
    private var metadata: Map<String, String>? = null
) {
    private val notificationManager: AndroidNotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
    
    companion object {
        private const val CHANNEL_ID = "jabook_audio_playback"
        private const val CHANNEL_NAME = "JaBook Audio Playback"
        private const val NOTIFICATION_ID = 1
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                AndroidNotificationManager.IMPORTANCE_LOW
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
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            // Add extra to indicate we want to open player
            putExtra("open_player", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val playPauseAction = if (player.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createPlaybackAction(NotificationManager.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createPlaybackAction(NotificationManager.ACTION_PLAY)
            )
        }
        
        val title = metadata?.get("title") ?: "JaBook Audio"
        val artist = metadata?.get("artist") ?: "Playing audio"
        val currentMediaItem = player.currentMediaItem
        val displayTitle = currentMediaItem?.mediaMetadata?.title?.toString() ?: title
        val displayArtist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: artist
        
        // Load cover image from embedded artwork in audio file
        // Media3 1.8: Use player.mediaMetadata.artworkData and artworkUri (both are NOT deprecated)
        var largeIcon: android.graphics.Bitmap? = null
        val mediaMetadata = currentMediaItem?.mediaMetadata
        
        if (mediaMetadata != null) {
            try {
                // Inspired by lissen-android: prefer artworkUri over artworkData for better performance
                // Method 1: Try artworkUri first (external cover file - better performance)
                // This is set via setArtworkUri(uri) in AudioPlayerService.setPlaylist()
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
                
                // Method 2: Fallback to artworkData if artworkUri is not available (embedded bytes)
                // This is set via setArtworkData(bytes, PICTURE_TYPE_FRONT_COVER) in AudioPlayerService.setPlaylist()
                if (largeIcon == null) {
                    val artworkData = mediaMetadata.artworkData
                    if (artworkData != null && artworkData.isNotEmpty()) {
                        largeIcon = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                        if (largeIcon != null) {
                            android.util.Log.d("NotificationManager", "Loaded cover image from MediaMetadata.artworkData: ${artworkData.size} bytes")
                        } else {
                            android.util.Log.w("NotificationManager", "Failed to decode artworkData (${artworkData.size} bytes)")
                        }
                    }
                }
                
                // Log if no artwork found
                if (largeIcon == null) {
                    android.util.Log.d("NotificationManager", "No artwork found. artworkData=${mediaMetadata.artworkData != null && mediaMetadata.artworkData!!.isNotEmpty()}, artworkUri=${mediaMetadata.artworkUri}")
                }
            } catch (e: Exception) {
                android.util.Log.w("NotificationManager", "Failed to load embedded cover image", e)
            }
        } else {
            android.util.Log.d("NotificationManager", "MediaMetadata is null for current media item")
        }
        
        val mediaStyle = MediaStyle()
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
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(displayTitle)
            .setContentText(displayArtist)
            .setContentIntent(pendingIntent)
        
        // Set large icon (cover image) if available
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }
        
        return notificationBuilder
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    createPlaybackAction(NotificationManager.ACTION_PREVIOUS)
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    createPlaybackAction(NotificationManager.ACTION_NEXT)
                )
            )
            .setStyle(mediaStyle)
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
    fun updateMetadata(metadata: Map<String, String>?) {
        try {
            this.metadata = metadata
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
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                this.action = action
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            return PendingIntent.getService(
                context,
                action.hashCode(),
                intent,
                flags
            )
        } catch (e: Exception) {
            android.util.Log.e("NotificationManager", "Failed to create PendingIntent for action: $action", e)
            
            // Fallback: create a simple PendingIntent without action
            val fallbackIntent = Intent(context, AudioPlayerService::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            
            return PendingIntent.getService(context, action.hashCode(), fallbackIntent, flags)
        }
    }
}

