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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_credentials",
)

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
class SecureCredentialStorage
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val KEYSET_NAME = "credentials_keyset"
            private const val PREFERENCE_FILE = "secure_credentials_prefs"
            private const val MASTER_KEY_URI = "android-keystore://credentials_master_key"

            private val KEY_USERNAME = stringPreferencesKey("encrypted_username")
            private val KEY_PASSWORD = stringPreferencesKey("encrypted_password")
        }

        private val dataStore = context.credentialsDataStore

        // Initialize Tink
        @Suppress("DEPRECATION") // getPrimitive is deprecated but still the correct API for Tink 1.15.0
        private val aead: Aead by lazy {
            AeadConfig.register()

            val keysetHandle =
                AndroidKeysetManager
                    .Builder()
                    .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
                    .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()
                    .keysetHandle

            keysetHandle.getPrimitive(Aead::class.java)
        }

        /**
         * Save user credentials securely.
         * Uses Tink for encryption before storing in DataStore.
         */
        suspend fun saveCredentials(credentials: UserCredentials) {
            // Encrypt credentials
            val encryptedUsername = encrypt(credentials.username)
            val encryptedPassword = encrypt(credentials.password)

            // Store in DataStore
            dataStore.edit { prefs ->
                prefs[KEY_USERNAME] = encryptedUsername
                prefs[KEY_PASSWORD] = encryptedPassword
            }
        }

        /**
         * Retrieve stored credentials.
         * @return UserCredentials or null if not found.
         */
        suspend fun getCredentials(): UserCredentials? {
            val prefs = dataStore.data.first()

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
                    null
                }
            } catch (e: Exception) {
                // Decryption failed
                null
            }
        }

        /**
         * Clear stored credentials.
         */
        suspend fun clearCredentials() {
            dataStore.edit { prefs ->
                prefs.remove(KEY_USERNAME)
                prefs.remove(KEY_PASSWORD)
            }
        }

        /**
         * Encrypt string using Tink AEAD.
         */
        private fun encrypt(plaintext: String): String {
            val encrypted = aead.encrypt(plaintext.toByteArray(), null)
            return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
        }

        /**
         * Decrypt string using Tink AEAD.
         */
        private fun decrypt(ciphertext: String): String {
            val encrypted = android.util.Base64.decode(ciphertext, android.util.Base64.NO_WRAP)
            val decrypted = aead.decrypt(encrypted, null)
            return String(decrypted)
        }
    }
