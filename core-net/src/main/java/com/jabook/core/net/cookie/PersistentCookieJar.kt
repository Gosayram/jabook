package com.jabook.core.net.cookie

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent CookieJar implementation for OkHttp
 * Stores cookies in SharedPreferences for persistence across app sessions
 */
class PersistentCookieJar(
    private val context: Context
) : CookieJar {
    
    private val cookieStore = ConcurrentHashMap<HttpUrl, MutableList<Cookie>>()
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    }
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "cookies") {
            loadCookiesFromPrefs()
        }
    }
    
    init {
        loadCookiesFromPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url] = cookies.toMutableList()
        saveCookiesToPrefs()
    }
    
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Return cookies for the specific URL and any matching domain cookies
        return cookieStore[url]?.filter { it.matches(url) } ?: emptyList()
    }
    
    /**
     * Clears all cookies
     */
    fun clearCookies() {
        cookieStore.clear()
        prefs.edit().clear().apply()
    }
    
    /**
     * Saves cookies to SharedPreferences
     */
    private fun saveCookiesToPrefs() {
        val editor = prefs.edit()
        editor.clear()
        
        cookieStore.forEach { (url, cookies) ->
            val cookieString = cookies.joinToString(";") { "${it.name}=${it.value}" }
            editor.putString(url.toString(), cookieString)
        }
        
        editor.apply()
    }
    
    /**
     * Loads cookies from SharedPreferences
     */
    private fun loadCookiesFromPrefs() {
        cookieStore.clear()
        
        prefs.all.forEach { (urlString, cookieString) ->
            val url = HttpUrl.parse(urlString)
            if (url != null && cookieString is String) {
                val cookies = cookieString.split(";").mapNotNull { cookiePart ->
                    val (name, value) = cookiePart.split("=", limit = 2)
                    Cookie.Builder()
                        .name(name.trim())
                        .value(value.trim())
                        .domain(url.host)
                        .build()
                }
                cookieStore[url] = cookies.toMutableList()
            }
        }
    }
    
    /**
     * Gets all cookies for a specific domain
     */
    fun getCookiesForDomain(domain: String): List<Cookie> {
        return cookieStore.keys
            .filter { it.host == domain }
            .flatMap { cookieStore[it] ?: emptyList() }
    }
    
    /**
     * Checks if user is logged in by looking for session cookies
     */
    fun isLoggedIn(): Boolean {
        return cookieStore.values.flatten().any { 
            it.name.lowercase().contains("session") || 
            it.name.lowercase().contains("auth") ||
            it.name.lowercase().contains("login")
        }
    }
}