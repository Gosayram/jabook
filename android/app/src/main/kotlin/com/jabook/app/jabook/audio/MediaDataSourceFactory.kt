// Copyright 2025 Jabook Contributors
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

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * DataSource factory for media playback with caching and network support.
 *
 * For local files, uses DefaultDataSource directly (no caching needed).
 * For network sources (http/https), uses CacheDataSource with OkHttpDataSource
 * and SimpleCache for offline playback and reduced network usage.
 *
 * Inspired by lissen-android implementation for efficient media loading.
 */
class MediaDataSourceFactory(
    private val context: Context,
    private val cache: Cache?,
) : DataSource.Factory {
    // OkHttp client for network requests (with proper timeouts and configuration)
    private val okHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // OkHttp DataSource factory for network sources
    private val okHttpFactory by lazy {
        OkHttpDataSource.Factory(okHttpClient)
    }

    // Default factory for local files
    private val defaultFactory by lazy {
        DefaultDataSource.Factory(context)
    }

    // Cache factory for network sources (combines OkHttp + Cache)
    private val cacheFactory by lazy {
        if (cache != null) {
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(
                    DefaultDataSource.Factory(context, okHttpFactory),
                ).setCacheWriteDataSinkFactory(
                    CacheDataSink
                        .Factory()
                        .setCache(cache)
                        .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
                ).setFlags(
                    CacheDataSource.FLAG_BLOCK_ON_CACHE or
                        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                )
        } else {
            null
        }
    }

    override fun createDataSource(): DataSource {
        // Default implementation - use default factory for local files
        // For network sources, use createDataSourceFactoryForUri() method
        return defaultFactory.createDataSource()
    }

    /**
     * Creates DataSource factory for a specific URI.
     *
     * Optimized factory selection based on URI scheme:
     * - http/https: Uses OkHttp + Cache for network sources
     * - file: Uses DefaultDataSource for local files
     *
     * @param uri Media URI (file://, http://, https://)
     * @return DataSource factory optimized for the URI type
     */
    fun createDataSourceFactoryForUri(uri: Uri): DataSource.Factory {
        val isNetworkUri = uri.scheme == "http" || uri.scheme == "https"
        val isLocalFile = uri.scheme == "file" || uri.scheme == null

        return when {
            isNetworkUri && cacheFactory != null -> {
                // Use cache + OkHttp for network sources
                // This provides offline playback and reduced network usage
                cacheFactory!!
            }
            isNetworkUri && cacheFactory == null -> {
                // Network source but no cache available - use OkHttp directly
                DefaultDataSource.Factory(context, okHttpFactory)
            }
            isLocalFile -> {
                // Local files don't need caching or network
                defaultFactory
            }
            else -> {
                // Fallback to default
                defaultFactory
            }
        }
    }
}
