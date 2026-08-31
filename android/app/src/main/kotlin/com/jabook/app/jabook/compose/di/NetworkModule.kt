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

package com.jabook.app.jabook.compose.di

import android.content.Context
import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.compose.data.network.AuthInterceptor
import com.jabook.app.jabook.compose.data.network.DynamicBaseUrlInterceptor
import com.jabook.app.jabook.compose.data.network.DynamicTimeoutInterceptor
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.network.NetworkMonitor
import com.jabook.app.jabook.compose.data.network.NetworkTelemetryEventListenerFactory
import com.jabook.app.jabook.compose.data.network.RutrackerCertificatePinningPolicy
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import com.jabook.app.jabook.core.network.NetworkRuntimePolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for network dependencies.
 *
 * Provides Retrofit, OkHttp client, and API interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
public object NetworkModule {
    private val rutrackerCertificatePinner by lazy {
        RutrackerCertificatePinningPolicy.buildCertificatePinner()
    }

    /**
     * Provide JSON serializer for Retrofit.
     */
    @Provides
    @Singleton
    public fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    /**
     * Provide logging interceptor.
     *
     * Request bodies can contain credentials, so logging is limited to request metadata.
     */
    @Provides
    @Singleton
    public fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
            redactHeader("X-Api-Key")
        }

    /**
     * Provide shared DNS-over-HTTPS resolver.
     *
     * Singleton so the mirror health client and the API client share one
     * DnsCache instead of each paying a DoH round trip per fresh lookup.
     */
    @Provides
    @Singleton
    public fun provideDohDns(): com.jabook.app.jabook.compose.data.network.DnsOverHttpsDns =
        com.jabook.app.jabook.compose.data.network.DnsOverHttpsDns
            .create()

    /**
     * Provide MirrorManager.
     *
     * Uses a lightweight OkHttpClient for health checks to avoid circular dependency.
     */
    @Provides
    @Singleton
    public fun provideMirrorManager(
        settingsRepository: SettingsRepository,
        cookieJar: PersistentCookieJar,
        loggerFactory: com.jabook.app.jabook.compose.core.logger.LoggerFactory,
        networkTelemetryEventListenerFactory: NetworkTelemetryEventListenerFactory,
        dohDns: com.jabook.app.jabook.compose.data.network.DnsOverHttpsDns,
    ): MirrorManager {
        // Lightweight OkHttpClient for health checks only
        val healthCheckClient =
            OkHttpClient
                .Builder()
                .dns(dohDns)
                .connectionPool(ConnectionPool(3, 2, TimeUnit.MINUTES))
                .certificatePinner(rutrackerCertificatePinner)
                .callTimeout(NetworkRuntimePolicy.MIRROR_HEALTH_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(NetworkRuntimePolicy.MIRROR_HEALTH_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NetworkRuntimePolicy.MIRROR_HEALTH_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(NetworkRuntimePolicy.MIRROR_HEALTH_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .apply {
                    if (BuildConfig.DEBUG) {
                        eventListenerFactory(networkTelemetryEventListenerFactory)
                    }
                }.build()

        return MirrorManager(settingsRepository, healthCheckClient, loggerFactory)
    }

    /**
     * Provide DynamicBaseUrlInterceptor.
     */
    @Provides
    @Singleton
    public fun provideDynamicBaseUrlInterceptor(
        mirrorManager: MirrorManager,
        loggerFactory: com.jabook.app.jabook.compose.core.logger.LoggerFactory,
    ): DynamicBaseUrlInterceptor = DynamicBaseUrlInterceptor(mirrorManager, loggerFactory)

    /**
     * Provide OkHttp client with cookie persistence, auto re-auth, dynamic base URL, proper headers, and logging.
     */
    @Provides
    @Singleton
    public fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cookieJar: PersistentCookieJar,
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        dynamicTimeoutInterceptor: DynamicTimeoutInterceptor,
        rutrackerHeadersInterceptor: com.jabook.app.jabook.compose.data.network.RutrackerHeadersInterceptor,
        networkTelemetryEventListenerFactory: NetworkTelemetryEventListenerFactory,
        dohDns: com.jabook.app.jabook.compose.data.network.DnsOverHttpsDns,
    ): OkHttpClient {
        // DNS-over-HTTPS: bypass DNS blocks on some ISPs without VPN
        // Uses Google Public DNS over HTTPS; falls back to system DNS on failure
        val apiCache = Cache(context.cacheDir.resolve("rutracker_http"), HTTP_CACHE_SIZE_BYTES)

        return OkHttpClient
            .Builder()
            .dns(dohDns)
            .cache(apiCache)
            .connectionPool(ConnectionPool(3, 2, TimeUnit.MINUTES))
            .cookieJar(cookieJar)
            .certificatePinner(rutrackerCertificatePinner)
            // Re-auth logins issued from AuthInterceptor run on this same client;
            // the default perHost limit of 5 can starve them under load.
            .dispatcher(
                Dispatcher().apply { maxRequestsPerHost = 16 },
            ).apply {
                if (BuildConfig.DEBUG) {
                    eventListenerFactory(networkTelemetryEventListenerFactory)
                }
            }
            // Interceptor order matters! They are called in order:
            // 1. BrotliInterceptor - MUST be first to add Accept-Encoding header (only if not already set)
            // 2. RutrackerHeadersInterceptor - Adds User-Agent, Accept, Accept-Language (NO Accept-Encoding!)
            // 3. AuthInterceptor - Handles session expiry and re-authentication
            // 4. DynamicBaseUrlInterceptor - Switches between RuTracker mirrors
            // 5. DynamicTimeoutInterceptor - Per-request timeout from @RequestTimeout annotation
            // 6. LoggingInterceptor - Last to log final request/response
            .addInterceptor(BrotliInterceptor) // Automatic Brotli decompression (MUST be first to add Accept-Encoding!)
            .addInterceptor(rutrackerHeadersInterceptor) // Add browser-like headers (NO Accept-Encoding - BrotliInterceptor handles it)
            .addInterceptor(authInterceptor) // Auto re-authentication
            .addInterceptor(dynamicBaseUrlInterceptor) // Dynamic base URL for mirrors
            .addInterceptor(dynamicTimeoutInterceptor) // Per-endpoint timeout override via @RequestTimeout
            .addInterceptor(loggingInterceptor) // Logging last for complete request/response
            // Improved timeouts with better defaults
            .callTimeout(NetworkRuntimePolicy.API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(NetworkRuntimePolicy.API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkRuntimePolicy.API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkRuntimePolicy.API_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // OkHttp defaults (explicit for clarity):
            // - retryOnConnectionFailure = true (retry on connection failures)
            // - followRedirects = true (follow HTTP redirects)
            // - followSslRedirects = true (follow redirects between HTTP/HTTPS)
            .build()
    }

    /**
     * Client for untrusted cover URLs. It deliberately has no cookies, auth, or mirror rewriting.
     */
    @Provides
    @Singleton
    @Named("coverDownload")
    public fun provideCoverDownloadClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(NetworkRuntimePolicy.API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(NetworkRuntimePolicy.API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkRuntimePolicy.API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkRuntimePolicy.API_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private const val HTTP_CACHE_SIZE_BYTES: Long = 10L * 1024L * 1024L

    /**
     * Provide Retrofit instance.
     *
     * Note: The baseUrl here is just a placeholder. The actual base URL is dynamically
     * determined by DynamicBaseUrlInterceptor which replaces the host with the current
     * mirror from MirrorManager. This baseUrl is only used for relative path resolution.
     */
    @Provides
    @Singleton
    public fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            // Base URL is a placeholder - DynamicBaseUrlInterceptor replaces host with current mirror.
            // Supplied at build time from .env (RUTRACKER_BASE_URL) — never hardcoded in source.
            .baseUrl(BuildConfig.RUTRACKER_BASE_URL)
            .client(okHttpClient)
            // Scalar converter: never fires for Response<ResponseBody> endpoints (JSON converter
            // wins); kept for future String/HTML endpoints.
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
    public fun provideRutrackerApi(retrofit: Retrofit): RutrackerApi = retrofit.create(RutrackerApi::class.java)
}

/**
 * Hilt module for network interface bindings (following Flow pattern).
 * Uses @Binds for better performance when binding interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class NetworkBindingModule {
    /**
     * Binds NetworkMonitor implementation to interface (following Flow pattern).
     * Using @Binds is more efficient than @Provides for interface bindings.
     */
    @Binds
    @Singleton
    public abstract fun bindNetworkMonitor(impl: com.jabook.app.jabook.compose.data.network.DebuggableNetworkMonitor): NetworkMonitor
}
