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

package com.jabook.app.jabook.compose.data.remote.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent cookie jar that stores cookies in DataStore.
 *
 * This implementation persists cookies across app restarts,
 * which is essential for maintaining Rutracker authentication.
 */
@Singleton
class PersistentCookieJar
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CookieJar {
        companion object {
            private const val DATASTORE_NAME = "cookies"
            private const val COOKIE_SEPARATOR = "||"

            private val Context.cookieDataStore: DataStore<Preferences> by preferencesDataStore(
                name = DATASTORE_NAME,
            )
        }

        private val dataStore = context.cookieDataStore
        private val cache = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            val host = url.host
            cache[host] = cookies

            // Persist to DataStore
            runBlocking {
                dataStore.edit { prefs ->
                    val key = stringPreferencesKey(host)
                    val serialized = cookies.joinToString(COOKIE_SEPARATOR) { serializeCookie(it) }
                    prefs[key] = serialized
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host

            // Try cache first
            cache[host]?.let { return it }

            // Load from DataStore
            val cookies =
                runBlocking {
                    val prefs = dataStore.data.first()
                    val key = stringPreferencesKey(host)
                    val serialized: String? = prefs[key]
                    if (serialized == null) return@runBlocking emptyList<Cookie>()

                    serialized
                        .split(COOKIE_SEPARATOR)
                        .mapNotNull { cookieString -> deserializeCookie(cookieString) }
                        .filter { cookie -> !cookie.hasExpired() }
                }

            cache[host] = cookies
            return cookies
        }

        /**
         * Clear all cookies.
         */
        suspend fun clear() {
            cache.clear()
            dataStore.edit { it.clear() }
        }

        private fun serializeCookie(cookie: Cookie): String =
            buildString {
                append(cookie.name)
                append("=")
                append(cookie.value)
                append(";domain=")
                append(cookie.domain)
                append(";path=")
                append(cookie.path)
                if (cookie.expiresAt != Long.MIN_VALUE) {
                    append(";expires=")
                    append(cookie.expiresAt)
                }
                if (cookie.secure) append(";secure")
                if (cookie.httpOnly) append(";httponly")
            }

        private fun deserializeCookie(serialized: String): Cookie? {
            return try {
                val parts = serialized.split(";")
                val nameValue = parts[0].split("=", limit = 2)
                if (nameValue.size != 2) return null

                val name = nameValue[0]
                val value = nameValue[1]

                var domain = ""
                var path = "/"
                var expiresAt = Long.MIN_VALUE
                var secure = false
                var httpOnly = false

                parts.drop(1).forEach { part ->
                    val trimmed = part.trim()
                    when {
                        trimmed.startsWith("domain=") -> domain = trimmed.substringAfter("domain=")
                        trimmed.startsWith("path=") -> path = trimmed.substringAfter("path=")
                        trimmed.startsWith("expires=") -> expiresAt = trimmed.substringAfter("expires=").toLongOrNull() ?: Long.MIN_VALUE
                        trimmed == "secure" -> secure = true
                        trimmed == "httponly" -> httpOnly = true
                    }
                }

                Cookie
                    .Builder()
                    .name(name)
                    .value(value)
                    .domain(domain)
                    .path(path)
                    .apply {
                        if (expiresAt != Long.MIN_VALUE) expiresAt(expiresAt)
                        if (secure) secure()
                        if (httpOnly) httpOnly()
                    }.build()
            } catch (e: Exception) {
                null
            }
        }

        private fun Cookie.hasExpired(): Boolean {
            if (expiresAt == Long.MIN_VALUE) return false
            return expiresAt < System.currentTimeMillis()
        }
    }
