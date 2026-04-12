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

package com.jabook.app.jabook.compose.data.network

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.core.network.NetworkRuntimePolicy
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
public class MirrorManager
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val okHttpClient: OkHttpClient,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("MirrorManager")

        public companion object {
            /**
             * Default list of RuTracker mirrors.
             */
            public val DEFAULT_MIRRORS: List<String> =
                listOf(
                    "rutracker.org",
                    "rutracker.net",
                    "rutracker.me",
                )

            private const val DEFAULT_MIRROR = "rutracker.me"
        }

        private val scope =
            CoroutineScope(
                SupervisorJob() + NetworkRuntimePolicy.ioDispatcher + loggingCoroutineExceptionHandler("MirrorManager"),
            )

        private val _currentMirror = MutableStateFlow(DEFAULT_MIRROR)

        /**
         * Current active mirror domain (reactive).
         *
         * Example: "rutracker.org"
         */
        public val currentMirror: StateFlow<String> = _currentMirror.asStateFlow()

        private val _availableMirrors = MutableStateFlow<List<String>>(DEFAULT_MIRRORS)

        /**
         * List of all available mirrors (default + custom).
         */
        public val availableMirrors: StateFlow<List<String>> = _availableMirrors.asStateFlow()

        init {
            // Load saved settings on init
            scope.launch {
                settingsRepository.userPreferences.collect { prefs ->
                    val savedMirror = prefs.selectedMirror
                    if (savedMirror.isNotBlank() && savedMirror != _currentMirror.value) {
                        _currentMirror.value = savedMirror
                        logger.d { "Loaded mirror from settings: $savedMirror" }
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
        public suspend fun setMirror(domain: String) {
            if (domain.isBlank()) {
                logger.w { "Attempted to set blank mirror, ignoring" }
                return
            }

            val previousMirror = _currentMirror.value
            _currentMirror.value = domain
            settingsRepository.updateSelectedMirror(domain)
            logger.i { "Mirror changed from $previousMirror to $domain (saved to settings)" }
        }

        /**
         * Check if a mirror is accessible.
         *
         * @param domain Mirror domain to check
         * @return true if mirror responds within timeout, false otherwise
         */
        public suspend fun checkMirrorHealth(domain: String): Boolean =
            withContext(NetworkRuntimePolicy.ioDispatcher) {
                try {
                    logger.d { "Checking health of mirror: $domain" }

                    val request =
                        Request
                            .Builder()
                            .url("https://$domain/forum/")
                            .head() // HEAD request for faster response
                            .build()

                    val response = okHttpClient.newCall(request).execute()
                    val isHealthy: Boolean = response.isSuccessful
                    logger.d { "Mirror $domain health: ${if (isHealthy) "OK" else "FAILED"} (${response.code})" }
                    response.close()

                    isHealthy
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Individual mirror unavailable is normal, not a warning
                    logger.i { "Mirror $domain unavailable (timeout or unreachable): ${e.message}" }
                    CrashDiagnostics.reportNonFatal(
                        tag = "mirror_health_check_failed",
                        throwable = e,
                        attributes = mapOf("mirror_domain" to domain),
                    )
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
        public suspend fun switchToNextMirror(): Boolean {
            syncStateFromPreferencesSnapshot()
            val currentDomain = _currentMirror.value
            val mirrors = _availableMirrors.value
            val currentIndex = mirrors.indexOf(currentDomain)

            logger.i { "🔄 Attempting to switch from $currentDomain to next mirror" }

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

                val healthCheckStart = System.currentTimeMillis()
                logger.d { "🔍 Trying mirror: $mirror" }

                if (checkMirrorHealth(mirror)) {
                    val healthCheckDuration = System.currentTimeMillis() - healthCheckStart
                    logger.i {
                        "✅ Mirror $mirror is healthy (health check: ${healthCheckDuration}ms), switching and saving to settings..."
                    }
                    setMirror(mirror) // This will save to settings via settingsRepository.updateSelectedMirror()
                    logger.i { "✅ Successfully switched from $currentDomain to $mirror and saved to settings" }
                    return true
                }
            }

            logger.e { "❌ Failed to find any working mirror after trying ${availableMirrors.value.size} mirrors" }
            return false
        }

        /**
         * Add a custom mirror domain.
         *
         * @param domain Custom mirror domain (e.g., "rutracker.nl")
         */

        /**
         * Add a custom mirror domain.
         *
         * The input is validated and sanitized via [MirrorDomainValidationPolicy]:
         * - Protocol prefixes, paths, ports, and fragments are stripped
         * - Local/private addresses are rejected
         * - Non-rutracker domains are accepted but logged as a warning
         *
         * @param domain Raw user input (e.g., "https://rutracker.nl/forum/")
         * @return [MirrorDomainValidationPolicy.ValidationResult] indicating success or rejection
         */
        public suspend fun addCustomMirror(domain: String): MirrorDomainValidationPolicy.ValidationResult {
            val validation = MirrorDomainValidationPolicy.validate(domain)

            val sanitized = validation.sanitizedDomain
            if (sanitized == null) {
                logger.w { "Custom mirror rejected: ${validation.rejectionReason}" }
                return validation
            }

            if (sanitized in _availableMirrors.value) {
                logger.w { "Custom mirror already exists: $sanitized" }
                return MirrorDomainValidationPolicy.ValidationResult(
                    sanitizedDomain = null,
                    isWarning = false,
                    rejectionReason = "Mirror already exists: $sanitized",
                )
            }

            if (validation.isWarning) {
                logger.w { "Custom mirror does not look like a RuTracker domain: $sanitized" }
            }

            settingsRepository.addCustomMirror(sanitized)
            logger.i { "Added custom mirror: $sanitized" }
            return validation
        }

        /**
         * Get current mirror base URL (https://domain).
         *
         * @return Base URL with current mirror domain
         */
        public fun getBaseUrl(): String = "https://${_currentMirror.value}"

        /**
         * Get current mirror domain synchronously.
         *
         * @return Current mirror domain (e.g., "rutracker.org")
         */
        public fun getCurrentMirrorDomain(): String = _currentMirror.value

        /**
         * Remove a custom mirror domain.
         *
         * @param domain Mirror domain to remove
         */
        public suspend fun removeCustomMirror(domain: String) {
            if (domain in DEFAULT_MIRRORS) {
                logger.w { "Cannot remove default mirror: $domain" }
                return
            }

            settingsRepository.removeCustomMirror(domain)

            // If current mirror is being removed, switch to default
            if (_currentMirror.value == domain) {
                setMirror(DEFAULT_MIRROR)
                logger.i { "Removed current mirror, switched to default" }
            }

            logger.i { "Removed custom mirror: $domain" }
        }

        /**
         * Check if auto-switch is enabled in settings.
         */
        public suspend fun isAutoSwitchEnabled(): Boolean {
            val prefs = settingsRepository.userPreferences.first()
            return prefs.autoSwitchMirror
        }

        /**
         * Synchronize in-memory state with latest persisted preferences snapshot.
         *
         * This avoids races where public suspend APIs are called before init collector
         * has observed the first settings emission.
         */
        private suspend fun syncStateFromPreferencesSnapshot() {
            val prefs = settingsRepository.userPreferences.first()
            val savedMirror = prefs.selectedMirror
            if (savedMirror.isNotBlank() && savedMirror != _currentMirror.value) {
                _currentMirror.value = savedMirror
            }

            val allMirrors = (DEFAULT_MIRRORS + prefs.customMirrorsList).distinct()
            if (allMirrors != _availableMirrors.value) {
                _availableMirrors.value = allMirrors
            }
        }
    }
