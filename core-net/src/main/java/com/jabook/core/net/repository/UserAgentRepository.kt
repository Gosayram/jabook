package com.jabook.core.net.repository

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Stores and retrieves a consistent User-Agent for WebView / OkHttp.
 * Avoids androidx.preference dependency; uses app SharedPreferences instead.
 */
class UserAgentRepository(
    context: Context
) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns current stored User-Agent or a computed default.
     */
    fun get(): String {
        return prefs.getString(KEY_USER_AGENT, null) ?: computeDefaultUserAgent().also {
            // Cache the computed default so further calls are cheap
            prefs.edit().putString(KEY_USER_AGENT, it).apply()
        }
    }

    /**
     * Persists a custom User-Agent string.
     */
    fun set(userAgent: String) {
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply()
    }

    /**
     * Updates stored UA if it differs from current device UA.
     * @param webView Optional WebView if you already have one on UI thread.
     * @return true if updated.
     */
    fun updateIfNeeded(webView: WebView? = null): Boolean {
        val current = computeDefaultUserAgent(webView)
        val stored = prefs.getString(KEY_USER_AGENT, null)
        return if (!current.isNullOrBlank() && current != stored) {
            prefs.edit().putString(KEY_USER_AGENT, current).apply()
            true
        } else {
            false
        }
    }

    /**
     * Clears stored UA forcing recomputation next time.
     */
    fun clear() {
        prefs.edit().remove(KEY_USER_AGENT).apply()
    }

    /**
     * Simple sanity check for stored UA.
     */
    fun isValid(): Boolean {
        val ua = get()
        return ua.isNotBlank() && "Mozilla" in ua
    }

    // ---- internals ----

    /**
     * Computes a reasonable default UA.
     * If called off the main thread, avoids constructing a WebView.
     */
    private fun computeDefaultUserAgent(webView: WebView? = null): String {
        // 1) Fast path: WebSettings API (works off main thread)
        val fromWebSettings = runCatching { WebSettings.getDefaultUserAgent(appContext) }.getOrNull()
        if (!fromWebSettings.isNullOrBlank()) return fromWebSettings

        // 2) If caller passed a WebView (UI thread), use it
        if (webView != null) {
            runCatching { webView.settings.userAgentString }.getOrNull()?.let { return it }
        }

        // 3) Fallback: system http.agent or generic UA
        val sys = System.getProperty("http.agent")
        if (!sys.isNullOrBlank()) return sys

        // 4) Last resort generic
        return "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
    }

    companion object {
        private const val PREFS_NAME = "ua_prefs"
        private const val KEY_USER_AGENT = "user_agent"
    }
}