package com.jabook.core.net.interceptor

import com.jabook.core.net.repository.UserAgentRepository
import okhttp3.Interceptor
import okhttp3.Response

/**
 * User-Agent interceptor for OkHttp
 * Ensures consistent User-Agent between WebView and OkHttp requests
 */
class UaInterceptor(
    private val userAgentRepository: UserAgentRepository
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = userAgentRepository.get()
        
        // Add User-Agent header if not already present
        val newRequest = request.newBuilder()
            .header("User-Agent", userAgent)
            .build()
        
        return chain.proceed(newRequest)
    }
}