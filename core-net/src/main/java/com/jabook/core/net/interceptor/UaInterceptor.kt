package com.jabook.core.net.interceptor

import com.jabook.core.net.repository.UserAgentRepository
import okhttp3.Interceptor
import okhttp3.Response

/**
 * User-Agent Interceptor for OkHttp
 * Ensures consistent User-Agent across all HTTP requests
 */
class UaInterceptor(private val userAgentRepository: UserAgentRepository) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Add User-Agent header if not already present
        val newRequest = originalRequest.newBuilder()
            .header("User-Agent", userAgentRepository.get())
            .build()
        
        return chain.proceed(newRequest)
    }
}