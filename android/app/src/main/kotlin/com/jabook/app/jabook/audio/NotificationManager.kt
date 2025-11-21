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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.MainActivity
import com.jabook.app.jabook.audio.AudioPlayerService

/**
 * Manages playback notification with media controls.
 *
 * This class creates and updates the notification that appears
 * in the notification panel during audio playback.
 */
@UnstableApi
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
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates playback notification.
     *
     * @return Notification instance
     */
    fun createNotification(): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(displayTitle)
            .setContentText(displayArtist)
            .setContentIntent(pendingIntent)
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
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .build()
    }
    
    /**
     * Updates notification with current state.
     */
    fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Updates metadata for notification.
     */
    fun updateMetadata(metadata: Map<String, String>?) {
        this.metadata = metadata
        updateNotification()
    }
    
    /**
     * Creates PendingIntent for playback action.
     */
    private fun createPlaybackAction(action: String): PendingIntent {
        val intent = Intent(context, AudioPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}

