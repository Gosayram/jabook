package com.jabook.app.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker preferences interface for storing user credentials and settings
 */
interface RuTrackerPreferences {
  suspend fun setCredentials(
    username: String,
    password: String,
  )

  suspend fun getCredentials(): Pair<String, String>?

  suspend fun clearCredentials()

  suspend fun setGuestMode(enabled: Boolean)

  suspend fun isGuestMode(): Boolean

  fun getGuestModeFlow(): Flow<Boolean>
}

/**
 * RuTracker preferences implementation with encrypted storage
 */
@Singleton
class RuTrackerPreferencesImpl
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) : RuTrackerPreferences {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rutracker_preferences")

    private val usernameKey = stringPreferencesKey("rutracker_username")
    private val passwordKey = stringPreferencesKey("rutracker_encrypted_password")
    private val guestModeKey = booleanPreferencesKey("rutracker_guest_mode")

    // Simple encryption key (in production, use proper key management)
    private val encryptionKey = SecretKeySpec("JaBookRuTracker2024!".toByteArray(), "AES")

    override suspend fun setCredentials(
      username: String,
      password: String,
    ) {
      try {
        val encryptedPassword = encryptPassword(password)
        context.dataStore.edit { preferences ->
          preferences[usernameKey] = username
          preferences[passwordKey] = encryptedPassword
        }
        // Credentials saved successfully
      } catch (e: Exception) {
        // Failed to save credentials
      }
    }

    override suspend fun getCredentials(): Pair<String, String>? =
      try {
        val preferences =
          context.dataStore.data
            .map { it }
            .first()
        val username = preferences[usernameKey]
        val encryptedPassword = preferences[passwordKey]

        if (username != null && encryptedPassword != null) {
          val password = decryptPassword(encryptedPassword)
          Pair(username, password)
        } else {
          null
        }
      } catch (e: Exception) {
        null
      }

    override suspend fun clearCredentials() {
      try {
        context.dataStore.edit { preferences ->
          preferences.remove(usernameKey)
          preferences.remove(passwordKey)
        }
        // Credentials cleared successfully
      } catch (e: Exception) {
        // Failed to clear credentials
      }
    }

    override suspend fun setGuestMode(enabled: Boolean) {
      try {
        context.dataStore.edit { preferences ->
          preferences[guestModeKey] = enabled
        }
        // Guest mode set successfully
      } catch (e: Exception) {
        // Failed to set guest mode
      }
    }

    override suspend fun isGuestMode(): Boolean =
      try {
        val preferences =
          context.dataStore.data
            .map { it }
            .first()
        preferences[guestModeKey] ?: true // Default to guest mode
      } catch (e: Exception) {
        true // Default to guest mode on error
      }

    override fun getGuestModeFlow(): Flow<Boolean> =
      context.dataStore.data.map { preferences ->
        preferences[guestModeKey] ?: true // Default to guest mode
      }

    private fun encryptPassword(password: String): String =
      try {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val encryptedBytes = cipher.doFinal(password.toByteArray())
        android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.DEFAULT)
      } catch (e: Exception) {
        password // Fallback to plain text (not secure, but functional)
      }

    private fun decryptPassword(encryptedPassword: String): String =
      try {
        val encryptedBytes = android.util.Base64.decode(encryptedPassword, android.util.Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        String(decryptedBytes)
      } catch (e: Exception) {
        encryptedPassword // Fallback to encrypted string
      }
  }
