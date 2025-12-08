package com.jabook.app.jabook.audio

import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService

/**
 * MediaSessionService.Listener implementation.
 *
 * This listener handles service lifecycle events, particularly the
 * onForegroundServiceStartNotAllowedException which occurs on Android 12+
 * when the system prevents the service from starting in the foreground.
 */
@OptIn(UnstableApi::class)
class MediaSessionServiceListener(
    private val service: AudioPlayerService,
) : MediaSessionService.Listener {
    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the MediaSessionService is in the background.
     *
     * This can happen when:
     * - User tries to resume playback from Quick Settings or lock screen
     * - System tries to resume playback after app was killed
     * - Notification permission is not granted (Android 13+)
     */
    override fun onForegroundServiceStartNotAllowedException() {
        android.util.Log.w(
            "AudioPlayerService",
            "onForegroundServiceStartNotAllowedException: System doesn't allow foreground service start",
        )

        // Check if notification permission is required but not granted (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (service.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.w(
                    "AudioPlayerService",
                    "Notification permission not granted - cannot show notification",
                )
                // Notification permission is required but not granted
                // User needs to grant permission in app settings
                return
            }
        }

        // Show notification to inform user that playback cannot be resumed
        // This notification will allow user to open app and grant permissions if needed
        try {
            val notificationManagerCompat =
                androidx.core.app.NotificationManagerCompat
                    .from(service)

            // Ensure notification channel exists
            service.notificationHelper?.let { helper ->
                // NotificationHelper already has channel creation logic
                // We can reuse it or create channel directly
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel =
                        android.app.NotificationChannel(
                            NotificationHelper.CHANNEL_ID,
                            "JaBook Audio Playback",
                            android.app.NotificationManager.IMPORTANCE_DEFAULT,
                        )
                    notificationManagerCompat.createNotificationChannel(channel)
                }
            }

            val builder =
                androidx.core.app.NotificationCompat
                    .Builder(
                        service,
                        NotificationHelper.CHANNEL_ID,
                    ).setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("JaBook Audio")
                    .setContentText("Tap to open app and resume playback")
                    .setStyle(
                        androidx.core.app.NotificationCompat
                            .BigTextStyle()
                            .bigText("Playback cannot be resumed automatically. Please open the app to continue."),
                    ).setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)

            // Create intent to open app
            val intent =
                service.packageManager
                    .getLaunchIntentForPackage(service.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pendingIntent =
                    android.app.PendingIntent.getActivity(
                        service,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                builder.setContentIntent(pendingIntent)
            }

            // Show notification
            // Use a specific ID for this error notification
            notificationManagerCompat.notify(1002, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to show error notification", e)
        }
    }
}
