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
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.ComposeMainActivity
import android.app.NotificationManager as AndroidNotificationManager

/**
 * Helper class for creating notifications for AudioPlayerService.
 */
internal class NotificationHelper(
    private val context: Context,
) {
    companion object {
        internal const val CHANNEL_ID = "media_playback_channel"
        internal const val NOTIFICATION_ID = 1
    }

    /**
     * Creates a minimal notification for startForeground() call.
     * This is required for Android 8.0+ to prevent crashes.
     * The notification will be updated later with full media controls.
     *
     * @return Minimal notification for foreground service
     */
    fun createMinimalNotification(): Notification {
        // Create notification channel if not exists
        ensureNotificationChannel(CHANNEL_ID)

        val intent =
            Intent(context, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.initializingAudioPlayer))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Creates a basic fallback notification when createMinimalNotification() fails.
     * This is used as last resort to prevent service crash.
     *
     * @return Basic fallback notification
     */
    fun createFallbackNotification(): Notification {
        ensureNotificationChannel(CHANNEL_ID)

        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.initializing))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Ensures notification channel exists. Creates it if it doesn't exist.
     *
     * @param channelId Channel ID to ensure exists
     */
    internal fun ensureNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel =
                    NotificationChannel(
                        channelId,
                        context.getString(R.string.notification_channel_name),
                        AndroidNotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.notification_channel_description)
                        setShowBadge(false)
                    }
                val notificationManager =
                    context.getSystemService(
                        Context.NOTIFICATION_SERVICE,
                    ) as AndroidNotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to create notification channel", e)
            }
        }
    }
}
