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

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import com.jabook.app.jabook.crash.CrashDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TELEMETRY_TAG = "secure_credentials"

internal object SecureCredentialStorageFactories {
    @VisibleForTesting
    internal var dataStoreFactoryOverride: ((Context) -> DataStore<Preferences>)? = null

    @VisibleForTesting
    internal var aeadFactoryOverride: ((Context) -> Aead)? = null

    internal fun dataStore(context: Context): DataStore<Preferences> =
        dataStoreFactoryOverride?.invoke(context) ?: createCredentialsDataStore(context)

    internal fun aead(context: Context): Aead = aeadFactoryOverride?.invoke(context) ?: createDefaultAead(context)
}

internal fun secureCredentialsCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { corruption ->
        reportSecureStorageNonFatal(
            stage = "datastore_corruption",
            throwable = corruption,
        )
        emptyPreferences()
    }

private fun createCredentialsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = secureCredentialsCorruptionHandler(),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("secure_credentials") },
    )

private fun createDefaultAead(context: Context): Aead {
    AeadConfig.register()

    val keysetHandle =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, SecureCredentialStorage.KEYSET_NAME, SecureCredentialStorage.PREFERENCE_FILE)
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri(SecureCredentialStorage.MASTER_KEY_URI)
            .build()
            .keysetHandle

    @Suppress("DEPRECATION")
    return keysetHandle.getPrimitive(Aead::class.java)
}

private fun reportSecureStorageNonFatal(
    stage: String,
    throwable: Throwable,
) {
    CrashDiagnostics.reportNonFatal(
        tag = "${TELEMETRY_TAG}_$stage",
        throwable = throwable,
        attributes =
            mapOf(
                "component" to "SecureCredentialStorage",
                "stage" to stage,
            ),
    )
}

/**
 * Securely stores user credentials using DataStore + Tink encryption.
 *
 * Replaces deprecated EncryptedSharedPreferences with modern approach:
 * - DataStore for async, type-safe storage
 * - Tink for industry-standard encryption
 * - Better performance (no blocking I/O)
 * - Coroutine/Flow based
 */
@Singleton
public class SecureCredentialStorage
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        public companion object {
            internal const val KEYSET_NAME = "credentials_keyset"
            internal const val PREFERENCE_FILE = "secure_credentials_prefs"
            internal const val MASTER_KEY_URI = "android-keystore://credentials_master_key"

            private val KEY_USERNAME = stringPreferencesKey("encrypted_username")
            private val KEY_PASSWORD = stringPreferencesKey("encrypted_password")
        }

        private val dataStore: DataStore<Preferences> by lazy { SecureCredentialStorageFactories.dataStore(context) }
        private val aeadOrNull: Aead? by lazy {
            runCatching { SecureCredentialStorageFactories.aead(context) }
                .onFailure {
                    reportSecureStorageNonFatal(
                        stage = "keyset_init_failed",
                        throwable = it,
                    )
                }.getOrNull()
        }

        /**
         * Save user credentials securely.
         * Uses Tink for encryption before storing in DataStore.
         */
        public suspend fun saveCredentials(credentials: UserCredentials) {
            val encryptedUsername = encrypt(credentials.username) ?: return
            val encryptedPassword = encrypt(credentials.password) ?: return

            // Store in DataStore
            runCatching {
                dataStore.edit { prefs ->
                    prefs[KEY_USERNAME] = encryptedUsername
                    prefs[KEY_PASSWORD] = encryptedPassword
                }
            }.onFailure {
                reportSecureStorageNonFatal(
                    stage = "save_failed",
                    throwable = it,
                )
            }
        }

        /**
         * Retrieve stored credentials.
         * @return UserCredentials or null if not found.
         */
        public suspend fun getCredentials(): UserCredentials? {
            val prefs =
                try {
                    dataStore.data.first()
                } catch (error: CorruptionException) {
                    reportSecureStorageNonFatal(
                        stage = "datastore_read_corruption",
                        throwable = error,
                    )
                    return null
                } catch (error: Exception) {
                    reportSecureStorageNonFatal(
                        stage = "datastore_read_failed",
                        throwable = error,
                    )
                    return null
                }

            val encryptedUsername = prefs[KEY_USERNAME]
            val encryptedPassword = prefs[KEY_PASSWORD]

            if (encryptedUsername == null || encryptedPassword == null) {
                return null
            }

            return try {
                val username = decrypt(encryptedUsername)
                val password = decrypt(encryptedPassword)

                if (username.isNotBlank() && password.isNotBlank()) {
                    UserCredentials(username, password)
                } else {
                    clearCredentialsSafely()
                    null
                }
            } catch (e: Exception) {
                reportSecureStorageNonFatal(
                    stage = "decrypt_failed",
                    throwable = e,
                )
                clearCredentialsSafely()
                null
            }
        }

        /**
         * Clear stored credentials.
         */
        public suspend fun clearCredentials() {
            clearCredentialsSafely()
        }

        @VisibleForTesting
        internal suspend fun replaceEncryptedCredentialsForTesting(
            encryptedUsername: String,
            encryptedPassword: String,
        ) {
            dataStore.edit { prefs ->
                prefs[KEY_USERNAME] = encryptedUsername
                prefs[KEY_PASSWORD] = encryptedPassword
            }
        }

        @VisibleForTesting
        internal suspend fun hasEncryptedCredentialsForTesting(): Boolean {
            val prefs = dataStore.data.first()
            return prefs[KEY_USERNAME] != null || prefs[KEY_PASSWORD] != null
        }

        private suspend fun clearCredentialsSafely() {
            runCatching {
                dataStore.edit { prefs ->
                    prefs.remove(KEY_USERNAME)
                    prefs.remove(KEY_PASSWORD)
                }
            }.onFailure {
                reportSecureStorageNonFatal(
                    stage = "clear_failed",
                    throwable = it,
                )
            }
        }

        /**
         * Encrypt string using Tink AEAD.
         */
        private fun encrypt(plaintext: String): String? {
            val localAead = aeadOrNull ?: return null
            return runCatching {
                val encrypted = localAead.encrypt(plaintext.toByteArray(), null)
                android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
            }.onFailure {
                reportSecureStorageNonFatal(
                    stage = "encrypt_failed",
                    throwable = it,
                )
            }.getOrNull()
        }

        /**
         * Decrypt string using Tink AEAD.
         */
        private fun decrypt(ciphertext: String): String {
            val localAead = aeadOrNull ?: throw IllegalStateException("AEAD is not initialized")
            val encrypted = android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)
            val decrypted = localAead.decrypt(encrypted, null)
            return String(decrypted)
        }
    }
