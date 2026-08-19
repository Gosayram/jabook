// Copyright 2026 Jabook Contributors
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

import android.webkit.CookieManager
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cookie persistence manager.
 * Synchronizes cookies between the encrypted native jar and WebView.
 */
@Singleton
public class CookiePersistenceManager
    @Inject
    constructor(
        private val cookieJar: PersistentCookieJar,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("CookiePersistence")

        /**
         * Persist cookies to every implemented storage layer.
         */
        public suspend fun persistCookiesMultiStage(url: String): Unit =
            withContext(Dispatchers.IO) {
                val httpUrl = url.toHttpUrl()
                val cookies = cookieJar.loadForRequest(httpUrl)

                if (cookies.isEmpty()) {
                    logger.d { "No cookies to persist for $url" }
                    return@withContext
                }

                logger.d { "Persisting ${cookies.size} cookies for $url" }

                // Sync the encrypted native jar into WebView when needed.
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
                    logger.d { "Cookies synced to WebView CookieManager" }
                } catch (e: Exception) {
                    logger.e({ "Failed to sync to WebView" }, e)
                }

                logger.i { "Multi-stage persist complete for $url" }
            }

        /**
         * Sync cookies from WebView to other layers.
         * Call this after WebView login.
         */
        public suspend fun syncCookiesFromWebView(url: String): Unit =
            withContext(Dispatchers.IO) {
                try {
                    val cookieManager = CookieManager.getInstance()

                    // Ensure URL has a path — CookieManager.getCookie() requires path matching
                    val queryUrl = if (url.endsWith("/")) url else "$url/"

                    cookieManager.flush()

                    val cookieString = cookieManager.getCookie(queryUrl)
                    logger.d { "getCookie($queryUrl) returned ${if (cookieString.isNullOrBlank()) "no" else "some"} cookies" }

                    if (!cookieString.isNullOrBlank()) {
                        val cookies = captureWebViewCookies(queryUrl, cookieString)
                        logger.d { "Captured ${cookies.size} cookies: ${cookies.joinToString { it.name }}" }

                        if (cookies.isEmpty()) {
                            logger.d { "No cookies captured from WebView" }
                            return@withContext
                        }

                        val httpUrl = queryUrl.toHttpUrl()
                        cookieJar.saveFromResponse(httpUrl, cookies)

                        logger.i { "Synced ${cookies.size} cookies from WebView for $queryUrl" }
                    } else {
                        logger.d { "No cookies in WebView for $queryUrl" }
                    }
                } catch (e: Exception) {
                    logger.e({ "Failed to sync from WebView" }, e)
                }
            }

        /** Removes the only session cookie that the fallback flow can import. */
        public suspend fun clearWebViewSession(url: String): Unit =
            withContext(Dispatchers.IO) {
                val host = url.toHttpUrl().host
                val cookieManager = CookieManager.getInstance()
                cookieManager.setCookie(url, "$RUTRACKER_SESSION_COOKIE=; Max-Age=0; Path=/; Secure; HttpOnly")
                cookieManager.setCookie(url, "$RUTRACKER_SESSION_COOKIE=; Max-Age=0; Domain=$host; Path=/; Secure; HttpOnly")
                cookieManager.flush()
            }
    }

internal fun captureWebViewCookies(
    url: String,
    cookieHeader: String,
): List<Cookie> {
    val httpUrl = url.toHttpUrl()
    val domain = httpUrl.host

    return cookieHeader
        .split(";")
        .mapNotNull { part ->
            val pieces = part.trim().split("=", limit = 2)
            if (pieces.size != 2) return@mapNotNull null
            val name = pieces[0].trim()
            val value = pieces[1].trim()
            if (name.isEmpty() || value.isEmpty()) return@mapNotNull null

            runCatching {
                Cookie
                    .Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path("/")
                    .apply {
                        // Cloudflare cookies often need Secure; session cookies too
                        if (name.startsWith("__cf") || name == "cf_clearance" || name == RUTRACKER_SESSION_COOKIE) {
                            secure()
                            httpOnly()
                        }
                    }.build()
            }.getOrNull()
        }
}

private const val RUTRACKER_SESSION_COOKIE = "bb_session"
