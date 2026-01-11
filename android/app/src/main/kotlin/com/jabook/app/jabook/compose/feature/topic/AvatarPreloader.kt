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

package com.jabook.app.jabook.compose.feature.topic

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.request.ImageRequest
import com.jabook.app.jabook.compose.domain.model.RutrackerComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Preloads avatar images for comments to ensure they are available offline.
 *
 * This helps fix the issue where avatars wouldn't load if the network
 * connection was lost after loading the topic details but before scrolling
 * to the comments.
 */
public class AvatarPreloader
    @Inject
    constructor() {
        public companion object {
            private const val TAG = "AvatarPreloader"
        }

        /**
         * Preloads avatars for the given list of comments.
         *
         * @param context Android context
         * @param comments List of comments to preload avatars for
         */
        suspend fun preloadAvatars(
            context: Context,
            comments: List<RutrackerComment>,
        ) = withContext(Dispatchers.IO) {
            if (comments.isEmpty()) return@withContext

            public val commentsWithAvatars = comments.filter { !it.avatarUrl.isNullOrBlank() }
            if (commentsWithAvatars.isEmpty()) return@withContext

            Log.d(TAG, "Starting preload for ${commentsWithAvatars.size} avatars")

            public val imageLoader = SingletonImageLoader.get(context)
            public var successCount = 0

            commentsWithAvatars.forEach { comment ->
                try {
                    public val url = comment.avatarUrl ?: return@forEach

                    public val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            // Preload with same settings as RemoteImage to ensure cache hit
                            .placeholder(ColorDrawable(Color.Gray.toArgb()).asImage())
                            .build()

                    imageLoader.enqueue(request)
                    successCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to preload avatar for ${comment.author}", e)
                }
            }

            Log.d(TAG, "Enqueued $successCount avatar preload requests")
        }
    }
