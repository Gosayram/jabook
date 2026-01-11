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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors

/**
 * Custom BitmapLoader using Glide for optimized artwork loading.
 *
 * Inspired by Easybook's artwork loading approach, this implementation
 * uses Glide for better caching, memory management, and async loading.
 *
 * Features:
 * - Disk and memory caching
 * - Efficient bitmap decoding and resizing
 * - Support for remote URLs (book cover APIs)
 * - Proper error handling with fallbacks
 *
 * @param context Application context for Glide initialization
 */
@OptIn(UnstableApi::class)
@Suppress("DEPRECATION") // BitmapLoader is deprecated in Media3 but still required
public class GlideBitmapLoader(
    private val context: Context,
) : BitmapLoader {
    // Executor for Glide operations to avoid blocking UI thread
    private val executorService = Executors.newFixedThreadPool(2)

    // Maximum artwork size for notifications (recommended by Android)
    // Larger images consume more memory and may be rejected by system
    private val maxArtworkWidth = 512
    private val maxArtworkHeight = 512

    /**
     * Loads bitmap from URI using Glide.
     * Supports file://, content://, and http(s):// schemes.
     */
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        executorService.execute {
            try {
                android.util.Log.d("GlideBitmapLoader", "Loading bitmap from URI: $uri")

                val requestOptions =
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE) // Cache processed images
                        .override(maxArtworkWidth, maxArtworkHeight) // Resize for optimal memory usage
                        .centerCrop() // Maintain aspect ratio with cropping
                        .dontTransform() // Don't apply transformations that might break aspect ratio

                val bitmap =
                    Glide
                        .with(context)
                        .asBitmap()
                        .load(uri)
                        .apply(requestOptions)
                        .submit()
                        .get() // Blocking call, but running on executor thread

                if (bitmap != null) {
                    android.util.Log.i(
                        "GlideBitmapLoader",
                        "Successfully loaded bitmap: ${bitmap.width}x${bitmap.height}",
                    )
                    future.set(bitmap)
                } else {
                    android.util.Log.w("GlideBitmapLoader", "Glide returned null bitmap for URI: $uri")
                    future.setException(Exception("Failed to load bitmap from URI: $uri"))
                }
            } catch (e: Exception) {
                android.util.Log.e("GlideBitmapLoader", "Error loading bitmap from URI: $uri", e)
                future.setException(e)
            }
        }

        return future
    }

    /**
     * Checks if Glide supports the given MIME type.
     * Glide supports most common image formats.
     */
    override fun supportsMimeType(mimeType: String): Boolean {
        val supported =
            mimeType.startsWith("image/") ||
                mimeType == "application/octet-stream" // Generic binary

        android.util.Log.d("GlideBitmapLoader", "MIME type $mimeType supported: $supported")
        return supported
    }

    /**
     * Decodes bitmap from raw byte data using Glide.
     * This is used for embedded artwork in audio files.
     */
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        executorService.execute {
            try {
                android.util.Log.d("GlideBitmapLoader", "Decoding bitmap from byte array: ${data.size} bytes")

                val requestOptions =
                    RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .override(maxArtworkWidth, maxArtworkHeight)
                        .centerCrop()

                val bitmap =
                    Glide
                        .with(context)
                        .asBitmap()
                        .load(data)
                        .apply(requestOptions)
                        .submit()
                        .get()

                if (bitmap != null) {
                    android.util.Log.i(
                        "GlideBitmapLoader",
                        "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}",
                    )
                    future.set(bitmap)
                } else {
                    android.util.Log.w("GlideBitmapLoader", "Glide returned null bitmap for byte array")
                    future.setException(Exception("Failed to decode bitmap from ${data.size} bytes"))
                }
            } catch (e: Exception) {
                android.util.Log.e("GlideBitmapLoader", "Error decoding bitmap from byte array", e)
                future.setException(e)
            }
        }

        return future
    }

    /**
     * Loads bitmap from MediaMetadata.
     * This method is deprecated but still required by Media3's BitmapLoader interface.
     *
     * Note: Media3 recommends using artworkData and artworkUri from MediaMetadata
     * instead of this method for better performance.
     */
    override fun loadBitmapFromMetadata(metadata: androidx.media3.common.MediaMetadata): ListenableFuture<Bitmap>? {
        android.util.Log.d("GlideBitmapLoader", "loadBitmapFromMetadata called (deprecated method)")

        // Try artworkUri first (better performance)
        val artworkUri = metadata.artworkUri
        if (artworkUri != null) {
            android.util.Log.d("GlideBitmapLoader", "Loading bitmap from metadata artworkUri: $artworkUri")
            return loadBitmap(artworkUri)
        }

        // Fallback to artworkData
        val artworkData = metadata.artworkData
        if (artworkData != null && artworkData.isNotEmpty()) {
            android.util.Log.d(
                "GlideBitmapLoader",
                "Loading bitmap from metadata artworkData: ${artworkData.size} bytes",
            )
            return decodeBitmap(artworkData)
        }

        // No artwork available
        android.util.Log.d("GlideBitmapLoader", "No artwork available in metadata")
        return Futures.immediateFailedFuture(Exception("No artwork in metadata"))
    }

    /**
     * Clears Glide memory cache.
     * Should be called when memory is low.
     */
    public fun clearMemoryCache(...) {
        android.util.Log.d("GlideBitmapLoader", "Clearing Glide memory cache")
        Glide.get(context).clearMemory()
    }

    /**
     * Clears Glide disk cache.
     * Should be called on background thread.
     */
    public fun clearDiskCache(...) {
        executorService.execute {
            android.util.Log.d("GlideBitmapLoader", "Clearing Glide disk cache")
            Glide.get(context).clearDiskCache()
        }
    }
}
