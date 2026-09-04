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
import com.jabook.app.jabook.crash.CrashDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

        @Serializable
        private data class PersistedCookie(
            val name: String,
            val value: String,
            val domain: String,
            val path: String,
            val expiresAt: Long? = null,
            val secure: Boolean = false,
            val httpOnly: Boolean = false,
        )

        private fun Cookie.toPersisted(): PersistedCookie =
            PersistedCookie(
                name = name,
                value = value,
                domain = domain,
                path = path,
                expiresAt = expiresAt.takeIf { it != Long.MIN_VALUE },
                secure = secure,
                httpOnly = httpOnly,
            )

        private fun PersistedCookie.toCookie(): Cookie =
            Cookie
                .Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(path)
                .apply {
                    if (expiresAt != null) expiresAt(expiresAt)
                    if (secure) secure()
                    if (httpOnly) httpOnly()
                }.build()

        private val cookieJson =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        private val dataStore: DataStore<Preferences> by lazy {
            PreferenceDataStoreFactory.create(
                corruptionHandler = DataStoreCorruptionPolicy.preferencesHandler(storeName = DATASTORE_NAME),
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
            )
        }
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val cache = CookieMemoryCache()
        private val aead: Aead? by lazy {
            runCatching {
                AeadConfig.register()
                @Suppress("DEPRECATION")
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
                    .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            }.onFailure {
                CrashDiagnostics.reportNonFatal(
                    tag = "cookie_keyset_init_failed",
                    throwable = it,
                    attributes = mapOf("component" to "PersistentCookieJar"),
                )
            }.getOrNull()
        }

        init {
            // Warm in-memory cache from DataStore on a background thread so the
            // first loadForRequest call doesn't block on runBlocking.
            scope.launch {
                try {
                    val prefs = dataStore.data.first()
                    val nowMillis = System.currentTimeMillis()
                    for (entry in prefs.asMap()) {
                        val key = entry.key.name
                        val encrypted = entry.value as? String ?: continue
                        val serialized = decrypt(encrypted, key) ?: continue
                        val cookies =
                            serialized
                                .split(COOKIE_SEPARATOR)
                                .mapNotNull { deserializeCookie(it) }
                                .filter { !it.hasExpiredAt(nowMillis) }
                        if (cookies.isNotEmpty()) {
                            cache.store(key, cookies)
                        }
                    }
                } catch (_: Exception) {
                    // Best-effort warm-up; cold start falls back to DataStore.
                }
            }
        }

        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            val host = url.host
            val nowMillis = System.currentTimeMillis()

            // Update in-memory cache synchronously so concurrent requests see fresh cookies.
            cache.load(host)?.let { existingCookies ->
                val merged = mergeCookies(existingCookies, cookies, nowMillis)
                cache.store(host, merged)
            } ?: cache.store(host, mergeCookies(emptyList(), cookies, nowMillis))

            // Persist to DataStore in the background — no need to block the OkHttp thread.
            scope.launch {
                dataStore.edit { prefs ->
                    val key = stringPreferencesKey(host)
                    val existingCookies =
                        prefs[key]
                            ?.let { decrypt(it, host) }
                            ?.split(COOKIE_SEPARATOR)
                            ?.mapNotNull(::deserializeCookie)
                            .orEmpty()
                    val mergedCookies = mergeCookies(existingCookies, cookies, nowMillis)
                    val encrypted =
                        mergedCookies
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(COOKIE_SEPARATOR, transform = ::serializeCookie)
                            ?.let { encrypt(it, host) }
                    if (encrypted == null) prefs.remove(key) else prefs[key] = encrypted
                    cache.store(host, mergedCookies)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val nowMillis = System.currentTimeMillis()

            // Return from in-memory cache (warmed at construction).
            cache.load(host)?.let { cachedCookies ->
                return cachedCookies.filter { !it.hasExpiredAt(nowMillis) && it.matches(url) }
            }

            // Cold start fallback: cache not yet warm, load from DataStore once.
            val storedCookies =
                try {
                    runBlocking {
                        val prefs = dataStore.data.first()
                        val key = stringPreferencesKey(host)
                        val encrypted: String = prefs[key] ?: return@runBlocking emptyList<Cookie>()
                        val serialized = decrypt(encrypted, host) ?: return@runBlocking emptyList<Cookie>()

                        serialized
                            .split(COOKIE_SEPARATOR)
                            .mapNotNull { cookieString -> deserializeCookie(cookieString) }
                            .filter { cookie -> !cookie.hasExpiredAt(nowMillis) }
                    }
                } catch (e: java.io.IOException) {
                    com.jabook.app.jabook.util.LogUtils
                        .e("PersistentCookieJar", "Failed to load cookies from DataStore", e)
                    emptyList<Cookie>()
                }

            cache.store(host, storedCookies)
            return storedCookies.filter { it.matches(url) }
        }

        /**
         * Clear all cookies.
         */
        public suspend fun clear() {
            cache.clear()
            dataStore.edit { it.clear() }
        }

        private fun serializeCookie(cookie: Cookie): String = cookieJson.encodeToString(PersistedCookie.serializer(), cookie.toPersisted())

        private fun deserializeCookie(serialized: String): Cookie? =
            runCatching {
                cookieJson.decodeFromString(PersistedCookie.serializer(), serialized).toCookie()
            }.getOrNull() ?: runCatching {
                // Fallback for legacy "name=value;domain=...;path=..." format
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
                        trimmed.startsWith("expires=") ->
                            expiresAt = trimmed.substringAfter("expires=").toLongOrNull() ?: Long.MIN_VALUE
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
            }.getOrNull()

        /** AAD binds each ciphertext to its host; legacy rows were written with null AAD. */
        private fun hostAad(host: String): ByteArray = "cookie:$host".toByteArray()

        private fun encrypt(
            value: String,
            host: String,
        ): String? =
            runCatching {
                val encrypted = checkNotNull(aead).encrypt(value.toByteArray(), hostAad(host))
                ENCRYPTED_PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            }.getOrNull()

        private fun decrypt(
            value: String,
            host: String,
        ): String? {
            if (!value.startsWith(ENCRYPTED_PREFIX)) return null
            return runCatching {
                val encrypted = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
                val localAead = checkNotNull(aead)
                val decrypted =
                    try {
                        localAead.decrypt(encrypted, hostAad(host))
                    } catch (_: Exception) {
                        // Legacy fallback for rows written before AAD binding.
                        localAead.decrypt(encrypted, null)
                    }
                decrypted.decodeToString()
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
