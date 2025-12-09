package com.jabook.app.jabook.handlers

import android.webkit.CookieManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class CookieMethodHandler(
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "cookie_channel")
    private val cookieManager = CookieManager.getInstance()

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "getCookiesForUrl" -> {
                val url = call.argument<String>("url")
                if (url == null) {
                    result.error("INVALID_ARGUMENT", "URL is required", null)
                    return
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
                    return
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

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }
}
