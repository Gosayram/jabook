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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.request.ImageRequest
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
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
    constructor(
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("AvatarPreloader")

        /**
         * Preloads avatars for the given list of comments.
         *
         * @param context Android context
         * @param comments List of comments to preload avatars for
         */
        public suspend fun preloadAvatars(
            context: Context,
            comments: List<RutrackerComment>,
        ): Unit =
            withContext(Dispatchers.IO) {
                if (comments.isEmpty()) return@withContext

                val commentsWithAvatars = comments.filter { !it.avatarUrl.isNullOrBlank() }
                if (commentsWithAvatars.isEmpty()) return@withContext

                logger.d { "Starting preload for ${commentsWithAvatars.size} avatars" }

                val imageLoader = SingletonImageLoader.get(context)
                var successCount: Int = 0
                commentsWithAvatars.forEach { comment ->
                    try {
                        val url = comment.avatarUrl ?: return@forEach

                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(url)
                                // Preload with same settings as RemoteImage to ensure cache hit
                                .placeholder(ColorDrawable(Color.Gray.toArgb()).asImage())
                                .build()

                        imageLoader.enqueue(request)
                        successCount++
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to preload avatar for ${comment.author}" }
                    }
                }

                logger.d { "Enqueued $successCount avatar preload requests" }
            }
    }
