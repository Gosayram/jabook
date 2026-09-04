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

package com.jabook.app.jabook.compose.designsystem.component

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder

/**
 * Universal component for displaying remote images with proper error handling.
 *
 * Based on Flow project analysis - provides consistent image loading
 * with placeholder, error states, and retry logic.
 *
 * Features:
 * - Automatic placeholder during loading
 * - Optional loading indicator overlay ([showLoadingIndicator])
 * - Error state with color
 * - Fallback for empty/null URLs
 * - Optional rounded corners (applied via [Modifier.clip], keeps hardware bitmaps)
 * - Crossfade animation
 * - Hardware bitmap support for better performance
 *
 * Usage:
 * ```kotlin
 * RemoteImage(
 *     src = "https://example.com/image.jpg",
 *     contentDescription = "Book cover",
 *     modifier = Modifier.size(200.dp),
 *     contentScale = ContentScale.Crop,
 * )
 * ```
 */
@Composable
public fun RemoteImage(
    src: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    cornerRadius: Float? = null,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    errorColor: Color = MaterialTheme.colorScheme.error,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    /** When true, shows a small centered progress indicator while loading. */
    showLoadingIndicator: Boolean = true,
) {
    val context = LocalContext.current
    var isLoading by remember(src) { mutableStateOf(true) }

    // Build ImageRequest with proper error handling; remembered to avoid rebuilds on recomposition
    val imageRequest =
        remember(src, placeholderColor, errorColor, fallbackColor, context) {
            ImageRequest
                .Builder(context)
                .data(src)
                .crossfade(true)
                .allowHardware(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .placeholder(ColorDrawable(placeholderColor.toArgb()).asImage())
                .error(ColorDrawable(errorColor.toArgb()).asImage())
                .fallback(ColorDrawable(fallbackColor.toArgb()).asImage())
                .build()
        }

    // AsyncImage — avoids subcomposition overhead of SubcomposeAsyncImage (Coil docs warn
    // against SubcomposeAsyncImage in scrollable lists). Placeholder/error use ColorDrawable
    // images set on the ImageRequest above. Rounded corners clip the layout (no
    // RoundedCornersTransformation, which would force software bitmaps).
    Box(
        modifier =
            modifier.then(
                if (cornerRadius != null && cornerRadius > 0f) {
                    Modifier.clip(RoundedCornerShape(cornerRadius.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
            contentScale = contentScale,
            onState = { state -> isLoading = state is AsyncImagePainter.State.Loading },
        )
        if (showLoadingIndicator && isLoading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
