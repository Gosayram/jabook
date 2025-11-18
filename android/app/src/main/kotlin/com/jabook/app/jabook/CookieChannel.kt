package com.jabook.app.jabook

import android.webkit.CookieManager
import android.webkit.WebView
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/// Channel for managing cookies via Android CookieManager.
///
/// This class provides a bridge between Flutter and Android's native CookieManager,
/// allowing Flutter to read and manage cookies that are set by WebView.
class CookieChannel : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private val cookieManager: CookieManager = CookieManager.getInstance()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "cookie_channel")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
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
            else -> {
                result.notImplemented()
            }
        }
    }
}

