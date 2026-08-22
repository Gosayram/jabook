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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation

/**
 * Universal component for displaying remote images with proper error handling.
 *
 * Based on Flow project analysis - provides consistent image loading
 * with placeholder, error states, and retry logic.
 *
 * Features:
 * - Automatic placeholder during loading
 * - Error state with icon
 * - Fallback for empty/null URLs
 * - Optional rounded corners
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
    /** When true, shows an animated shimmer during image load. When false, shows the placeholder color. */
    showLoadingIndicator: Boolean = true,
) {
    val context = LocalContext.current

    // Build ImageRequest with proper error handling
    val imageRequest =
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
            .apply {
                // Add rounded corners transformation if specified
                if (cornerRadius != null && cornerRadius > 0) {
                    val density = context.resources.displayMetrics.density
                    val radiusPx = cornerRadius * density
                    transformations(RoundedCornersTransformation(radiusPx))
                }
            }.build()

    // AsyncImage — avoids subcomposition overhead of SubcomposeAsyncImage (Coil docs warn
    // against SubcomposeAsyncImage in scrollable lists). Placeholder/error use ColorDrawable
    // images set on the ImageRequest above.
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
