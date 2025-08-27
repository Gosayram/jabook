package com.jabook.core.net.repository

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing User-Agent strings
 * Synchronizes WebView and OkHttp User-Agent values
 */
class UserAgentRepository(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("user_agent_prefs", Context.MODE_PRIVATE)
    private val _userAgent = MutableStateFlow(getStoredUserAgent())
    val userAgent: StateFlow<String> = _userAgent
    
    companion object {
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_LAST_UPDATED = "last_updated"
        private const val UPDATE_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Gets the current User-Agent string
     */
    fun get(): String {
        return _userAgent.value
    }
    
    /**
     * Updates User-Agent if needed based on WebView defaults
     */
    fun updateIfNeeded(webView: WebView) {
        val webViewUa = webView.settings.userAgentString
        val storedUa = getStoredUserAgent()
        
        if (webViewUa != storedUa) {
            // WebView UA changed, update our stored value
            setUserAgent(webViewUa)
        }
    }
    
    /**
     * Updates User-Agent from system defaults
     */
    fun updateFromSystem() {
        val systemUa = getSystemUserAgent()
        val storedUa = getStoredUserAgent()
        
        if (systemUa != storedUa) {
            setUserAgent(systemUa)
        }
    }
    
    /**
     * Sets a new User-Agent string
     */
    fun setUserAgent(userAgent: String) {
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply()
        _userAgent.value = userAgent
    }
    
    /**
     * Gets stored User-Agent with fallback to system default
     */
    private fun getStoredUserAgent(): String {
        val stored = prefs.getString(KEY_USER_AGENT, null)
        return stored ?: getSystemUserAgent()
    }
    
    /**
     * Gets system default User-Agent
     */
    private fun getSystemUserAgent(): String {
        return try {
            // Try WebSettings first (most reliable)
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            // Fallback to WebView
            try {
                WebView(context).settings.userAgentString
            } catch (e: Exception) {
                // Last resort to system property
                System.getProperty("http.agent") ?: "JaBook/1.0"
            }
        }
    }
    
    /**
     * Checks if User-Agent needs updating based on time
     */
    fun needsUpdate(): Boolean {
        val lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0)
        val now = System.currentTimeMillis()
        return (now - lastUpdated) > UPDATE_INTERVAL
    }
    
    /**
     * Forces an update check
     */
    fun forceUpdate() {
        updateFromSystem()
        prefs.edit().putLong(KEY_LAST_UPDATED, System.currentTimeMillis()).apply()
    }
    
    /**
     * Clears stored User-Agent (will use system default on next access)
     */
    fun clear() {
        prefs.edit().remove(KEY_USER_AGENT).remove(KEY_LAST_UPDATED).apply()
        _userAgent.value = getSystemUserAgent()
    }
}