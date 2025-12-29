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

package com.jabook.app.jabook.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
 * - Current book title (or "No book playing")
 * - Play/Pause button
 * - Next/Previous buttons
 *
 * Clicking the widget opens the player screen.
 */
class PlayerWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PlayerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    /**
     * Updates a single widget instance.
     * Uses MediaSession to get player state, which is more reliable than singleton instance.
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        scope.launch(Dispatchers.IO) {
            var controller: MediaController? = null
            var controllerFuture: ListenableFuture<MediaController>? = null
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_player)

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

                    // Wait for controller with timeout
                    controller = controllerFuture?.get(1, TimeUnit.SECONDS)

                    if (controller != null) {
                        // Get player state from MediaController
                        val isPlaying = controller.isPlaying
                        val currentMediaItem = controller.currentMediaItem
                        val mediaMetadata = currentMediaItem?.mediaMetadata

                        // Get book information from metadata
                        var bookTitle =
                            mediaMetadata?.albumTitle?.toString()
                                ?: mediaMetadata?.title?.toString()
                                ?: context.getString(R.string.no_book_playing)
                        val bookAuthor = mediaMetadata?.artist?.toString()

                        // Update book title and author
                        views.setTextViewText(R.id.widget_book_title, bookTitle)
                        if (!bookAuthor.isNullOrBlank()) {
                            views.setTextViewText(R.id.widget_book_author, bookAuthor)
                            views.setViewVisibility(R.id.widget_book_author, android.view.View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
                        }

                        // Update play/pause button
                        val playPauseIcon =
                            if (isPlaying) {
                                R.drawable.ic_pause
                            } else {
                                R.drawable.ic_play
                            }
                        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

                        // Get book ID from metadata or service
                        val currentBookId =
                            mediaMetadata?.extras?.getString("bookId")
                                ?: AudioPlayerService.getInstance()?.currentGroupPath

                        // Set up click intents
                        setupClickIntents(context, views, currentBookId)

                        android.util.Log.d("PlayerWidget", "Widget updated via MediaController: book=$bookTitle, playing=$isPlaying")
                    } else {
                        // Fallback to service instance if MediaController is not available
                        updateWidgetFromService(context, views)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerWidget", "Failed to get MediaController, falling back to service", e)
                    // Fallback to service instance
                    updateWidgetFromService(context, views)
                } finally {
                    // Release MediaController
                    controllerFuture?.let {
                        MediaController.releaseFuture(it)
                    }
                }

                // Update widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                android.util.Log.e("PlayerWidget", "Failed to update widget", e)
                // Show default state on error
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_player)
                    views.setTextViewText(R.id.widget_book_title, context.getString(R.string.no_book_playing))
                    views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
                    views.setImageViewResource(R.id.widget_play_pause, R.drawable.ic_play)
                    setupClickIntents(context, views, null)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e2: Exception) {
                    android.util.Log.e("PlayerWidget", "Failed to set default widget state", e2)
                }
            }
        }
    }

    /**
     * Fallback method to update widget from service instance.
     */
    private fun updateWidgetFromService(
        context: Context,
        views: RemoteViews,
    ) {
        val service = AudioPlayerService.getInstance()
        if (service != null) {
            // Get player state
            val playerState = service.getPlayerState()
            val isPlaying = playerState["isPlaying"] as? Boolean ?: false
            val currentBookId = service.currentGroupPath

            // Get book information if available
            var bookTitle = context.getString(R.string.no_book_playing)
            var bookAuthor: String? = null

            if (currentBookId != null) {
                // Try to get book info from metadata
                try {
                    val mediaInfo = service.getCurrentMediaItemInfo()
                    bookTitle = mediaInfo["title"] as? String
                        ?: mediaInfo["albumTitle"] as? String
                        ?: currentBookId.substringAfterLast("/").takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.no_book_playing)
                    bookAuthor = mediaInfo["artist"] as? String
                } catch (e: Exception) {
                    android.util.Log.w("PlayerWidget", "Failed to get book info from service", e)
                }
            }

            // Update book title and author
            views.setTextViewText(R.id.widget_book_title, bookTitle)
            if (!bookAuthor.isNullOrBlank()) {
                views.setTextViewText(R.id.widget_book_author, bookAuthor)
                views.setViewVisibility(R.id.widget_book_author, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
            }

            // Update play/pause button
            val playPauseIcon =
                if (isPlaying) {
                    R.drawable.ic_pause
                } else {
                    R.drawable.ic_play
                }
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

            // Set up click intents
            setupClickIntents(context, views, currentBookId)

            android.util.Log.d("PlayerWidget", "Widget updated via service: book=$bookTitle, playing=$isPlaying")
        } else {
            // Service not available - show default state
            views.setTextViewText(R.id.widget_book_title, context.getString(R.string.no_book_playing))
            views.setViewVisibility(R.id.widget_book_author, android.view.View.GONE)
            views.setImageViewResource(R.id.widget_play_pause, R.drawable.ic_play)

            // Set up click intents (will start service)
            setupClickIntents(context, views, null)
        }
    }

    /**
     * Sets up click intents for widget buttons.
     */
    private fun setupClickIntents(
        context: Context,
        views: RemoteViews,
        currentBookId: String?,
    ) {
        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        // Play/Pause button
        val playPauseIntent =
            Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            PendingIntent.getService(context, 0, playPauseIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
        )

        // Next button
        val nextIntent =
            Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_NEXT
            }
        views.setOnClickPendingIntent(
            R.id.widget_next,
            PendingIntent.getService(context, 1, nextIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
        )

        // Previous button
        val previousIntent =
            Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_PREVIOUS
            }
        views.setOnClickPendingIntent(
            R.id.widget_previous,
            PendingIntent.getService(context, 2, previousIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
        )

        // Widget click - open player screen
        val openPlayerIntent =
            Intent(context, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = android.net.Uri.parse("jabook://player${if (currentBookId != null) "?bookId=$currentBookId" else ""}")
            }
        views.setOnClickPendingIntent(
            R.id.widget_content,
            PendingIntent.getActivity(context, 3, openPlayerIntent, pendingIntentFlags or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.jabook.app.jabook.UPDATE_WIDGET"
        const val ACTION_PLAY_PAUSE = "com.jabook.app.jabook.WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "com.jabook.app.jabook.WIDGET_NEXT"
        const val ACTION_PREVIOUS = "com.jabook.app.jabook.WIDGET_PREVIOUS"

        /**
         * Requests widget update from anywhere in the app.
         */
        fun requestUpdate(context: Context) {
            val intent =
                Intent(context, PlayerWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
            context.sendBroadcast(intent)
        }
    }
}
