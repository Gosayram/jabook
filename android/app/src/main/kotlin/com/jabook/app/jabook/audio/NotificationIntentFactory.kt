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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.TaskStackBuilder
import com.jabook.app.jabook.compose.ComposeMainActivity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun playerNotificationRoute(bookId: String?): String? =
    bookId
        ?.takeIf(String::isNotBlank)
        ?.let { "jabook://player/${URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20")}" }

/**
 * Creates notification content intents for the audio player service.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates PendingIntent creation logic in one place.
 *
 * Cold start is safe: tapping the notification may launch the activity before
 * [com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController] finishes
 * connecting, but commands queue client-side (executeOrQueue) and the service accepts
 * controllers early in onGetSession, so nothing is lost or blocked.
 */
internal class NotificationIntentFactory(
    private val context: Context,
    private val currentBookId: () -> String?,
) {
    /**
     * Returns the single top activity PendingIntent.
     * Used when the app task is active and an activity is in the fore or background.
     * Tapping the notification triggers a single top activity with deep link to PlayerScreen.
     */
    fun getSingleTopActivity(): PendingIntent? =
        PendingIntent.getActivity(
            context,
            0,
            playerActivityIntent(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Returns a back-stacked session activity PendingIntent.
     * Used when the service runs standalone as a foreground service (app dismissed from recents).
     * Creates proper back stack so pressing back doesn't land on home screen.
     */
    fun getBackStackedActivity(): PendingIntent? =
        TaskStackBuilder.create(context).run {
            addNextIntent(
                playerActivityIntent(),
            )
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun playerActivityIntent(flags: Int = 0): Intent =
        Intent(context, ComposeMainActivity::class.java).apply {
            this.flags = flags
            data = playerNotificationRoute(currentBookId())?.let(android.net.Uri::parse)
        }
}
