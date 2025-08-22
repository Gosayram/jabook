package com.jabook.app.core.di

import com.jabook.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Hilt module for network dependencies */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class UserAgentInterceptorQualifier

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class RetryInterceptorQualifier

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }

    @Provides
    @Singleton
    fun provideCookieJar(): CookieJar =
        object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

            override fun saveFromResponse(
                url: HttpUrl,
                cookies: List<Cookie>,
            ) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: emptyList()
        }

    @Provides
    @Singleton
    @UserAgentInterceptorQualifier
    fun provideUserAgentInterceptor(): Interceptor =
        Interceptor { chain ->
            val userAgents =
                listOf(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                        "Version/17.1 Safari/605.1.15",
                )

            val randomUserAgent = userAgents.random()
            val originalRequest = chain.request()

            val newRequest =
                originalRequest
                    .newBuilder()
                    .header("User-Agent", randomUserAgent)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9," +
                            "image/avif,image/webp,image/apng,*/*;q=0.8," +
                            "application/signed-exchange;v=b3;q=0.7",
                    ).header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Cache-Control", "max-age=0")
                    .build()

            chain.proceed(newRequest)
        }

    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool =
        ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES,
        )

    @Provides
    @Singleton
    @RetryInterceptorQualifier
    fun provideRetryInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            var response: Response? = null
            var attempts = 0
            val maxRetries = 3
            while (attempts < maxRetries) {
                try {
                    attempts++
                    response = chain.proceed(request)
                    if (response.isSuccessful) {
                        return@Interceptor response
                    } else {
                        response.close()
                        println("Request failed, retrying in ${attempts * 2} seconds")
                        Thread.sleep((attempts * 2000).toLong()) // Exponential backoff
                    }
                } catch (e: IOException) {
                    println("Request failed with exception: ${e.message}, retrying in ${attempts * 2} seconds")
                    Thread.sleep((attempts * 2000).toLong()) // Exponential backoff
                }
            }
            response ?: throw IOException("Max retries reached, request failed")
            response
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        cookieJar: CookieJar,
        @UserAgentInterceptorQualifier userAgentInterceptor: Interceptor,
        connectionPool: ConnectionPool,
        @RetryInterceptorQualifier retryInterceptor: Interceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)
            .cookieJar(cookieJar)
            .connectionPool(connectionPool)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
}
