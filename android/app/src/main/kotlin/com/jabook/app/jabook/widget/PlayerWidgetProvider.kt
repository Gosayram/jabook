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
    private val updateJobRegistry = WidgetUpdateJobRegistry()
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
            android.util.Log.d(
                "PlayerWidget",
                WidgetObservabilityPolicy.providerMessage(
                    event = "update_broadcast_received",
                    widgetId = WidgetObservabilityPolicy.UNKNOWN_WIDGET_ID,
                    source = WidgetUpdateSource.BROADCAST,
                    detail = "action=${intent.action}",
                ),
            )
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PlayerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        updateJobRegistry.cancelForIds(appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        updateJobRegistry.cancelAll()
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
        val updateJob =
            scope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(debounceDelayMs)
                updateAppWidgetInternal(context, appWidgetManager, appWidgetId)
            }
        updateJobRegistry.replace(appWidgetId, updateJob)
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
            android.util.Log.d(
                "PlayerWidget",
                WidgetObservabilityPolicy.providerMessage(
                    event = "update_start",
                    widgetId = appWidgetId,
                ),
            )
            var controller: MediaController? = null
            var controllerFuture: ListenableFuture<MediaController>? = null
            try {
                // Determine widget size and select appropriate layout
                val widgetSize = getWidgetSize(context, appWidgetManager, appWidgetId)
                val layoutResId = getLayoutForSize(widgetSize)
                val views = RemoteViews(context.packageName, layoutResId)

                // Try to get MediaController for AudioPlayerService
                try {
                    val sessionToken =
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
                        controllerFuture.get(
                            com.jabook.app.jabook.audio.MediaControllerConstants.WIDGET_TIMEOUT_SECONDS
                                .toLong(),
                            TimeUnit.SECONDS,
                        )

                    if (controller != null) {
                        updateWidgetFromController(context, views, controller, widgetSize, appWidgetManager, appWidgetId)
                    } else {
                        android.util.Log.w(
                            "PlayerWidget",
                            WidgetObservabilityPolicy.providerMessage(
                                event = "controller_fallback",
                                widgetId = appWidgetId,
                                source = WidgetUpdateSource.SERVICE_FALLBACK,
                                reason = WidgetFallbackReason.CONTROLLER_UNAVAILABLE,
                            ),
                        )
                        // Fallback to service instance if MediaController is not available
                        updateWidgetFromService(context, views, widgetSize, appWidgetManager, appWidgetId)
                    }
                } catch (e: Exception) {
                    android.util.Log.w(
                        "PlayerWidget",
                        WidgetObservabilityPolicy.providerMessage(
                            event = "controller_fallback",
                            widgetId = appWidgetId,
                            source = WidgetUpdateSource.SERVICE_FALLBACK,
                            reason = WidgetFallbackReason.CONTROLLER_EXCEPTION,
                            detail = e.javaClass.simpleName,
                        ),
                        e,
                    )
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
                android.util.Log.e(
                    "PlayerWidget",
                    WidgetObservabilityPolicy.providerMessage(
                        event = "update_failed",
                        widgetId = appWidgetId,
                        source = WidgetUpdateSource.DEFAULT_STATE,
                        reason = WidgetFallbackReason.UPDATE_EXCEPTION,
                        detail = e.javaClass.simpleName,
                    ),
                    e,
                )
                // Show default state on error
                try {
                    val widgetSize = getWidgetSize(context, appWidgetManager, appWidgetId)
                    val layoutResId = getLayoutForSize(widgetSize)
                    val views = RemoteViews(context.packageName, layoutResId)
                    setDefaultWidgetState(context, views, appWidgetId)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e2: Exception) {
                    android.util.Log.e(
                        "PlayerWidget",
                        WidgetObservabilityPolicy.providerMessage(
                            event = "default_state_failed",
                            widgetId = appWidgetId,
                            source = WidgetUpdateSource.DEFAULT_STATE,
                            reason = WidgetFallbackReason.UPDATE_EXCEPTION,
                            detail = e2.javaClass.simpleName,
                        ),
                        e2,
                    )
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
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        // Convert from dp to approximate size
        // 1 cell = ~70dp on most devices
        val widthCells = (minWidth + 30) / 70
        val heightCells = (minHeight + 30) / 70

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
        val isPlaying = controller.isPlaying
        val currentMediaItem = controller.currentMediaItem
        val shouldFallbackToService =
            WidgetControllerSnapshotPolicy.shouldFallbackToService(
                hasCurrentMediaItem = currentMediaItem != null,
                playbackState = controller.playbackState,
                isPlaying = isPlaying,
            )
        if (shouldFallbackToService) {
            android.util.Log.w(
                "PlayerWidget",
                WidgetObservabilityPolicy.providerMessage(
                    event = "controller_fallback",
                    widgetId = appWidgetId,
                    source = WidgetUpdateSource.SERVICE_FALLBACK,
                    reason = WidgetFallbackReason.CONTROLLER_STALE_SNAPSHOT,
                ),
            )
            updateWidgetFromService(context, views, widgetSize, appWidgetManager, appWidgetId)
            return
        }
        val mediaMetadata = currentMediaItem?.mediaMetadata

        // Get book information from metadata
        var bookTitle =
            mediaMetadata?.albumTitle?.toString()
                ?: mediaMetadata?.title?.toString()
                ?: context.getString(R.string.no_book_playing)
        val bookAuthor = mediaMetadata?.artist?.toString()

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
        val artworkUri = mediaMetadata?.artworkUri
        safeUpdateView(views, R.id.widget_cover) {
            updateCoverImage(context, views, appWidgetId, artworkUri)
        }

        // Update progress (if present in layout)
        val currentPosition = controller.currentPosition
        val duration = controller.duration
        safeUpdateView(views, R.id.widget_progress) {
            updateProgress(views, currentPosition, duration, widgetSize)
        }

        // Update play/pause button
        val playPauseIcon =
            if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        // Get repeat mode and playback speed
        val repeatMode = controller.repeatMode

        // Update repeat button state (if present in layout)
        safeUpdateView(views, R.id.widget_repeat) {
            val repeatIcon = getRepeatIcon(context, repeatMode)
            views.setImageViewResource(R.id.widget_repeat, repeatIcon)
        }

        // Get book ID from metadata or service
        // For widget updates, we use async approach to avoid blocking
        val currentBookId = mediaMetadata?.extras?.getString("bookId")

        // If not in metadata, we'll get it asynchronously via custom command
        // For now, use fallback to getInstance() for widget (widget updates are time-sensitive)
        // TODO: Implement async custom command call for widget updates
        val currentBookIdFromService =
            if (currentBookId == null) {
                @Suppress("DEPRECATION")
                val service = AudioPlayerService.getInstance()
                if (service != null && service.isFullyInitialized()) {
                    service.currentGroupPath
                } else {
                    null
                }
            } else {
                null
            }

        val finalBookId = currentBookId ?: currentBookIdFromService

        // Set up click intents
        setupClickIntents(context, views, finalBookId, appWidgetId)

        android.util.Log.d(
            "PlayerWidget",
            WidgetObservabilityPolicy.providerMessage(
                event = "update_success",
                widgetId = appWidgetId,
                source = WidgetUpdateSource.CONTROLLER,
                detail = "playing=$isPlaying",
            ),
        )
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
        val service = AudioPlayerService.getInstance()
        if (service != null && service.isFullyInitialized()) {
            // Get player state
            val playerState = service.getPlayerState()
            val isPlaying = playerState["isPlaying"] as? Boolean ?: false
            val currentPosition = playerState["currentPosition"] as? Long ?: 0L
            val duration = playerState["duration"] as? Long ?: 0L
            val currentBookId = service.currentGroupPath

            // Get book information if available
            var bookTitle = context.getString(R.string.no_book_playing)
            var bookAuthor: String? = null
            var coverUri: Uri? = null

            if (currentBookId != null) {
                // Try to get book info from metadata
                try {
                    val mediaInfo = service.getCurrentMediaItemInfo()
                    bookTitle = mediaInfo["title"] as? String
                        ?: mediaInfo["albumTitle"] as? String
                        ?: currentBookId.substringAfterLast("/").takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.no_book_playing)
                    bookAuthor = mediaInfo["artist"] as? String

                    // Try to get cover URI
                    val artworkUri = mediaInfo["artworkUri"] as? Uri
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
                updateCoverImage(context, views, appWidgetId, coverUri)
            }

            // Update progress (if present in layout)
            safeUpdateView(views, R.id.widget_progress) {
                updateProgress(views, currentPosition, duration, widgetSize)
            }

            // Update play/pause button
            val playPauseIcon =
                if (isPlaying) {
                    R.drawable.ic_pause
                } else {
                    R.drawable.ic_play
                }
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            // Get repeat mode and speed from service
            val repeatMode = service.getRepeatMode()

            // Update repeat button state (if present in layout)
            safeUpdateView(views, R.id.widget_repeat) {
                val repeatIcon = getRepeatIcon(context, repeatMode)
                views.setImageViewResource(R.id.widget_repeat, repeatIcon)
            }

            // Set up click intents
            setupClickIntents(context, views, currentBookId, appWidgetId)

            // Update widget immediately (Glide will update cover asynchronously)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Note: Glide will update cover asynchronously via AppWidgetTarget
            // No need for second update - Glide handles it automatically

            android.util.Log.d(
                "PlayerWidget",
                WidgetObservabilityPolicy.providerMessage(
                    event = "update_success",
                    widgetId = appWidgetId,
                    source = WidgetUpdateSource.SERVICE_FALLBACK,
                    detail = "playing=$isPlaying",
                ),
            )
        } else {
            // Service not available - show default state
            android.util.Log.w(
                "PlayerWidget",
                WidgetObservabilityPolicy.providerMessage(
                    event = "default_state_applied",
                    widgetId = appWidgetId,
                    source = WidgetUpdateSource.DEFAULT_STATE,
                    reason = WidgetFallbackReason.SERVICE_UNAVAILABLE,
                ),
            )
            setDefaultWidgetState(context, views, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Sets default widget state when no playback is active.
     */
    private fun setDefaultWidgetState(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
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
        setupClickIntents(context, views, null, appWidgetId)
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
            val progress = ((currentPosition * 1000) / duration).toInt().coerceIn(0, 1000)
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
        val totalSeconds = (timeMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
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
                    val resId = context.resources.getIdentifier("ic_repeat_one", "drawable", context.packageName)
                    if (resId != 0) resId else R.drawable.ic_repeat
                } catch (e: Exception) {
                    R.drawable.ic_repeat
                }
            }
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
            else -> {
                // Try ic_repeat_off, fallback to ic_repeat if not available
                try {
                    val resId = context.resources.getIdentifier("ic_repeat_off", "drawable", context.packageName)
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
        appWidgetId: Int,
    ) {
        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        // Play/Pause button (always present)
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            createServicePendingIntent(
                context = context,
                action = ACTION_PLAY_PAUSE,
                appWidgetId = appWidgetId,
                pendingIntentFlags = pendingIntentFlags,
            ),
        )

        // Speed button - cycle through speeds - if present
        safeUpdateView(views, R.id.widget_speed) {
            views.setOnClickPendingIntent(
                R.id.widget_speed,
                createServicePendingIntent(
                    context = context,
                    action = ACTION_SPEED,
                    appWidgetId = appWidgetId,
                    pendingIntentFlags = pendingIntentFlags,
                ),
            )
        }

        // Repeat button - cycle through repeat modes - if present
        safeUpdateView(views, R.id.widget_repeat) {
            views.setOnClickPendingIntent(
                R.id.widget_repeat,
                createServicePendingIntent(
                    context = context,
                    action = ACTION_REPEAT,
                    appWidgetId = appWidgetId,
                    pendingIntentFlags = pendingIntentFlags,
                ),
            )
        }

        // Timer button - cycle through timer options - if present
        safeUpdateView(views, R.id.widget_timer) {
            views.setOnClickPendingIntent(
                R.id.widget_timer,
                createServicePendingIntent(
                    context = context,
                    action = ACTION_TIMER,
                    appWidgetId = appWidgetId,
                    pendingIntentFlags = pendingIntentFlags,
                ),
            )
        }

        // Progress bar - seek to position on click - if present
        safeUpdateView(views, R.id.widget_progress) {
            // Note: ProgressBar clicks are handled via setOnClickPendingIntent on the progress bar itself
            // We'll use a custom action that opens player for now, as seeking requires position calculation
            views.setOnClickPendingIntent(
                R.id.widget_progress,
                createOpenPlayerPendingIntent(
                    context = context,
                    currentBookId = currentBookId,
                    appWidgetId = appWidgetId,
                    routeAction = WidgetActionRoutingPolicy.ROUTE_OPEN_PLAYER_PROGRESS,
                    pendingIntentFlags = pendingIntentFlags,
                ),
            )
        }

        // Previous and Next buttons - if present
        safeUpdateView(views, R.id.widget_previous) {
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                createServicePendingIntent(
                    context = context,
                    action = ACTION_PREVIOUS,
                    appWidgetId = appWidgetId,
                    pendingIntentFlags = pendingIntentFlags,
                ),
            )
        }

        safeUpdateView(views, R.id.widget_next) {
            views.setOnClickPendingIntent(
                R.id.widget_next,
                createServicePendingIntent(
                    context = context,
                    action = ACTION_NEXT,
                    appWidgetId = appWidgetId,
                    pendingIntentFlags = pendingIntentFlags,
                ),
            )
        }

        // Widget click - open player screen
        views.setOnClickPendingIntent(
            R.id.widget_content,
            createOpenPlayerPendingIntent(
                context = context,
                currentBookId = currentBookId,
                appWidgetId = appWidgetId,
                routeAction = WidgetActionRoutingPolicy.ROUTE_OPEN_PLAYER,
                pendingIntentFlags = pendingIntentFlags,
            ),
        )
    }

    private fun createServicePendingIntent(
        context: Context,
        action: String,
        appWidgetId: Int,
        pendingIntentFlags: Int,
    ): PendingIntent {
        val intent =
            Intent(context, AudioPlayerService::class.java).apply {
                this.action = action
                `package` = context.packageName
                putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
                putExtra(EXTRA_WIDGET_ACTION_CREATED_AT_MS, System.currentTimeMillis())
            }

        return PendingIntent.getService(
            context,
            WidgetActionRoutingPolicy.requestCodeForAction(appWidgetId, action),
            intent,
            pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createOpenPlayerPendingIntent(
        context: Context,
        currentBookId: String?,
        appWidgetId: Int,
        routeAction: String,
        pendingIntentFlags: Int,
    ): PendingIntent {
        val openPlayerIntent =
            Intent(context, ComposeMainActivity::class.java).apply {
                action = routeAction
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = WidgetDeepLinkPolicy.buildPlayerDeepLink(currentBookId, appWidgetId)
                putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
            }

        return PendingIntent.getActivity(
            context,
            WidgetActionRoutingPolicy.requestCodeForAction(appWidgetId, routeAction),
            openPlayerIntent,
            pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun updateCoverImage(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        artworkUri: Uri?,
    ) {
        if (artworkUri == null) {
            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            return
        }

        try {
            val widgetTarget = AppWidgetTarget(context, appWidgetId, views, R.id.widget_cover)
            Glide.with(context.applicationContext).clear(widgetTarget)

            Glide
                .with(context.applicationContext)
                .asBitmap()
                .load(artworkUri)
                .override(WidgetCoverLoadPolicy.COVER_SIZE_PX, WidgetCoverLoadPolicy.COVER_SIZE_PX)
                .timeout(WidgetCoverLoadPolicy.COVER_TIMEOUT_MS)
                .centerCrop()
                .fallback(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(widgetTarget)
        } catch (e: Exception) {
            android.util.Log.w("PlayerWidget", "Failed to load cover with Glide, trying URI fallback", e)
            try {
                views.setImageViewUri(R.id.widget_cover, artworkUri)
            } catch (e2: Exception) {
                android.util.Log.w("PlayerWidget", "Failed to set cover URI fallback", e2)
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            }
        }
    }

    public companion object {
        public const val ACTION_UPDATE_WIDGET: String = "com.jabook.app.jabook.UPDATE_WIDGET"
        public const val ACTION_PLAY_PAUSE: String = "com.jabook.app.jabook.WIDGET_PLAY_PAUSE"
        public const val ACTION_NEXT: String = "com.jabook.app.jabook.WIDGET_NEXT"
        public const val ACTION_PREVIOUS: String = "com.jabook.app.jabook.WIDGET_PREVIOUS"
        public const val ACTION_REPEAT: String = "com.jabook.app.jabook.WIDGET_REPEAT"
        public const val ACTION_SPEED: String = "com.jabook.app.jabook.WIDGET_SPEED"
        public const val ACTION_TIMER: String = "com.jabook.app.jabook.WIDGET_TIMER"
        public const val EXTRA_APP_WIDGET_ID: String = "com.jabook.app.jabook.EXTRA_APP_WIDGET_ID"
        public const val EXTRA_WIDGET_ACTION_CREATED_AT_MS: String =
            "com.jabook.app.jabook.EXTRA_WIDGET_ACTION_CREATED_AT_MS"

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
