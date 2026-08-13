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

package com.jabook.app.jabook.compose.data.remote.network

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.jabook.app.jabook.core.datastore.DataStoreCorruptionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe in-memory cookie snapshot cache.
 *
 * OkHttp may invoke a [CookieJar] concurrently for requests on different dispatcher threads.
 * Keeping snapshots prevents a response callback from exposing a mutable caller-owned list to
 * another request while it is being read.
 */
internal class CookieMemoryCache {
    private val entries = ConcurrentHashMap<String, List<Cookie>>()

    fun store(
        host: String,
        cookies: List<Cookie>,
    ) {
        entries[host] = cookies.toList()
    }

    fun load(host: String): List<Cookie>? = entries[host]?.toList()

    fun clear() {
        entries.clear()
    }
}

/**
 * Persistent cookie jar that stores cookies in DataStore.
 *
 * This implementation persists cookies across app restarts,
 * which is essential for maintaining Rutracker authentication.
 */
@Singleton
public class PersistentCookieJar
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CookieJar {
        public companion object {
            private const val DATASTORE_NAME = "cookies"
            private const val COOKIE_SEPARATOR = "||"
            private const val ENCRYPTED_PREFIX = "v1:"
            private const val KEYSET_NAME = "cookies_keyset"
            private const val PREFERENCE_FILE = "cookies_keyset_prefs"
            private const val MASTER_KEY_URI = "android-keystore://cookies_master_key"
        }

        private val dataStore: DataStore<Preferences> by lazy {
            PreferenceDataStoreFactory.create(
                corruptionHandler = DataStoreCorruptionPolicy.preferencesHandler(storeName = DATASTORE_NAME),
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
            )
        }
        private val cache = CookieMemoryCache()
        private val aead: Aead? by lazy {
            runCatching {
                AeadConfig.register()
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
                    .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            }.getOrNull()
        }

        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            val host = url.host

            // Persist session material only with a Keystore-backed AEAD.
            runBlocking {
                dataStore.edit { prefs ->
                    val key = stringPreferencesKey(host)
                    val existingCookies =
                        prefs[key]
                            ?.let(::decrypt)
                            ?.split(COOKIE_SEPARATOR)
                            ?.mapNotNull(::deserializeCookie)
                            .orEmpty()
                    val mergedCookies = mergeCookies(existingCookies, cookies, System.currentTimeMillis())
                    val encrypted =
                        mergedCookies
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(COOKIE_SEPARATOR, transform = ::serializeCookie)
                            ?.let(::encrypt)
                    if (encrypted == null) prefs.remove(key) else prefs[key] = encrypted
                    cache.store(host, mergedCookies)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host

            // Try cache first
            cache.load(host)?.let { return it }

            // Load from DataStore
            val cookies =
                runBlocking {
                    val prefs = dataStore.data.first()
                    val key = stringPreferencesKey(host)
                    val encrypted: String = prefs[key] ?: return@runBlocking emptyList<Cookie>()
                    val serialized = decrypt(encrypted) ?: return@runBlocking emptyList<Cookie>()

                    serialized
                        .split(COOKIE_SEPARATOR)
                        .mapNotNull { cookieString -> deserializeCookie(cookieString) }
                        .filter { cookie -> !cookie.hasExpiredAt(System.currentTimeMillis()) }
                }

            cache.store(host, cookies)
            return cookies
        }

        /**
         * Clear all cookies.
         */
        public suspend fun clear() {
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

                var domain: String = ""
                var path: String = "/"
                var expiresAt = Long.MIN_VALUE

                var secure: Boolean = false
                var httpOnly: Boolean = false
                parts.drop(1).forEach { part ->
                    val trimmed = part.trim()
                    when {
                        trimmed.startsWith("domain=") -> domain = trimmed.substringAfter("domain=")
                        trimmed.startsWith("path=") -> path = trimmed.substringAfter("path=")
                        trimmed.startsWith("expires=") ->
                            expiresAt =
                                trimmed.substringAfter("expires=").toLongOrNull() ?: Long.MIN_VALUE
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

        private fun encrypt(value: String): String? =
            runCatching {
                val encrypted = checkNotNull(aead).encrypt(value.toByteArray(), null)
                ENCRYPTED_PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            }.getOrNull()

        private fun decrypt(value: String): String? {
            if (!value.startsWith(ENCRYPTED_PREFIX)) return null
            return runCatching {
                val encrypted = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
                checkNotNull(aead).decrypt(encrypted, null).decodeToString()
            }.getOrNull()
        }
    }

internal fun mergeCookies(
    existing: List<Cookie>,
    incoming: List<Cookie>,
    nowMillis: Long,
): List<Cookie> {
    val replacements = incoming.associateBy(::cookieIdentity)
    return (existing.filterNot { cookieIdentity(it) in replacements } + replacements.values)
        .filterNot { it.hasExpiredAt(nowMillis) }
}

private fun cookieIdentity(cookie: Cookie): Triple<String, String, String> = Triple(cookie.name, cookie.domain, cookie.path)

private fun Cookie.hasExpiredAt(nowMillis: Long): Boolean = expiresAt != Long.MIN_VALUE && expiresAt <= nowMillis
