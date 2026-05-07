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
import android.os.Build
import androidx.core.app.TaskStackBuilder
import com.jabook.app.jabook.compose.ComposeMainActivity

/**
 * Creates notification content intents for the audio player service.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates PendingIntent creation logic in one place.
 */
internal class NotificationIntentFactory(
    private val context: Context,
) {
    /**
     * Returns the single top activity PendingIntent.
     * Used when the app task is active and an activity is in the fore or background.
     * Tapping the notification triggers a single top activity with deep link to PlayerScreen.
     */
    fun getSingleTopActivity(): PendingIntent? {
        val immutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = android.net.Uri.parse("jabook://player")
            },
            immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Returns a back-stacked session activity PendingIntent.
     * Used when the service runs standalone as a foreground service (app dismissed from recents).
     * Creates proper back stack so pressing back doesn't land on home screen.
     */
    fun getBackStackedActivity(): PendingIntent? {
        val immutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return TaskStackBuilder.create(context).run {
            addNextIntent(
                Intent(context, ComposeMainActivity::class.java),
            )
            getPendingIntent(0, immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
