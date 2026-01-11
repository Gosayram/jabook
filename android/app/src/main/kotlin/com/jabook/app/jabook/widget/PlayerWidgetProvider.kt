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

package com.jabook.app.jabook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.AppWidgetTarget
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.ComposeMainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Widget provider for quick access to audio player controls.
 *
 * Displays:
 * - Cover image
 * - Book title and author
 * - Progress bar with time labels
 * - Play/Pause, Next, Previous buttons
 * - Speed, Repeat, Timer buttons
 *
 * Clicking the widget opens the player screen.
 */
public class PlayerWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Debounce updates to prevent excessive widget refreshes
    private val updateJobs = mutableMapOf<Int, kotlinx.coroutines.Job>()
    private val debounceDelayMs = 300L

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)

        // Update widget when playback state changes
        if (
            intent.action == ACTION_UPDATE_WIDGET ||
            intent.action == "com.jabook.app.jabook.PLAYBACK_STATE_CHANGED" ||
            intent.action == "com.jabook.app.jabook.MEDIA_ITEM_CHANGED"
        ) {
            public val appWidgetManager = AppWidgetManager.getInstance(context)
            public val componentName = ComponentName(context, PlayerWidgetProvider::class.java)
            public val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    /**
     * Updates a single widget instance.
     * Uses MediaSession to get player state, which is more reliable than singleton instance.
     * Includes debouncing to prevent excessive updates.
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        // Cancel any pending update for this widget
        updateJobs[appWidgetId]?.cancel()

        // Schedule debounced update
        updateJobs[appWidgetId] =
            scope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(debounceDelayMs)
                updateAppWidgetInternal(context, appWidgetManager, appWidgetId)
            }
    }

    /**
     * Internal method that performs the actual widget update.
     */
    private fun updateAppWidgetInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        scope.launch(Dispatchers.IO) {
            public var controller: MediaController? = null
            public var controllerFuture: ListenableFuture<MediaController>? = null
            try {
                // Determine widget size and select appropriate layout
                public val widgetSize = getWidgetSize(context, appWidgetManager, appWidgetId)
                public val layoutResId = getLayoutForSize(widgetSize)
                public val views = RemoteViews(context.packageName, layoutResId)

                // Try to get MediaController for AudioPlayerService
                try {
                    public val sessionToken =
                        SessionToken(
                            context,
                            ComponentName(context, AudioPlayerService::class.java),
                        )

                    controllerFuture =
                        MediaController
                            .Builder(context, sessionToken)
                            .buildAsync()

                    // Wait for controller with timeout (faster for widget UX)
                    controller =
                        controllerFuture?.get(
                            com.jabook.app.jabook.audio.MediaControllerConstants.WIDGET_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS,
                        )

                    if (controller != null) {
                        updateWidgetFromController(context, views, controller, widgetSize, appWidgetManager, appWidgetId)
                    } else {
                        // Fallback to service instance if MediaController is not available
                        updateWidgetFromService(context, views, widgetSize, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerWidget", "Failed to get MediaController, falling back to service", e)
                    // Fallback to service instance
                    updateWidgetFromService(context, views, widgetSize, appWidgetManager, appWidgetId)
                } finally {
                    // Release MediaController
                    controllerFuture?.let {
                        MediaController.releaseFuture(it)
                    }
                }

                // Update widget immediately (Glide will update cover asynchronously)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                // Note: Glide will update cover asynchronously via AppWidgetTarget
                // No need for second update - Glide handles it automatically
            } catch (e: Exception) {
                android.util.Log.e("PlayerWidget", "Failed to update widget", e)
                // Show default state on error
                try {
                    public val widgetSize = getWidgetSize(context, appWidgetManager, appWidgetId)
                    public val layoutResId = getLayoutForSize(widgetSize)
                    public val views = RemoteViews(context.packageName, layoutResId)
                    setDefaultWidgetState(context, views, widgetSize)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e2: Exception) {
                    android.util.Log.e("PlayerWidget", "Failed to set default widget state", e2)
                }
            }
        }
    }

    /**
     * Gets widget size based on dimensions.
     */
    private fun getWidgetSize(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ): WidgetSize {
        public val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        public val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        public val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        // Convert from dp to approximate size
        // 1 cell = ~70dp on most devices
        public val widthCells = (minWidth + 30) / 70
        public val heightCells = (minHeight + 30) / 70

        return when {
            widthCells <= 2 && heightCells <= 1 -> WidgetSize.MINIMAL
            widthCells <= 3 && heightCells <= 2 -> WidgetSize.SMALL
            widthCells >= 4 && heightCells >= 4 -> WidgetSize.LARGE
            else -> WidgetSize.MEDIUM
        }
    }

    /**
     * Gets layout resource ID for widget size.
     */
    private fun getLayoutForSize(size: WidgetSize): Int =
        when (size) {
            WidgetSize.MINIMAL -> R.layout.widget_player_minimal
            WidgetSize.SMALL -> R.layout.widget_player_small
            WidgetSize.MEDIUM -> R.layout.widget_player
            WidgetSize.LARGE -> R.layout.widget_player_large
        }

    /**
     * Updates widget from MediaController.
     */
    private fun updateWidgetFromController(
        context: Context,
        views: RemoteViews,
        controller: MediaController,
        widgetSize: WidgetSize,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        public val isPlaying = controller.isPlaying
        public val currentMediaItem = controller.currentMediaItem
        public val mediaMetadata = currentMediaItem?.mediaMetadata

        // Get book information from metadata
        public var bookTitle =
            mediaMetadata?.albumTitle?.toString()
                ?: mediaMetadata?.title?.toString()
                ?: context.getString(R.string.no_book_playing)
        public val bookAuthor = mediaMetadata?.artist?.toString()

        // Update book title and author
        views.setTextViewText(R.id.widget_book_title, bookTitle)
        safeUpdateView(views, R.id.widget_book_author) {
            if (!bookAuthor.isNullOrBlank()) {
                views.setTextViewText(R.id.widget_book_author, bookAuthor)
                views.setViewVisibility(R.id.widget_book_author, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
            }
        }

        // Update cover image (if present in layout) - load with Glide for better compatibility
        public val artworkUri = mediaMetadata?.artworkUri
        safeUpdateView(views, R.id.widget_cover) {
            if (artworkUri != null) {
                try {
                    // Use Glide to load bitmap for widget (more reliable than setImageViewUri)
                    public val widgetTarget = AppWidgetTarget(context, appWidgetId, views, R.id.widget_cover)

                    Glide
                        .with(context.applicationContext)
                        .asBitmap()
                        .load(artworkUri)
                        .override(512, 512) // Widget-friendly size
                        .centerCrop()
                        .into(widgetTarget)
                } catch (e: Exception) {
                    android.util.Log.w("PlayerWidget", "Failed to load cover image with Glide", e)
                    // Fallback to URI method
                    try {
                        views.setImageViewUri(R.id.widget_cover, artworkUri)
                    } catch (e2: Exception) {
                        android.util.Log.w("PlayerWidget", "Failed to set cover image URI", e2)
                        views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
                    }
                }
            } else {
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            }
        }

        // Update progress (if present in layout)
        public val currentPosition = controller.currentPosition
        public val duration = controller.duration
        safeUpdateView(views, R.id.widget_progress) {
            updateProgress(views, currentPosition, duration, widgetSize)
        }

        // Update play/pause button
        public val playPauseIcon =
            if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        // Get repeat mode and playback speed
        public val repeatMode = controller.repeatMode
        public val playbackSpeed = controller.playbackParameters.speed

        // Update repeat button state (if present in layout)
        safeUpdateView(views, R.id.widget_repeat) {
            public val repeatIcon = getRepeatIcon(context, repeatMode)
            views.setImageViewResource(R.id.widget_repeat, repeatIcon)
        }

        // Get book ID from metadata or service
        // For widget updates, we use async approach to avoid blocking
        public val currentBookId = mediaMetadata?.extras?.getString("bookId")

        // If not in metadata, we'll get it asynchronously via custom command
        // For now, use fallback to getInstance() for widget (widget updates are time-sensitive)
        // TODO: Implement async custom command call for widget updates
        public val currentBookIdFromService =
            if (currentBookId == null) {
                @Suppress("DEPRECATION")
                public val service = AudioPlayerService.getInstance()
                if (service != null && service.isFullyInitialized()) {
                    service.currentGroupPath
                } else {
                    null
                }
            } else {
                null
            }

        public val finalBookId = currentBookId ?: currentBookIdFromService

        // Set up click intents
        setupClickIntents(context, views, finalBookId, playbackSpeed, repeatMode, widgetSize)

        android.util.Log.d("PlayerWidget", "Widget updated via MediaController: book=$bookTitle, playing=$isPlaying")
    }

    /**
     * Fallback method to update widget from service instance.
     * This is used when MediaController is not available.
     */
    private fun updateWidgetFromService(
        context: Context,
        views: RemoteViews,
        widgetSize: WidgetSize,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        // Fallback: try to get service instance only if MediaController failed
        // This should rarely be needed now that we use custom commands
        @Suppress("DEPRECATION")
        public val service = AudioPlayerService.getInstance()
        if (service != null && service.isFullyInitialized()) {
            // Get player state
            public val playerState = service.getPlayerState()
            public val isPlaying = playerState["isPlaying"] as? Boolean ?: false
            public val currentPosition = playerState["currentPosition"] as? Long ?: 0L
            public val duration = playerState["duration"] as? Long ?: 0L
            public val currentBookId = service.currentGroupPath

            // Get book information if available
            public var bookTitle = context.getString(R.string.no_book_playing)
            public var bookAuthor: String? = null
            public var coverUri: Uri? = null

            if (currentBookId != null) {
                // Try to get book info from metadata
                try {
                    public val mediaInfo = service.getCurrentMediaItemInfo()
                    bookTitle = mediaInfo["title"] as? String
                        ?: mediaInfo["albumTitle"] as? String
                        ?: currentBookId.substringAfterLast("/").takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.no_book_playing)
                    bookAuthor = mediaInfo["artist"] as? String

                    // Try to get cover URI
                    public val artworkUri = mediaInfo["artworkUri"] as? Uri
                    if (artworkUri != null) {
                        coverUri = artworkUri
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerWidget", "Failed to get book info from service", e)
                }
            }

            // Update book title and author
            views.setTextViewText(R.id.widget_book_title, bookTitle)
            safeUpdateView(views, R.id.widget_book_author) {
                if (!bookAuthor.isNullOrBlank()) {
                    views.setTextViewText(R.id.widget_book_author, bookAuthor)
                    views.setViewVisibility(R.id.widget_book_author, android.view.View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
                }
            }

            // Update cover image (if present in layout) - load with Glide
            safeUpdateView(views, R.id.widget_cover) {
                if (coverUri != null) {
                    try {
                        // Use Glide to load bitmap for widget
                        public val widgetTarget = AppWidgetTarget(context, appWidgetId, views, R.id.widget_cover)

                        Glide
                            .with(context.applicationContext)
                            .asBitmap()
                            .load(coverUri)
                            .override(512, 512)
                            .centerCrop()
                            .into(widgetTarget)
                    } catch (e: Exception) {
                        android.util.Log.w("PlayerWidget", "Failed to load cover image with Glide from service", e)
                        // Fallback to URI method
                        try {
                            views.setImageViewUri(R.id.widget_cover, coverUri)
                        } catch (e2: Exception) {
                            android.util.Log.w("PlayerWidget", "Failed to set cover image URI from service", e2)
                            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
                        }
                    }
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
                }
            }

            // Update progress (if present in layout)
            safeUpdateView(views, R.id.widget_progress) {
                updateProgress(views, currentPosition, duration, widgetSize)
            }

            // Update play/pause button
            public val playPauseIcon =
                if (isPlaying) {
                    R.drawable.ic_pause
                } else {
                    R.drawable.ic_play
                }
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            // Get repeat mode and speed from service
            public val repeatMode = service.getRepeatMode()
            public val playbackSpeed = service.getPlaybackSpeed()

            // Update repeat button state (if present in layout)
            safeUpdateView(views, R.id.widget_repeat) {
                public val repeatIcon = getRepeatIcon(context, repeatMode)
                views.setImageViewResource(R.id.widget_repeat, repeatIcon)
            }

            // Set up click intents
            setupClickIntents(context, views, currentBookId, playbackSpeed, repeatMode, widgetSize)

            // Update widget immediately (Glide will update cover asynchronously)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Note: Glide will update cover asynchronously via AppWidgetTarget
            // No need for second update - Glide handles it automatically

            android.util.Log.d("PlayerWidget", "Widget updated via service: book=$bookTitle, playing=$isPlaying")
        } else {
            // Service not available - show default state
            setDefaultWidgetState(context, views, widgetSize)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Sets default widget state when no playback is active.
     */
    private fun setDefaultWidgetState(
        context: Context,
        views: RemoteViews,
        widgetSize: WidgetSize,
    ) {
        views.setTextViewText(R.id.widget_book_title, context.getString(R.string.no_book_playing))

        safeUpdateView(views, R.id.widget_book_author) {
            views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
        }

        safeUpdateView(views, R.id.widget_cover) {
            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
        }

        views.setImageViewResource(R.id.widget_play_pause, R.drawable.ic_play)

        safeUpdateView(views, R.id.widget_progress) {
            views.setProgressBar(R.id.widget_progress, 1000, 0, false)
        }

        safeUpdateView(views, R.id.widget_time_current) {
            views.setTextViewText(R.id.widget_time_current, "0:00")
        }

        safeUpdateView(views, R.id.widget_time_total) {
            views.setTextViewText(R.id.widget_time_total, "0:00")
        }

        safeUpdateView(views, R.id.widget_repeat) {
            views.setImageViewResource(R.id.widget_repeat, getRepeatIcon(context, Player.REPEAT_MODE_OFF))
        }

        // Set up click intents (will start service)
        setupClickIntents(context, views, null, 1.0f, Player.REPEAT_MODE_OFF, widgetSize)
    }

    /**
     * Safely updates a view if it exists in the layout.
     */
    private fun safeUpdateView(
        views: RemoteViews,
        viewId: Int,
        update: () -> Unit,
    ) {
        try {
            update()
        } catch (e: Exception) {
            // View doesn't exist in this layout, ignore
            android.util.Log.d("PlayerWidget", "View $viewId not found in layout, skipping")
        }
    }

    /**
     * Updates progress bar and time labels.
     */
    private fun updateProgress(
        views: RemoteViews,
        currentPosition: Long,
        duration: Long,
        widgetSize: WidgetSize,
    ) {
        if (duration > 0) {
            public val progress = ((currentPosition * 1000) / duration).toInt().coerceIn(0, 1000)
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)
        } else {
            views.setProgressBar(R.id.widget_progress, 1000, 0, false)
        }

        // Update time labels (if present in layout)
        safeUpdateView(views, R.id.widget_time_current) {
            views.setTextViewText(R.id.widget_time_current, formatTime(currentPosition))
        }
        safeUpdateView(views, R.id.widget_time_total) {
            views.setTextViewText(R.id.widget_time_total, formatTime(duration))
        }
    }

    /**
     * Formats time in milliseconds to MM:SS format.
     */
    private fun formatTime(timeMs: Long): String {
        if (timeMs <= 0) return "0:00"
        public val totalSeconds = (timeMs / 1000).toInt()
        public val minutes = totalSeconds / 60
        public val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Gets repeat icon based on repeat mode.
     */
    private fun getRepeatIcon(
        context: Context,
        repeatMode: Int,
    ): Int =
        when (repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                // Try ic_repeat_one, fallback to ic_repeat if not available
                try {
                    public val resId = context.resources.getIdentifier("ic_repeat_one", "drawable", context.packageName)
                    if (resId != 0) resId else R.drawable.ic_repeat
                } catch (e: Exception) {
                    R.drawable.ic_repeat
                }
            }
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
            else -> {
                // Try ic_repeat_off, fallback to ic_repeat if not available
                try {
                    public val resId = context.resources.getIdentifier("ic_repeat_off", "drawable", context.packageName)
                    if (resId != 0) resId else R.drawable.ic_repeat
                } catch (e: Exception) {
                    R.drawable.ic_repeat
                }
            }
        }

    /**
     * Sets up click intents for widget buttons.
     */
    private fun setupClickIntents(
        context: Context,
        views: RemoteViews,
        currentBookId: String?,
        playbackSpeed: Float,
        repeatMode: Int,
        widgetSize: WidgetSize,
    ) {
        public val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        // Play/Pause button (always present)
        public val playPauseIntent =
            Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            PendingIntent.getService(context, 0, playPauseIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
        )

        // Speed button - cycle through speeds - if present
        safeUpdateView(views, R.id.widget_speed) {
            public val speedIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = ACTION_SPEED
                }
            views.setOnClickPendingIntent(
                R.id.widget_speed,
                PendingIntent.getService(context, 4, speedIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        // Repeat button - cycle through repeat modes - if present
        safeUpdateView(views, R.id.widget_repeat) {
            public val repeatIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = ACTION_REPEAT
                }
            views.setOnClickPendingIntent(
                R.id.widget_repeat,
                PendingIntent.getService(context, 5, repeatIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        // Timer button - cycle through timer options - if present
        safeUpdateView(views, R.id.widget_timer) {
            public val timerIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = ACTION_TIMER
                }
            views.setOnClickPendingIntent(
                R.id.widget_timer,
                PendingIntent.getService(context, 6, timerIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        // Progress bar - seek to position on click - if present
        safeUpdateView(views, R.id.widget_progress) {
            // Note: ProgressBar clicks are handled via setOnClickPendingIntent on the progress bar itself
            // We'll use a custom action that opens player for now, as seeking requires position calculation
            public val seekIntent =
                Intent(context, ComposeMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = android.net.Uri.parse("jabook://player${if (currentBookId != null) "?bookId=$currentBookId" else ""}")
                }
            views.setOnClickPendingIntent(
                R.id.widget_progress,
                PendingIntent.getActivity(context, 7, seekIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        // Previous and Next buttons - if present
        safeUpdateView(views, R.id.widget_previous) {
            public val previousIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = ACTION_PREVIOUS
                }
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                PendingIntent.getService(context, 2, previousIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        safeUpdateView(views, R.id.widget_next) {
            public val nextIntent =
                Intent(context, AudioPlayerService::class.java).apply {
                    action = ACTION_NEXT
                }
            views.setOnClickPendingIntent(
                R.id.widget_next,
                PendingIntent.getService(context, 1, nextIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

        // Widget click - open player screen
        public val openPlayerIntent =
            Intent(context, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = android.net.Uri.parse("jabook://player${if (currentBookId != null) "?bookId=$currentBookId" else ""}")
            }
        views.setOnClickPendingIntent(
            R.id.widget_content,
            PendingIntent.getActivity(context, 3, openPlayerIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    public companion object {
        public const val ACTION_UPDATE_WIDGET: String = "com.jabook.app.jabook.UPDATE_WIDGET"
        public const val ACTION_PLAY_PAUSE: String = "com.jabook.app.jabook.WIDGET_PLAY_PAUSE"
        public const val ACTION_NEXT: String = "com.jabook.app.jabook.WIDGET_NEXT"
        public const val ACTION_PREVIOUS: String = "com.jabook.app.jabook.WIDGET_PREVIOUS"
        public const val ACTION_REPEAT: String = "com.jabook.app.jabook.WIDGET_REPEAT"
        public const val ACTION_SPEED: String = "com.jabook.app.jabook.WIDGET_SPEED"
        public const val ACTION_TIMER: String = "com.jabook.app.jabook.WIDGET_TIMER"

        /**
         * Requests widget update from anywhere in the app.
         */
        public fun requestUpdate(context: Context) {
            val intent =
                Intent(context, PlayerWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
            context.sendBroadcast(intent)
        }
    }
}

/**
 * Widget size enum for different widget layouts.
 */
private enum class WidgetSize {
    MINIMAL, // Minimal widget: cover + title + play/pause
    SMALL, // Small widget: cover + title + progress + basic controls
    MEDIUM, // Medium widget: full features
    LARGE, // Large square widget: all features with better layout
}
