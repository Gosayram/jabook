package com.jabook.app.jabook

import android.webkit.CookieManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Register CookieChannel for cookie management
        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "cookie_channel")
        val cookieManager = CookieManager.getInstance()
        
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getCookiesForUrl" -> {
                    val url = call.argument<String>("url")
                    if (url == null) {
                        result.error("INVALID_ARGUMENT", "URL is required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        val cookies = cookieManager.getCookie(url)
                        result.success(cookies ?: "")
                    } catch (e: Exception) {
                        result.error("GET_COOKIES_ERROR", e.message, null)
                    }
                }
                "setCookie" -> {
                    val url = call.argument<String>("url")
                    val cookieString = call.argument<String>("cookie")
                    if (url == null || cookieString == null) {
                        result.error("INVALID_ARGUMENT", "URL and cookie are required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        cookieManager.setCookie(url, cookieString)
                        cookieManager.flush()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SET_COOKIE_ERROR", e.message, null)
                    }
                }
                "clearAllCookies" -> {
                    try {
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("CLEAR_COOKIES_ERROR", e.message, null)
                    }
                }
                "flushCookies" -> {
                    try {
                        cookieManager.flush()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("FLUSH_COOKIES_ERROR", e.message, null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
}
