package com.jabook.core.net

import android.content.Context
import com.jabook.core.net.interceptor.UaInterceptor
import com.jabook.core.net.cookie.PersistentCookieJar
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HTTP client configuration for JaBook app
 * Provides centralized OkHttp client with interceptors and configuration
 */
object Http {
    
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    private const val CACHE_SIZE = 10 * 1024 * 1024L // 10MB
    
    /**
     * Creates and configures OkHttp client
     */
    fun createClient(
        context: Context,
        userAgentInterceptor: UaInterceptor,
        enableLogging: Boolean = false
    ): OkHttpClient {
        
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .cookieJar(PersistentCookieJar(context))
            .addInterceptor(userAgentInterceptor)
        
        // Add cache if available
        val cacheDir = File(context.cacheDir, "http_cache")
        if (cacheDir.exists() || cacheDir.mkdirs()) {
            builder.cache(Cache(cacheDir, CACHE_SIZE))
        }
        
        // Add logging interceptor in debug mode
        if (enableLogging) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        
        return builder.build()
    }
    
    /**
     * Creates a new request builder with common headers
     */
    fun createRequestBuilder() = okhttp3.Request.Builder()
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.5")
        .header("Accept-Encoding", "gzip, deflate")
        .header("DNT", "1")
        .header("Connection", "keep-alive")
        .header("Upgrade-Insecure-Requests", "1")
}