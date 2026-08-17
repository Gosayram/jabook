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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.ComposeMainActivity
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("PlayerWidgetProvider"),
        )

    // Debounce updates to prevent excessive widget refreshes
    private val updateJobRegistry = WidgetUpdateJobRegistry()
    private val debounceDelayMs = 300L

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicUpdate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        schedulePeriodicUpdate(context)
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
            // For periodic alarm: skip if nothing is playing to save battery
            if (intent.action == ACTION_UPDATE_WIDGET && !isPlaybackActive(context)) {
                cancelPeriodicUpdate(context)
                return
            }

            LogUtils.d(
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
        cancelPeriodicUpdate(context)
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
            LogUtils.d(
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
                        updateWidgetFromController(
                            context,
                            views,
                            controller,
                            widgetSize,
                            appWidgetManager,
                            appWidgetId,
                        )
                    } else {
                        LogUtils.w(
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
                    LogUtils.w(
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

                // Update widget immediately (Coil will update cover asynchronously)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                // Note: Coil will update cover asynchronously and re-post the widget
            } catch (e: Exception) {
                LogUtils.e(
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
                    LogUtils.e(
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
     * Controller reads must happen on main thread (Media3 requirement).
     */
    private suspend fun updateWidgetFromController(
        context: Context,
        views: RemoteViews,
        controller: MediaController,
        widgetSize: WidgetSize,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) = withContext(Dispatchers.Main) {
        val isPlaying = controller.isPlaying
        val currentMediaItem = controller.currentMediaItem
        val shouldFallbackToService =
            WidgetControllerSnapshotPolicy.shouldFallbackToService(
                hasCurrentMediaItem = currentMediaItem != null,
                playbackState = controller.playbackState,
                isPlaying = isPlaying,
            )
        if (shouldFallbackToService) {
            LogUtils.w(
                "PlayerWidget",
                WidgetObservabilityPolicy.providerMessage(
                    event = "controller_fallback",
                    widgetId = appWidgetId,
                    source = WidgetUpdateSource.SERVICE_FALLBACK,
                    reason = WidgetFallbackReason.CONTROLLER_STALE_SNAPSHOT,
                ),
            )
            updateWidgetFromService(context, views, widgetSize, appWidgetManager, appWidgetId)
            return@withContext
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

        // Update cover image (if present in layout) - load with Coil for better compatibility
        val artworkUri = mediaMetadata?.artworkUri
        safeUpdateView(views, R.id.widget_cover) {
            updateCoverImage(context, views, appWidgetId, artworkUri)
        }

        // Get book ID from metadata or service
        val currentBookId = mediaMetadata?.extras?.getString("bookId")

        // Update progress (if present in layout)
        val currentPosition = controller.currentPosition
        val duration = controller.duration
        val chapterTitle = currentMediaItem?.mediaMetadata?.title?.toString()
        safeUpdateView(views, R.id.widget_progress) {
            updateProgress(
                context = context,
                views = views,
                currentPosition = currentPosition,
                duration = duration,
                widgetSize = widgetSize,
                bookId = currentBookId,
                chapterTitle = chapterTitle,
            )
        }

        // Update play/pause button
        val playPauseIcon =
            if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        // Get repeat mode
        val repeatMode = controller.repeatMode

        // Update repeat button state (if present in layout)
        safeUpdateView(views, R.id.widget_repeat) {
            val repeatIcon = getRepeatIcon(context, repeatMode)
            views.setImageViewResource(R.id.widget_repeat, repeatIcon)
        }

        // Get book ID from service if not in metadata
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

        LogUtils.d(
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
    private suspend fun updateWidgetFromService(
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
                    LogUtils.w("PlayerWidget", "Failed to get book info from service", e)
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

            // Update cover image (if present in layout) - load with Coil
            safeUpdateView(views, R.id.widget_cover) {
                updateCoverImage(context, views, appWidgetId, coverUri)
            }

            // Update progress (if present in layout)
            val chapterTitle = playerState["currentChapterTitle"] as? String
            safeUpdateView(views, R.id.widget_progress) {
                updateProgress(
                    context = context,
                    views = views,
                    currentPosition = currentPosition,
                    duration = duration,
                    widgetSize = widgetSize,
                    bookId = currentBookId,
                    chapterTitle = chapterTitle,
                )
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

            // Update widget immediately (Coil will update cover asynchronously)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Note: Coil will update cover asynchronously
            // No need for second update - Coil handles it automatically

            LogUtils.d(
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
            LogUtils.w(
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
    private suspend fun setDefaultWidgetState(
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

        safeUpdateView(views, R.id.widget_chapter_title) {
            views.setViewVisibility(R.id.widget_chapter_title, android.view.View.GONE)
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
    private suspend fun safeUpdateView(
        views: RemoteViews,
        viewId: Int,
        update: suspend () -> Unit,
    ) {
        try {
            update()
        } catch (e: Exception) {
            // View doesn't exist in this layout, ignore
            LogUtils.d("PlayerWidget", "View $viewId not found in layout, skipping")
        }
    }

    /**
     * Updates progress bar and time labels.
     * Progress bar shows book-level progress (across all chapters) when bookId is available.
     * Time labels show chapter-level progress.
     */
    private suspend fun updateProgress(
        context: Context,
        views: RemoteViews,
        currentPosition: Long,
        duration: Long,
        widgetSize: WidgetSize,
        bookId: String? = null,
        chapterTitle: String? = null,
    ) {
        // Chapter title (large layout)
        safeUpdateView(views, R.id.widget_chapter_title) {
            if (!chapterTitle.isNullOrBlank()) {
                views.setTextViewText(R.id.widget_chapter_title, chapterTitle)
                views.setViewVisibility(R.id.widget_chapter_title, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_chapter_title, android.view.View.GONE)
            }
        }

        // Book-level progress bar
        safeUpdateView(views, R.id.widget_progress) {
            var bookProgress = 0f
            if (bookId != null) {
                val bp = getBookProgress(context, bookId)
                if (bp != null) {
                    bookProgress = bp.first.toFloat() / bp.second.toFloat()
                }
            }
            if (bookProgress > 0f) {
                val progress = (bookProgress * 1000).toInt().coerceIn(0, 1000)
                views.setProgressBar(R.id.widget_progress, 1000, progress, false)
            } else if (duration > 0) {
                val progress = ((currentPosition * 1000) / duration).toInt().coerceIn(0, 1000)
                views.setProgressBar(R.id.widget_progress, 1000, progress, false)
            } else {
                views.setProgressBar(R.id.widget_progress, 1000, 0, false)
            }
            applyDominantColorTint(views, context)
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
     * Checks if audio playback is currently active.
     * Called on main thread (from onReceive).
     */
    private fun isPlaybackActive(context: Context): Boolean =
        try {
            @Suppress("DEPRECATION")
            val service = AudioPlayerService.getInstance()
            service != null && service.isFullyInitialized() && service.isPlaying
        } catch (e: Exception) {
            false
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
    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun setupClickIntents(
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

    /**
     * Loads cover image via Coil3 (unified image pipeline).
     * Falls back to URI-based loading for local content, then to placeholder.
     */
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

        // Check if URI scheme is supported by our image pipeline
        if (!WidgetCoverLoadPolicy.shouldLoadWithCoil(artworkUri)) {
            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            return
        }

        // Load cover asynchronously via Coil3
        scope.launch(Dispatchers.IO) {
            try {
                val loader = SingletonImageLoader.get(context.applicationContext)
                val request =
                    ImageRequest
                        .Builder(context.applicationContext)
                        .data(artworkUri)
                        .size(WidgetCoverLoadPolicy.COVER_SIZE_PX, WidgetCoverLoadPolicy.COVER_SIZE_PX)
                        .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    extractAndStoreDominantColor(context, bitmap)
                    val updatedViews =
                        RemoteViews(
                            context.packageName,
                            getLayoutForSize(
                                getWidgetSize(context, AppWidgetManager.getInstance(context), appWidgetId),
                            ),
                        )
                    updatedViews.setImageViewBitmap(R.id.widget_cover, bitmap)
                    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, updatedViews)
                } else {
                    applyCoverFallback(context, views, appWidgetId, artworkUri)
                }
            } catch (e: Exception) {
                LogUtils.w("PlayerWidget", "Failed to load cover via Coil, trying fallback", e)
                applyCoverFallback(context, views, appWidgetId, artworkUri)
            }
        }
    }

    /**
     * Applies fallback cover loading strategy when Coil fails.
     * Tries URI-based loading for local content, then placeholder.
     */
    private fun extractAndStoreDominantColor(
        context: Context,
        bitmap: Bitmap,
    ) {
        try {
            val palette = Palette.from(bitmap).generate()
            val color =
                palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: return
            storeDominantColor(context, color)
        } catch (_: Exception) {
            // Palette extraction is best-effort
        }
    }

    // ponytail: single cached DB instance — avoids rebuilding Room DB every 30s
    private suspend fun getBookProgress(
        context: Context,
        bookId: String,
    ): Pair<Long, Long>? {
        return try {
            val db = getDatabase(context)
            val chapters = db.chaptersDao().getChaptersByBookId(bookId)
            if (chapters.isEmpty()) return null
            val totalPosition = chapters.sumOf { if (it.isCompleted) it.duration else it.position.coerceAtMost(it.duration) }
            val totalDuration = chapters.sumOf { it.duration }
            if (totalDuration <= 0L) return null
            Pair(totalPosition, totalDuration)
        } catch (e: Exception) {
            LogUtils.w("PlayerWidget", "Failed to query book progress", e)
            null
        }
    }

    private fun applyDominantColorTint(
        views: RemoteViews,
        context: Context,
    ) {
        val color = getDominantColor(context)
        if (color != 0) {
            views.setColorStateList(
                R.id.widget_progress,
                "setProgressTintList",
                android.content.res.ColorStateList
                    .valueOf(color),
            )
        }
    }

    private fun applyCoverFallback(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        artworkUri: Uri,
    ) {
        if (WidgetCoverLoadPolicy.shouldUseUriFallback(artworkUri)) {
            try {
                views.setImageViewUri(R.id.widget_cover, artworkUri)
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
            } catch (e2: Exception) {
                LogUtils.w("PlayerWidget", "Failed to set cover URI fallback", e2)
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            }
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
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

        private const val ALARM_REQUEST_CODE = 0x1001
        private const val UPDATE_INTERVAL_MS = 30000L
        private const val PREFS_NAME = "widget_player_prefs"
        private const val PREFS_KEY_DOMINANT_COLOR = "dominant_color"
        private const val PREFS_KEY_BOOK_ID = "last_book_id"
        private const val DATABASE_NAME = "jabook-database"

        private var cachedDatabase: JabookDatabase? = null

        private fun getDatabase(context: Context): JabookDatabase {
            cachedDatabase?.let { return it }
            val db =
                androidx.room.Room
                    .databaseBuilder(
                        context.applicationContext,
                        JabookDatabase::class.java,
                        DATABASE_NAME,
                    ).build()
            cachedDatabase = db
            return db
        }

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

        /**
         * Schedules periodic widget updates via AlarmManager (30s interval).
         */
        public fun schedulePeriodicUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent =
                Intent(context, PlayerWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
                UPDATE_INTERVAL_MS,
                pendingIntent,
            )
        }

        /**
         * Cancels periodic widget updates.
         */
        public fun cancelPeriodicUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent =
                Intent(context, PlayerWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            alarmManager.cancel(pendingIntent)
        }

        /**
         * Stores the dominant color extracted from cover art.
         */
        public fun storeDominantColor(
            context: Context,
            color: Int,
        ) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREFS_KEY_DOMINANT_COLOR, color)
                .apply()
        }

        /**
         * Returns the stored dominant color, or 0 if not set.
         */
        public fun getDominantColor(context: Context): Int =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(PREFS_KEY_DOMINANT_COLOR, 0)

        private fun storeLastBookId(
            context: Context,
            bookId: String,
        ) {
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREFS_KEY_BOOK_ID, bookId)
                .apply()
        }

        private fun getLastBookId(context: Context): String? =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREFS_KEY_BOOK_ID, null)
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
