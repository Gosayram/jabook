package com.jabook.app.core.compat

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Compatibility manager for Android API 23-34 support Based on IDEA.md architecture specification */
object CompatibilityManager {
    /** Check if the device supports a specific API level */
    fun isApiSupported(apiLevel: Int): Boolean = Build.VERSION.SDK_INT >= apiLevel

    /** Check if runtime permissions are required (API 23+) */
    fun requiresRuntimePermissions(): Boolean = isApiSupported(Build.VERSION_CODES.M)

    /** Check if scoped storage is enforced (API 30+) */
    fun usesScopedStorage(): Boolean = isApiSupported(Build.VERSION_CODES.R)

    /** Check if notification channels are required (API 26+) */
    fun requiresNotificationChannels(): Boolean = isApiSupported(Build.VERSION_CODES.O)

    /** Check if background service restrictions apply (API 26+) */
    fun hasBackgroundServiceRestrictions(): Boolean = isApiSupported(Build.VERSION_CODES.O)

    /** Check if notification runtime permission is required (API 33+) */
    fun requiresNotificationPermission(): Boolean = isApiSupported(Build.VERSION_CODES.TIRAMISU)

    /** Check if Material You theming is available (API 31+) */
    fun supportsMaterialYou(): Boolean = isApiSupported(Build.VERSION_CODES.S)

    /** Check if per-app language preferences are available (API 33+) */
    fun supportsPerAppLanguage(): Boolean = isApiSupported(Build.VERSION_CODES.TIRAMISU)
}

/** Permission compatibility helper */
object PermissionCompat {
    fun hasPermission(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Storage permissions
        if (CompatibilityManager.usesScopedStorage()) {
            // API 30+ uses scoped storage
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // API 23-29 uses legacy storage
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // Network permissions
        permissions.add(android.Manifest.permission.INTERNET)
        permissions.add(android.Manifest.permission.ACCESS_NETWORK_STATE)

        // Foreground service permission (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE)
        }

        // Notification permission (API 33+)
        if (CompatibilityManager.requiresNotificationPermission()) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Wake lock for background playback
        permissions.add(android.Manifest.permission.WAKE_LOCK)

        return permissions
    }
}

/** Storage compatibility helper */
object StorageCompat {
    fun getExternalStorageDirectory(context: Context): String =
        if (CompatibilityManager.usesScopedStorage()) {
            // API 30+ uses scoped storage
            context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
        } else {
            // API 23-29 uses legacy storage
            android.os.Environment
                .getExternalStorageDirectory()
                .absolutePath
        }

    fun getDownloadsDirectory(context: Context): String =
        if (CompatibilityManager.usesScopedStorage()) {
            // API 30+ uses app-specific storage
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: "${context.filesDir}/downloads"
        } else {
            // API 23-29 can use public downloads
            "${android.os.Environment.getExternalStorageDirectory()}/Download"
        }

    fun getMediaDirectory(context: Context): String =
        if (CompatibilityManager.usesScopedStorage()) {
            // API 30+ uses app-specific storage
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.absolutePath ?: "${context.filesDir}/music"
        } else {
            // API 23-29 can use public music directory
            "${android.os.Environment.getExternalStorageDirectory()}/Music"
        }
}

/** Notification compatibility helper */
object NotificationCompat {
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        if (CompatibilityManager.requiresNotificationChannels()) {
            val channel = android.app.NotificationChannel(channelId, channelName, importance)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun getNotificationImportance(priority: Int): Int =
        if (CompatibilityManager.requiresNotificationChannels()) {
            when (priority) {
                androidx.core.app.NotificationCompat.PRIORITY_HIGH -> android.app.NotificationManager.IMPORTANCE_HIGH
                androidx.core.app.NotificationCompat.PRIORITY_DEFAULT -> android.app.NotificationManager.IMPORTANCE_DEFAULT
                androidx.core.app.NotificationCompat.PRIORITY_LOW -> android.app.NotificationManager.IMPORTANCE_LOW
                androidx.core.app.NotificationCompat.PRIORITY_MIN -> android.app.NotificationManager.IMPORTANCE_MIN
                else -> android.app.NotificationManager.IMPORTANCE_DEFAULT
            }
        } else {
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        }
}

/** Theme compatibility helper */
object ThemeCompat {
    fun supportsDynamicColors(): Boolean = CompatibilityManager.supportsMaterialYou()

    fun supportsSystemDarkMode(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun getSystemDarkMode(context: Context): Boolean =
        if (supportsSystemDarkMode()) {
            val nightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            false
        }
}

/** Audio compatibility helper */
object AudioCompat {
    fun supportsAudioFocusRequest(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun supportsMediaSession(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    fun supportsAudioAttributes(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    fun createAudioFocusRequest(
        focusGain: Int,
        audioAttributes: android.media.AudioAttributes?,
        listener: android.media.AudioManager.OnAudioFocusChangeListener,
    ): Any? =
        if (supportsAudioFocusRequest() && audioAttributes != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.media.AudioFocusRequest
                    .Builder(focusGain)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(listener)
                    .build()
            } else {
                null
            }
        } else {
            null
        }
}
