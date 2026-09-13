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

import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerLibrarySessionCallback.Companion.CUSTOM_COMMAND_FORWARD
import com.jabook.app.jabook.audio.AudioPlayerLibrarySessionCallback.Companion.CUSTOM_COMMAND_REWIND
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.launch

/**
 * Custom MediaNotification.Provider to handle "Minimal Notification" mode.
 * We use the DefaultMediaNotificationProvider but can intercept Bitmap loading.
 */
@OptIn(UnstableApi::class)
public class AudioPlayerNotificationProvider(
    private val service: AudioPlayerService,
) : MediaNotification.Provider {
    // Use CoilBitmapLoader for better performance and caching (aligned with project stack)
    // Coil provides superior memory management and async loading
    private val coilBitmapLoader = CoilBitmapLoader(service)

    // Share this loader with MediaLibrarySession so artwork behavior is identical in the session
    // and notification, including minimal-notification mode.
    internal val bitmapLoader: BitmapLoader =
        object : BitmapLoader {
            override fun loadBitmap(uri: android.net.Uri): ListenableFuture<Bitmap> {
                if (service.isMinimalNotification) {
                    // Return failed future to skip artwork loading
                    // This causes DefaultMediaNotificationProvider to use fallback/no artwork
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Use Coil for better performance
                return coilBitmapLoader.loadBitmap(uri)
            }

            override fun supportsMimeType(mimeType: String): Boolean = coilBitmapLoader.supportsMimeType(mimeType)

            override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                if (service.isMinimalNotification) {
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Use Coil for better performance
                return coilBitmapLoader.decodeBitmap(data)
            }

            override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
                if (service.isMinimalNotification) {
                    return Futures.immediateFailedFuture(Exception("Minimal mode"))
                }
                // Use Coil for better performance
                return coilBitmapLoader.loadBitmapFromMetadata(metadata)
            }
        }

    // Callback to request notification rebuild from outside
    private var notificationCallback: MediaNotification.Provider.Callback? = null
    private var lastNotification: MediaNotification? = null

    /**
     * Releases internal resources (Coil scope). Call during service teardown.
     */
    public fun release() {
        coilBitmapLoader.release()
    }

    // Cached notification action slots — kept fresh by a collector in playerServiceScope
    // so filterCustomLayout never blocks the main thread with a DataStore read.
    @Volatile
    private var cachedNotificationActionSlots: Set<Int>? = null

    // Cached lockscreen visibility — kept fresh by the same collector as cachedNotificationActionSlots.
    @Volatile
    private var cachedLockscreenPrivate: Boolean = false

    init {
        service.playerServiceScope.launch {
            try {
                service.settingsRepository.userPreferences.collect { prefs ->
                    val slots = prefs.notificationActionSlotsList
                    cachedNotificationActionSlots = if (slots.isEmpty()) null else slots.toSet()
                    cachedLockscreenPrivate = prefs.notificationLockscreenPrivate
                }
            } catch (e: Exception) {
                LogUtils.w("AudioPlayerNotificationProvider", "Failed to collect notification action slot preferences", e)
            }
        }
    }

    // Build the default provider with explicit channel configuration
    // Note: DefaultMediaNotificationProvider.Builder only supports setChannelId() in Media3
    // Small icon is configured via drawable resource override (media3_notification_small_icon.xml)
    private val defaultProvider: DefaultMediaNotificationProvider =
        DefaultMediaNotificationProvider
            .Builder(service)
            // The default provider also posts artwork-loaded updates through its callback.
            // Keep those updates on the same notification ID as the initial notification.
            .setNotificationId(MEDIA_NOTIFICATION_ID)
            .setChannelId(NotificationHelper.CHANNEL_ID)
            .build()
            .also {
                LogUtils.i(
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
        // Wrap the callback so lastNotification tracks every notification the
        // default provider posts (including async artwork updates) — otherwise
        // invalidateNotification() would re-post a stale cached one.
        val trackedCallback = trackingCallback(onNotificationChangedCallback)
        notificationCallback = trackedCallback
        val filteredLayout = filterCustomLayout(customLayout)
        LogUtils.d(
            "AudioPlayerNotificationProvider",
            "createNotification called. Session: ${mediaSession.token}, " +
                "custom buttons: ${customLayout.size} -> filtered: ${filteredLayout.size}",
        )
        val mediaNotification =
            defaultProvider.createNotification(
                mediaSession,
                filteredLayout,
                actionFactory,
                trackedCallback,
            )
        LogUtils.d("AudioPlayerNotificationProvider", "Notification created: ${mediaNotification.notification}")

        // Override subtitle with chapter progress if set
        service.notificationSubtitleOverride?.let { subtitle ->
            mediaNotification.notification.extras?.putString(
                android.app.Notification.EXTRA_SUB_TEXT,
                subtitle,
            )
        }
        mediaNotification.notification.visibility = visibilityFor(cachedLockscreenPrivate)

        lastNotification = mediaNotification
        return mediaNotification
    }

    /** Pushes notification with current subtitle override (for chapter progress updates). */
    public fun invalidateNotification() {
        val last = lastNotification ?: return
        service.notificationSubtitleOverride?.let { subtitle ->
            last.notification.extras?.putString(
                android.app.Notification.EXTRA_SUB_TEXT,
                subtitle,
            )
        }
        notificationCallback?.onNotificationChanged(last)
    }

    private fun trackingCallback(delegate: MediaNotification.Provider.Callback): MediaNotification.Provider.Callback =
        object : MediaNotification.Provider.Callback {
            override fun onNotificationChanged(notification: MediaNotification) {
                // Artwork-loaded updates bypass createNotification() — re-apply lockscreen visibility here.
                notification.notification.visibility = visibilityFor(cachedLockscreenPrivate)
                lastNotification = notification
                delegate.onNotificationChanged(notification)
            }
        }

    /**
     * ponytail: Media3 1.11 setSlots API fully honored — Proto filtering is fallback for OEMs that ignore slots.
     * When slots are honored, this becomes no-op; kept to support legacy devices.
     */
    private fun filterCustomLayout(customLayout: ImmutableList<CommandButton>): ImmutableList<CommandButton> {
        if (customLayout.isEmpty()) return customLayout
        // 1.11: slots already placed per-surface; Proto filter only for ignored slots (OEM compat)
        val preferredSlots = cachedNotificationActionSlots
        if (preferredSlots == null) return customLayout

        val filtered =
            customLayout.filter { button ->
                notificationSlot(button)?.let { it in preferredSlots } ?: true
            }
        return ImmutableList.copyOf(filtered)
    }

    private fun notificationSlot(button: CommandButton): Int? =
        button.sessionCommand?.customAction?.let(::slotIdForCustomAction)
            ?: slotIdForPlayerCommand(button.playerCommand)

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = defaultProvider.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            NotificationHelper.CHANNEL_ID,
            service.getString(com.jabook.app.jabook.R.string.notification_channel_name),
        )

    public companion object {
        internal const val MEDIA_NOTIFICATION_ID: Int = NotificationHelper.NOTIFICATION_ID

        // Notification action slot IDs — must match proto enum values
        public const val SLOT_REWIND_30: Int = 0
        public const val SLOT_FORWARD_30: Int = 1
        public const val SLOT_BOOKMARK: Int = 2
        public const val SLOT_SLEEP_TIMER: Int = 3
        public const val SLOT_SPEED: Int = 4
        public const val SLOT_CHAPTER_PREV: Int = 5
        public const val SLOT_CHAPTER_NEXT: Int = 6

        internal fun slotIdForCustomAction(customAction: String): Int? =
            when (customAction) {
                CUSTOM_COMMAND_REWIND -> SLOT_REWIND_30
                CUSTOM_COMMAND_FORWARD -> SLOT_FORWARD_30
                else -> null
            }

        internal fun slotIdForPlayerCommand(playerCommand: Int): Int? =
            when (playerCommand) {
                Player.COMMAND_SEEK_BACK -> SLOT_REWIND_30
                Player.COMMAND_SEEK_FORWARD -> SLOT_FORWARD_30
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> SLOT_CHAPTER_PREV
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> SLOT_CHAPTER_NEXT
                else -> null
            }

        internal fun visibilityFor(lockscreenPrivate: Boolean): Int =
            if (lockscreenPrivate) {
                android.app.Notification.VISIBILITY_PRIVATE
            } else {
                android.app.Notification.VISIBILITY_PUBLIC
            }
    }
}
