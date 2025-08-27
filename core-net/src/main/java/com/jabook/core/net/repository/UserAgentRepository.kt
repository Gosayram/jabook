package com.jabook.core.net.repository

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.preference.PreferenceManager

/**
 * User-Agent repository for managing consistent User-Agent across WebView and OkHttp
 * Stores and retrieves User-Agent from SharedPreferences
 */
class UserAgentRepository(
    private val context: Context
) {
    
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val KEY_USER_AGENT = "user_agent"
    
    /**
     * Gets the current User-Agent
     * @return User-Agent string or default if not set
     */
    fun get(): String {
        return prefs.getString(KEY_USER_AGENT, getDefaultUserAgent()) ?: getDefaultUserAgent()
    }
    
    /**
     * Sets the User-Agent
     * @param userAgent User-Agent string to store
     */
    fun set(userAgent: String) {
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply()
    }
    
    /**
     * Gets the default User-Agent for the device
     * @return Default User-Agent string
     */
    private fun getDefaultUserAgent(): String {
        return try {
            // Try to get the default User-Agent from WebSettings
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            // Fallback to WebView settings if getDefaultUserAgent fails
            try {
                WebView(context).settings.userAgentString
            } catch (e: Exception) {
                // Final fallback to a generic User-Agent
                "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
            }
        }
    }
    
    /**
     * Updates the User-Agent if it has changed
     * @param webView WebView instance to get current User-Agent from
     * @return true if User-Agent was updated, false otherwise
     */
    fun updateIfNeeded(webView: WebView): Boolean {
        val current = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            webView.settings.userAgentString
        }
        
        val stored = get()
        
        if (current.isNotBlank() && current != stored) {
            set(current)
            return true
        }
        
        return false
    }
    
    /**
     * Clears the stored User-Agent
     */
    fun clear() {
        prefs.edit().remove(KEY_USER_AGENT).apply()
    }
    
    /**
     * Checks if the stored User-Agent is valid
     * @return true if User-Agent is valid, false otherwise
     */
    fun isValid(): Boolean {
        val ua = get()
        return ua.isNotBlank() && ua.contains("Mozilla") && ua.contains("Android")
    }
}