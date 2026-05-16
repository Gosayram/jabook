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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
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

    // Use SubcomposeAsyncImage for custom loading/error states
    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            if (showLoadingIndicator) {
                ShimmerLoadingBox(modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        },
        error = {
            // Show error icon
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
        success = { state ->
            // Show image when loaded successfully
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
        },
    )
}

/**
 * Animated shimmer box used as the image loading placeholder.
 *
 * Sweeps a gradient highlight from left to right using [rememberInfiniteTransition].
 * Colors are derived from the Material theme so it respects dark/light mode.
 */
@Composable
private fun ShimmerLoadingBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    Box(
        modifier = modifier.drawBehind {
            drawRect(
                Brush.linearGradient(
                    colors = listOf(baseColor, highlightColor, baseColor),
                    start = Offset(x = (progress - 0.5f) * 2f * size.width, y = 0f),
                    end = Offset(x = (progress + 0.5f) * 2f * size.width, y = size.height),
                ),
            )
        },
    )
}

/**
 * Simplified version of RemoteImage that uses AsyncImage directly.
 * Use this when you don't need custom loading/error states.
 *
 * @param src Image URL or path
 * @param contentDescription Content description for accessibility
 * @param modifier Modifier for styling
 * @param contentScale Content scale for image
 * @param cornerRadius Optional corner radius in dp
 * @param placeholderColor Color for placeholder
 * @param errorColor Color for error state
 * @param fallbackColor Color for fallback when no image available
 */
@Composable
public fun SimpleRemoteImage(
    src: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    cornerRadius: Float? = null,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    errorColor: Color = MaterialTheme.colorScheme.error,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val context = LocalContext.current

    val imageRequest =
        ImageRequest
            .Builder(context)
            .data(src)
            .crossfade(true)
            .allowHardware(true)
            .placeholder(ColorDrawable(placeholderColor.toArgb()).asImage())
            .error(ColorDrawable(errorColor.toArgb()).asImage())
            .fallback(ColorDrawable(fallbackColor.toArgb()).asImage())
            .apply {
                if (cornerRadius != null && cornerRadius > 0) {
                    val density = context.resources.displayMetrics.density
                    val radiusPx = cornerRadius * density
                    transformations(RoundedCornersTransformation(radiusPx))
                }
            }.build()

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
