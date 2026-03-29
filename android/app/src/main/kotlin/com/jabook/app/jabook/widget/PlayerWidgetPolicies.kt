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

import android.net.Uri
import androidx.media3.common.Player
import com.bumptech.glide.load.engine.DiskCacheStrategy

internal object WidgetControllerSnapshotPolicy {
    internal fun shouldFallbackToService(
        hasCurrentMediaItem: Boolean,
        playbackState: Int,
        isPlaying: Boolean,
    ): Boolean {
        if (hasCurrentMediaItem) {
            return false
        }
        return isPlaying || playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
    }
}

internal object WidgetActionRoutingPolicy {
    internal const val ROUTE_OPEN_PLAYER: String = "com.jabook.app.jabook.WIDGET_OPEN_PLAYER"
    internal const val ROUTE_OPEN_PLAYER_PROGRESS: String = "com.jabook.app.jabook.WIDGET_OPEN_PLAYER_PROGRESS"

    private fun actionSlot(action: String): Int =
        when (action) {
            PlayerWidgetProvider.ACTION_PLAY_PAUSE -> 1
            PlayerWidgetProvider.ACTION_NEXT -> 2
            PlayerWidgetProvider.ACTION_PREVIOUS -> 3
            PlayerWidgetProvider.ACTION_REPEAT -> 4
            PlayerWidgetProvider.ACTION_SPEED -> 5
            PlayerWidgetProvider.ACTION_TIMER -> 6
            ROUTE_OPEN_PLAYER -> 7
            ROUTE_OPEN_PLAYER_PROGRESS -> 8
            else -> 99
        }

    internal fun requestCodeForAction(
        appWidgetId: Int,
        action: String,
    ): Int {
        val safeWidgetId = appWidgetId.coerceAtLeast(0)
        return safeWidgetId * 100 + actionSlot(action)
    }
}

internal object WidgetCoverLoadPolicy {
    internal const val COVER_SIZE_PX: Int = 512
    internal const val COVER_TIMEOUT_MS: Int = 1500
    internal val DISK_CACHE_STRATEGY: DiskCacheStrategy = DiskCacheStrategy.AUTOMATIC

    private val GLIDE_SUPPORTED_SCHEMES: Set<String> =
        setOf("http", "https", "content", "file", "android.resource")
    private val URI_FALLBACK_SCHEMES: Set<String> =
        setOf("content", "file", "android.resource")

    internal fun shouldLoadWithGlide(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return true
        return scheme in GLIDE_SUPPORTED_SCHEMES
    }

    internal fun shouldUseUriFallback(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return true
        return scheme in URI_FALLBACK_SCHEMES
    }
}

internal object WidgetDeepLinkPolicy {
    private const val PLAYER_ROUTE: String = "jabook://player"
    internal const val QUERY_WIDGET_ID: String = "widgetId"
    internal const val QUERY_BOOK_ID: String = "bookId"

    internal fun buildPlayerDeepLink(
        currentBookId: String?,
        appWidgetId: Int,
    ): Uri {
        val builder =
            Uri
                .parse(PLAYER_ROUTE)
                .buildUpon()
                .appendQueryParameter(QUERY_WIDGET_ID, appWidgetId.coerceAtLeast(0).toString())

        if (!currentBookId.isNullOrBlank()) {
            builder.appendQueryParameter(QUERY_BOOK_ID, currentBookId)
        }

        return builder.build()
    }
}

internal enum class WidgetUpdateSource {
    BROADCAST,
    CONTROLLER,
    SERVICE_FALLBACK,
    DEFAULT_STATE,
}

internal enum class WidgetFallbackReason {
    CONTROLLER_UNAVAILABLE,
    CONTROLLER_EXCEPTION,
    CONTROLLER_STALE_SNAPSHOT,
    SERVICE_UNAVAILABLE,
    UPDATE_EXCEPTION,
}

internal object WidgetObservabilityPolicy {
    internal const val UNKNOWN_WIDGET_ID: Int = -1

    internal fun sanitizeWidgetId(widgetId: Int?): Int =
        if (widgetId != null && widgetId >= 0) {
            widgetId
        } else {
            UNKNOWN_WIDGET_ID
        }

    internal fun providerMessage(
        event: String,
        widgetId: Int?,
        source: WidgetUpdateSource? = null,
        reason: WidgetFallbackReason? = null,
        detail: String? = null,
    ): String =
        buildString {
            append("widget_event=").append(event)
            append(" widgetId=").append(sanitizeWidgetId(widgetId))
            source?.let { append(" source=").append(it.name) }
            reason?.let { append(" reason=").append(it.name) }
            if (!detail.isNullOrBlank()) {
                append(" detail=").append(detail)
            }
        }

    internal fun serviceMessage(
        event: String,
        action: String,
        widgetId: Int?,
        deduplicated: Boolean? = null,
    ): String =
        buildString {
            append("widget_service_event=").append(event)
            append(" action=").append(action)
            append(" widgetId=").append(sanitizeWidgetId(widgetId))
            deduplicated?.let { append(" deduplicated=").append(it) }
        }
}
