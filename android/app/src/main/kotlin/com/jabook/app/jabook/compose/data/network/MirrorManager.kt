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

package com.jabook.app.jabook.compose.data.network

import android.util.Log
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for RuTracker mirror domains.
 *
 * Handles:
 * - Current mirror selection
 * - Mirror health checks
 * - Automatic failover to working mirrors
 * - Persistence via SettingsRepository
 */
@Singleton
class MirrorManager
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val okHttpClient: OkHttpClient,
    ) {
        companion object {
            private const val TAG = "MirrorManager"

            /**
             * Default list of RuTracker mirrors.
             */
            val DEFAULT_MIRRORS =
                listOf(
                    "rutracker.org",
                    "rutracker.net",
                    "rutracker.me",
                )

            private const val DEFAULT_MIRROR = "rutracker.me"
            private const val HEALTH_CHECK_TIMEOUT_SECONDS = 5L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val _currentMirror = MutableStateFlow(DEFAULT_MIRROR)

        /**
         * Current active mirror domain (reactive).
         *
         * Example: "rutracker.org"
         */
        val currentMirror: StateFlow<String> = _currentMirror.asStateFlow()

        private val _availableMirrors = MutableStateFlow<List<String>>(DEFAULT_MIRRORS)

        /**
         * List of all available mirrors (default + custom).
         */
        val availableMirrors: StateFlow<List<String>> = _availableMirrors.asStateFlow()

        init {
            // Load saved settings on init
            scope.launch {
                settingsRepository.userPreferences.collect { prefs ->
                    val savedMirror = prefs.selectedMirror
                    if (savedMirror.isNotBlank() && savedMirror != _currentMirror.value) {
                        _currentMirror.value = savedMirror
                        Log.d(TAG, "Loaded mirror from settings: $savedMirror")
                    }

                    // Merge default and custom mirrors
                    val customMirrors = prefs.customMirrorsList
                    val allMirrors = (DEFAULT_MIRRORS + customMirrors).distinct()
                    _availableMirrors.value = allMirrors
                }
            }
        }

        /**
         * Set the current mirror.
         *
         * @param domain Mirror domain (e.g., "rutracker.org")
         */
        suspend fun setMirror(domain: String) {
            if (domain.isBlank()) {
                Log.w(TAG, "Attempted to set blank mirror, ignoring")
                return
            }

            _currentMirror.value = domain
            settingsRepository.updateSelectedMirror(domain)
            Log.i(TAG, "Mirror set to: $domain")
        }

        /**
         * Check if a mirror is accessible.
         *
         * @param domain Mirror domain to check
         * @return true if mirror responds within timeout, false otherwise
         */
        suspend fun checkMirrorHealth(domain: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Checking health of mirror: $domain")

                    // Create a dedicated client with short timeout for health checks
                    val healthCheckClient =
                        okHttpClient
                            .newBuilder()
                            .connectTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .build()

                    val request =
                        Request
                            .Builder()
                            .url("https://$domain/forum/")
                            .head() // HEAD request for faster response
                            .build()

                    val response = healthCheckClient.newCall(request).execute()
                    val isHealthy = response.isSuccessful

                    Log.d(TAG, "Mirror $domain health: ${if (isHealthy) "OK" else "FAILED"} (${response.code})")
                    response.close()

                    isHealthy
                } catch (e: Exception) {
                    Log.w(TAG, "Mirror $domain health check failed: ${e.message}")
                    false
                }
            }

        /**
         * Switch to the next available working mirror.
         *
         * Checks mirrors in order until a healthy one is found.
         *
         * @return true if switched successfully, false if no mirrors are available
         */
        suspend fun switchToNextMirror(): Boolean {
            val currentDomain = _currentMirror.value
            val mirrors = _availableMirrors.value
            val currentIndex = mirrors.indexOf(currentDomain)

            Log.d(TAG, "Attempting to switch from $currentDomain to next mirror")

            // Try all mirrors starting from next one
            val mirrorsToTry =
                if (currentIndex >= 0) {
                    // Start from next mirror, wrap around
                    mirrors.drop(currentIndex + 1) + mirrors.take(currentIndex + 1)
                } else {
                    mirrors
                }

            for (mirror in mirrorsToTry) {
                if (mirror == currentDomain) continue // Skip current

                Log.d(TAG, "Trying mirror: $mirror")
                if (checkMirrorHealth(mirror)) {
                    setMirror(mirror)
                    Log.i(TAG, "Successfully switched to mirror: $mirror")
                    return true
                }
            }

            Log.w(TAG, "Failed to find any working mirror")
            return false
        }

        /**
         * Add a custom mirror domain.
         *
         * @param domain Custom mirror domain (e.g., "rutracker.nl")
         */
        suspend fun addCustomMirror(domain: String) {
            if (domain.isBlank() || domain in _availableMirrors.value) {
                Log.w(TAG, "Custom mirror already exists or is blank: $domain")
                return
            }

            settingsRepository.addCustomMirror(domain)
            Log.i(TAG, "Added custom mirror: $domain")
        }

        /**
         * Remove a custom mirror domain.
         *
         * @param domain Mirror domain to remove
         */
        suspend fun removeCustomMirror(domain: String) {
            if (domain in DEFAULT_MIRRORS) {
                Log.w(TAG, "Cannot remove default mirror: $domain")
                return
            }

            settingsRepository.removeCustomMirror(domain)

            // If current mirror is being removed, switch to default
            if (_currentMirror.value == domain) {
                setMirror(DEFAULT_MIRROR)
                Log.i(TAG, "Removed current mirror, switched to default")
            }

            Log.i(TAG, "Removed custom mirror: $domain")
        }

        /**
         * Check if auto-switch is enabled in settings.
         */
        suspend fun isAutoSwitchEnabled(): Boolean {
            val prefs = settingsRepository.userPreferences.first()
            return prefs.autoSwitchMirror
        }
    }
