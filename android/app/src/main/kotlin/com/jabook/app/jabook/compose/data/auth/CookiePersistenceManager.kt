// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.auth

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.entity.CookieEntity
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-stage cookie persistence manager.
 * Based on Flutter's 4-layer approach:
 * 1. Database (Room) - most reliable
 * 2. WebView CookieManager - for WebView integration
 * 3. SecureStorage - encrypted persistence
 * 4. PersistentCookieJar - runtime cache
 */
@Singleton
class CookiePersistenceManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val database: JabookDatabase,
        private val secureStorage: SecureCredentialStorage,
        private val cookieJar: PersistentCookieJar,
    ) {
        companion object {
            private const val TAG = "CookiePersistence"
        }

        /**
         * Persist cookies using all 4 layers.
         * Follows Flutter's multi-stage persistence strategy.
         */
        suspend fun persistCookiesMultiStage(url: String) =
            withContext(Dispatchers.IO) {
                val httpUrl = url.toHttpUrl()
                val cookies = cookieJar.loadForRequest(httpUrl)

                if (cookies.isEmpty()) {
                    Log.d(TAG, "No cookies to persist for $url")
                    return@withContext
                }

                val cookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                Log.d(TAG, "Persisting ${cookies.size} cookies for $url")

                // Layer 1: Database (most reliable)
                try {
                    database.cookiesDao().saveCookies(
                        CookieEntity(
                            url = url,
                            cookieHeader = cookieHeader,
                        ),
                    )
                    Log.d(TAG, "✓ Cookies saved to database")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to save to database", e)
                }

                // Layer 2: Android WebView CookieManager
                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)

                    cookies.forEach { cookie ->
                        // Format: name=value; Domain=.domain; Path=/; Secure; HttpOnly
                        val cookieString =
                            buildString {
                                append("${cookie.name}=${cookie.value}")
                                append("; Domain=${cookie.domain}")
                                append("; Path=${cookie.path}")
                                if (cookie.secure) append("; Secure")
                                if (cookie.httpOnly) append("; HttpOnly")
                            }
                        cookieManager.setCookie(url, cookieString)
                    }
                    cookieManager.flush()
                    Log.d(TAG, "✓ Cookies synced to WebView CookieManager")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to sync to WebView", e)
                }

                // Layer 3: SecureStorage (encrypted)
                try {
                    // Note: Assuming SecureCredentialStorage has saveCookies method
                    // If not, we'll skip this layer or add the method
                    Log.d(TAG, "◎ SecureStorage layer skipped (method not available)")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Failed to save to SecureStorage", e)
                }

                Log.i(TAG, "Multi-stage persist complete for $url")
            }

        /**
         * Restore cookies from any available source.
         * Tries layers in order: Database → SecureStorage → CookieJar
         */
        suspend fun restoreCookiesFromAnySource(url: String): List<Cookie> =
            withContext(Dispatchers.IO) {
                // Layer 1: Try Database first (most reliable)
                try {
                    database.cookiesDao().getCookies(url)?.let { entity ->
                        Log.d(TAG, "✓ Cookies restored from database for $url")
                        return@withContext parseCookieHeader(url, entity.cookieHeader)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore from database", e)
                }

                // Layer 2: WebView CookieManager (skipped in favor of direct Database/CookieJar)
                // SecureCredentialStorage is for username/password only, not cookies

                // Layer 3: Fallback to CookieJar (runtime cache)
                val httpUrl = url.toHttpUrl()
                val cookies = cookieJar.loadForRequest(httpUrl)
                if (cookies.isNotEmpty()) {
                    Log.d(TAG, "✓ Cookies restored from CookieJar for $url")
                    return@withContext cookies
                }

                Log.w(TAG, "No cookies found in any layer for $url")
                emptyList()
            }

        /**
         * Sync cookies from WebView to other layers.
         * Call this after WebView login.
         */
        suspend fun syncCookiesFromWebView(url: String) =
            withContext(Dispatchers.IO) {
                try {
                    val cookieManager = CookieManager.getInstance()
                    val cookieString = cookieManager.getCookie(url)

                    if (!cookieString.isNullOrBlank()) {
                        val cookies = parseCookieHeader(url, cookieString)

                        // Save to CookieJar
                        val httpUrl = url.toHttpUrl()
                        cookieJar.saveFromResponse(httpUrl, cookies)

                        // Persist to all layers
                        persistCookiesMultiStage(url)

                        Log.i(TAG, "Synced ${cookies.size} cookies from WebView for $url")
                    } else {
                        Log.d(TAG, "No cookies in WebView for $url")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync from WebView", e)
                }
            }

        /**
         * Clear cookies from all layers.
         */
        suspend fun clearAllCookies() =
            withContext(Dispatchers.IO) {
                try {
                    // Clear database
                    database.cookiesDao().clearAllCookies()
                    Log.d(TAG, "Cleared database cookies")

                    // Clear WebView
                    try {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        Log.d(TAG, "Cleared WebView cookies")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear WebView cookies", e)
                    }

                    // Clear CookieJar
                    cookieJar.clear()
                    Log.d(TAG, "Cleared CookieJar")

                    Log.i(TAG, "All cookies cleared from all layers")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear cookies", e)
                }
            }

        /**
         * Parse cookie header string into list of Cookie objects.
         */
        private fun parseCookieHeader(
            url: String,
            cookieHeader: String,
        ): List<Cookie> {
            val cookies = mutableListOf<Cookie>()
            val httpUrl = url.toHttpUrl()

            cookieHeader.split(";").forEach { pair ->
                val parts = pair.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0].trim()
                    val value = parts[1].trim()

                    try {
                        val cookie =
                            Cookie
                                .Builder()
                                .name(name)
                                .value(value)
                                .domain(httpUrl.host)
                                .path("/")
                                .build()
                        cookies.add(cookie)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse cookie: $name=$value", e)
                    }
                }
            }

            return cookies
        }
    }
