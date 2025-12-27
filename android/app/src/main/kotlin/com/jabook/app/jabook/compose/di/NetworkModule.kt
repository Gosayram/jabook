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

package com.jabook.app.jabook.compose.di

import com.jabook.app.jabook.compose.data.network.AuthInterceptor
import com.jabook.app.jabook.compose.data.network.DynamicBaseUrlInterceptor
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for network dependencies.
 *
 * Provides Retrofit, OkHttp client, and API interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Provide JSON serializer for Retrofit.
     */
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    /**
     * Provide logging interceptor.
     *
     * Note: Level.BODY logs request/response bodies which may contain sensitive data.
     * For production, consider using Level.BASIC or Level.HEADERS instead.
     * Level.BODY is useful for debugging network issues.
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Use BASIC or HEADERS for production
        }

    /**
     * Provide MirrorManager.
     *
     * Uses a lightweight OkHttpClient for health checks to avoid circular dependency.
     */
    @Provides
    @Singleton
    fun provideMirrorManager(
        settingsRepository: SettingsRepository,
        cookieJar: PersistentCookieJar,
    ): MirrorManager {
        // Lightweight OkHttpClient for health checks only
        val healthCheckClient =
            OkHttpClient
                .Builder()
                .cookieJar(cookieJar)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        return MirrorManager(settingsRepository, healthCheckClient)
    }

    /**
     * Provide DynamicBaseUrlInterceptor.
     */
    @Provides
    @Singleton
    fun provideDynamicBaseUrlInterceptor(mirrorManager: MirrorManager): DynamicBaseUrlInterceptor = DynamicBaseUrlInterceptor(mirrorManager)

    /**
     * Provide OkHttp client with cookie persistence, auto re-auth, dynamic base URL, proper headers, and logging.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar,
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        rutrackerHeadersInterceptor: com.jabook.app.jabook.compose.data.network.RutrackerHeadersInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            // Interceptor order matters! They are called in order:
            // 1. BrotliInterceptor - MUST be first to add Accept-Encoding header (only if not already set)
            // 2. RutrackerHeadersInterceptor - Adds User-Agent, Accept, Accept-Language (NO Accept-Encoding!)
            // 3. AuthInterceptor - Handles session expiry and re-authentication
            // 4. DynamicBaseUrlInterceptor - Switches between RuTracker mirrors
            // 5. LoggingInterceptor - Last to log final request/response
            .addInterceptor(BrotliInterceptor) // Automatic Brotli decompression (MUST be first to add Accept-Encoding!)
            .addInterceptor(rutrackerHeadersInterceptor) // Add browser-like headers (NO Accept-Encoding - BrotliInterceptor handles it)
            .addInterceptor(authInterceptor) // Auto re-authentication
            .addInterceptor(dynamicBaseUrlInterceptor) // Dynamic base URL for mirrors
            .addInterceptor(loggingInterceptor) // Logging last for complete request/response
            // Timeouts
            .callTimeout(60, TimeUnit.SECONDS) // Total time for entire call (including retries/redirects)
            .connectTimeout(30, TimeUnit.SECONDS) // Time to establish connection
            .readTimeout(30, TimeUnit.SECONDS) // Time to read response
            .writeTimeout(30, TimeUnit.SECONDS) // Time to write request
            // OkHttp defaults (explicit for clarity):
            // - retryOnConnectionFailure = true (retry on connection failures)
            // - followRedirects = true (follow HTTP redirects)
            // - followSslRedirects = true (follow redirects between HTTP/HTTPS)
            .build()

    /**
     * Provide Retrofit instance.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()

        return Retrofit
            .Builder()
            .baseUrl("https://rutracker.org/forum/")
            .client(okHttpClient)
            // Scalar converter first for HTML responses
            .addConverterFactory(ScalarsConverterFactory.create())
            // JSON converter for any JSON responses
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /**
     * Provide RutrackerApi interface.
     */
    @Provides
    @Singleton
    fun provideRutrackerApi(retrofit: Retrofit): RutrackerApi = retrofit.create(RutrackerApi::class.java)

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        impl: com.jabook.app.jabook.compose.data.network.ConnectivityManagerNetworkMonitor,
    ): com.jabook.app.jabook.compose.data.network.NetworkMonitor = impl
}
