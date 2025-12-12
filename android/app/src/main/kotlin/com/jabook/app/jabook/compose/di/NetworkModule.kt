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
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
     * Provide OkHttp client with cookie persistence, dynamic base URL, and logging.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar,
        loggingInterceptor: HttpLoggingInterceptor,
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            .addInterceptor(dynamicBaseUrlInterceptor) // Add BEFORE logging for cleaner logs
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
}
