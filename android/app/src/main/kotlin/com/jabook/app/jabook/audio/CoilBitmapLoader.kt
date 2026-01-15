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

@file:Suppress("DEPRECATION") // BitmapLoader is deprecated in Media3 but still required

package com.jabook.app.jabook.audio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.BitmapLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Custom BitmapLoader using Coil3 for optimized artwork loading.
 *
 * This implementation uses Coil3 for better caching, memory management, and async loading.
 * Replaces GlideBitmapLoader to align with the project's image loading strategy.
 *
 * Features:
 * - Disk and memory caching via Coil
 * - Efficient bitmap decoding and resizing
 * - Support for remote URLs and local files
 * - Proper error handling with fallbacks
 *
 * @param context Application context for Coil initialization
 */
@OptIn(UnstableApi::class)
@Suppress("DEPRECATION") // BitmapLoader is deprecated in Media3 but still required
public class CoilBitmapLoader(
    private val context: Context,
) : BitmapLoader {
    // CoroutineScope for Coil operations to avoid blocking UI thread
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Maximum artwork size for notifications (recommended by Android)
    private val maxArtworkWidth = 512
    private val maxArtworkHeight = 512

    /**
     * Loads bitmap from URI using Coil.
     * Supports file://, content://, and http(s):// schemes.
     */
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        scope.launch {
            try {
                android.util.Log.d("CoilBitmapLoader", "Loading bitmap from URI: $uri")

                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(maxArtworkWidth, maxArtworkHeight)
                    .build()

                val result = loader.execute(request)

                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    android.util.Log.i(
                        "CoilBitmapLoader",
                        "Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}",
                    )
                    future.set(bitmap)
                } else {
                    android.util.Log.w("CoilBitmapLoader", "Coil failed to load bitmap for URI: $uri")
                    future.setException(Exception("Failed to load bitmap from URI: $uri"))
                }
            } catch (e: Exception) {
                android.util.Log.e("CoilBitmapLoader", "Error loading bitmap from URI: $uri", e)
                future.setException(e)
            }
        }

        return future
    }

    /**
     * Checks if Coil supports the given MIME type.
     * Coil supports most common image formats.
     */
    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/") || mimeType == "application/octet-stream"
    }

    /**
     * Decodes bitmap from raw byte data using Coil.
     * This is used for embedded artwork in audio files.
     */
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        scope.launch {
            try {
                android.util.Log.d("CoilBitmapLoader", "Decoding bitmap from byte array: ${data.size} bytes")

                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .size(maxArtworkWidth, maxArtworkHeight)
                    .build()

                val result = loader.execute(request)

                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    android.util.Log.i(
                        "CoilBitmapLoader",
                        "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}",
                    )
                    future.set(bitmap)
                } else {
                    android.util.Log.w("CoilBitmapLoader", "Coil failed to decode bitmap from byte array")
                    future.setException(Exception("Failed to decode bitmap"))
                }
            } catch (e: Exception) {
                android.util.Log.e("CoilBitmapLoader", "Error decoding bitmap from byte array", e)
                future.setException(e)
            }
        }

        return future
    }

    /**
     * Loads bitmap from MediaMetadata.
     * This method is deprecated but still required by Media3's BitmapLoader interface.
     */
    override fun loadBitmapFromMetadata(metadata: androidx.media3.common.MediaMetadata): ListenableFuture<Bitmap>? {
        // Try artworkUri first (better performance)
        val artworkUri = metadata.artworkUri
        if (artworkUri != null) {
            return loadBitmap(artworkUri)
        }

        // Fallback to artworkData
        val artworkData = metadata.artworkData
        if (artworkData != null && artworkData.isNotEmpty()) {
            return decodeBitmap(artworkData)
        }

        return Futures.immediateFailedFuture(Exception("No artwork in metadata"))
    }
}
