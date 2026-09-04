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

import com.jabook.app.jabook.BuildConfig
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirror health status returned by [MirrorManager.checkMirrorHealth].
 */
public sealed class MirrorHealth {
    /** Mirror responds with 2xx — direct API access works. */
    public data object Healthy : MirrorHealth()

    /** Mirror returns 403/503 with Cloudflare challenge page — WebView only. */
    public data object CloudflareProtected : MirrorHealth()

    /** DNS resolution or TLS handshake failed — mirror is dead. */
    public data object Dead : MirrorHealth()

    /** Timeout or unknown error — might be temporary. */
    public data object Unknown : MirrorHealth()
}

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
             * Default list of RuTracker mirrors, supplied at build time from .env
             * (`RUTRACKER_DEFAULT_MIRRORS`). Never hardcoded in source.
             */
            public val DEFAULT_MIRRORS: List<String> =
                BuildConfig.RUTRACKER_DEFAULT_MIRRORS
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

            private val DEFAULT_MIRROR: String = DEFAULT_MIRRORS.firstOrNull().orEmpty()

            // Anti-flap tuning (mirrors share one backend: 4xx is an application
            // response, not a mirror failure, so switching must be conservative)
            private const val SWITCH_SUCCESS_GRACE_MS = 10_000L
            private const val SWITCH_FAILURE_BACKOFF_MS = 60_000L
        }

        private val scope =
            CoroutineScope(
                SupervisorJob() + NetworkRuntimePolicy.ioDispatcher + loggingCoroutineExceptionHandler("MirrorManager"),
            )

        private val circuitFailureCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val circuitOpenSince = ConcurrentHashMap<String, Long>()
        private val halfOpenProbes = ConcurrentHashMap.newKeySet<String>()
        private val circuitResetTimeoutMs = 60_000L
        private val circuitFailureThreshold = 3

        // Single-flight guard: prevents N parallel interceptor threads from
        // running the (expensive) health-check switch dance at once.
        private val switchMutex = Mutex()

        // Cooldowns to stop mirror flapping (mirrors are one backend — a 4xx
        // won't be fixed by switching, so rapid re-switching is pure churn):
        //  - grace after a successful switch before switching again
        //  - backoff after a failed switch attempt (no mirror worked)
        private val lastSuccessfulSwitchMs = AtomicLong(0L)
        private val lastFailedSwitchMs = AtomicLong(0L)

        // Dedicated health-check client built in NetworkModule (short call/connect/read
        // timeouts, DoH, cert pinning). No re-wrapping here: a newBuilder() layer would
        // stack a second timeout configuration over the purpose-built one.
        private val healthCheckClient: OkHttpClient = okHttpClient

        private val _currentMirror = MutableStateFlow(DEFAULT_MIRROR)

        /**
         * Current active mirror domain (reactive).
         *
         * Example: "<mirror-domain>"
         */
        public val currentMirror: StateFlow<String> = _currentMirror.asStateFlow()

        private val _availableMirrors = MutableStateFlow<List<String>>(DEFAULT_MIRRORS)

        /**
         * List of all available mirrors (default + custom).
         */
        public val availableMirrors: StateFlow<List<String>> = _availableMirrors.asStateFlow()

        // Cached auto-switch flag, kept in sync by the settings collector.
        // Read synchronously by DynamicBaseUrlInterceptor to avoid runBlocking.
        // @Volatile: written on the settings collector (IO) thread, read from
        // OkHttp worker threads — plain field has no cross-thread visibility.
        @Volatile
        private var cachedAutoSwitchEnabled = false

        init {
            // Load saved settings on init
            scope.launch {
                settingsRepository.userPreferences.collect { prefs ->
                    val savedMirror = prefs.selectedMirror
                    if (savedMirror.isNotBlank() && savedMirror != _currentMirror.value) {
                        _currentMirror.value = savedMirror
                        logger.d { "Loaded mirror from settings: $savedMirror" }
                    }

                    // Merge default and custom mirrors, ensuring all defaults are always present
                    val customMirrors = prefs.customMirrorsList
                    val allMirrors = (DEFAULT_MIRRORS + customMirrors).distinct()
                    _availableMirrors.value = (DEFAULT_MIRRORS + allMirrors).distinct()

                    cachedAutoSwitchEnabled = prefs.autoSwitchMirror
                }
            }
        }

        /**
         * Set the current mirror.
         *
         * @param domain Mirror domain (e.g., "<mirror-domain>")
         */
        public suspend fun setMirror(domain: String) {
            if (domain.isBlank()) {
                logger.w { "Attempted to set blank mirror, ignoring" }
                return
            }

            val previousMirror = _currentMirror.value
            _currentMirror.value = domain
            settingsRepository.updateSelectedMirror(domain)
            // DNS lookup is blocking I/O — callers launch on Main (SettingsViewModel),
            // so without IO the UI freezes for seconds on flaky networks.
            val prefetch =
                withContext(NetworkRuntimePolicy.ioDispatcher) {
                    DnsPrefetchPolicy.prefetch(domain)
                }
            if (prefetch.success) {
                logger.d {
                    "DNS prefetch success for $domain: ${prefetch.addresses.size} addresses in ${prefetch.elapsedMs}ms"
                }
            } else {
                logger.d { "DNS prefetch skipped/failed for $domain: ${prefetch.error}" }
            }
            logger.i { "Mirror changed from $previousMirror to $domain (saved to settings)" }
        }

        /**
         * Check if a mirror is accessible, respecting Circuit Breaker state.
         *
         * @param domain Mirror domain to check
         * @return [MirrorHealth] indicating the mirror's accessibility
         */
        public suspend fun checkMirrorHealth(domain: String): MirrorHealth =
            withContext(NetworkRuntimePolicy.ioDispatcher) {
                if (isCircuitOpen(domain)) {
                    logger.d { "Circuit open for $domain, skipping health check" }
                    return@withContext MirrorHealth.Dead
                }

                try {
                    logger.d { "Checking health of mirror: $domain" }

                    val request =
                        Request
                            .Builder()
                            .url("https://$domain/forum/")
                            .header("User-Agent", "JaBook/${BuildConfig.VERSION_NAME}")
                            // GET, not HEAD: Cloudflare challenge pages carry no body on
                            // HEAD responses, so body-based detection would never fire.
                            .get()
                            .build()

                    val response = healthCheckClient.newCall(request).execute()
                    response.use {
                        val code = it.code
                        val body = runCatching { it.peekBody(8192).string() }.getOrDefault("")

                        // Cloudflare's explicit "this is a challenge" signal. cf-ray alone
                        // must NOT be used: every Cloudflare-proxied response carries it,
                        // including healthy ones, which would misclassify real origin 403s
                        // (expired session / user block) as "protected" and switch mirrors
                        // to a backend that behaves identically.
                        val cfMitigated =
                            it.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true
                        val isCfProtected =
                            cfMitigated ||
                                body.contains("Just a moment", ignoreCase = true) ||
                                body.contains("Checking your browser", ignoreCase = true) ||
                                body.contains("cf-browser-verification", ignoreCase = true) ||
                                body.contains("cf-chl", ignoreCase = true)

                        val health =
                            when {
                                code in 200..299 -> MirrorHealth.Healthy
                                (code == 403 || code == 503) && isCfProtected -> MirrorHealth.CloudflareProtected
                                code == 403 || code == 503 -> MirrorHealth.Dead
                                else -> MirrorHealth.Dead
                            }

                        logger.d { "Mirror $domain health: ${health::class.simpleName} ($code)" }

                        // Only count DNS/TLS/timeout failures for circuit breaker — CF-protected is not a failure
                        when (health) {
                            is MirrorHealth.Healthy -> recordCircuitSuccess(domain)
                            is MirrorHealth.CloudflareProtected -> recordCircuitSuccess(domain)
                            is MirrorHealth.Dead -> recordCircuitFailure(domain)
                            is MirrorHealth.Unknown -> recordCircuitFailure(domain)
                        }
                        health
                    }
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
                    recordCircuitFailure(domain)
                    MirrorHealth.Dead
                }
            }

        private fun isCircuitOpen(domain: String): Boolean {
            val openSince = circuitOpenSince[domain] ?: return false
            return if (System.currentTimeMillis() - openSince < circuitResetTimeoutMs) {
                true
            } else {
                circuitOpenSince.remove(domain) // half-open: allow one probe
                halfOpenProbes.add(domain)
                false
            }
        }

        private fun recordCircuitFailure(domain: String) {
            // A failed half-open probe means the mirror is still dead — re-open
            // immediately so dead mirrors don't get a full-timeout check on every
            // switch attempt (RouteSelector-style: don't re-select a failed route
            // while others remain).
            if (halfOpenProbes.remove(domain)) {
                circuitOpenSince[domain] = System.currentTimeMillis()
                logger.w { "Half-open probe failed for $domain, circuit re-opened" }
                return
            }

            val count = circuitFailureCounts.getOrPut(domain) { AtomicInteger(0) }.incrementAndGet()
            if (count >= circuitFailureThreshold) {
                circuitOpenSince[domain] = System.currentTimeMillis()
                circuitFailureCounts.remove(domain)
                logger.w { "Circuit opened for $domain after $count failures" }
            }
        }

        private fun recordCircuitSuccess(domain: String) {
            halfOpenProbes.remove(domain)
            circuitFailureCounts.remove(domain)
            circuitOpenSince.remove(domain)
        }

        /**
         * Switch to the next available working mirror.
         *
         * Checks mirrors in order until a healthy one is found.
         *
         * @return true if switched successfully, false if no mirrors are available
         */
        public suspend fun switchToNextMirror(): Boolean =
            switchMutex.withLock {
                val now = System.currentTimeMillis()

                // Anti-flap: mirrors share one backend, so rapid re-switching on 4xx
                // only ping-pongs between domains. If we just switched, stay put and
                // let the application layer surface the error instead.
                if (now - lastSuccessfulSwitchMs.get() < SWITCH_SUCCESS_GRACE_MS) {
                    logger.d { "Mirror switched recently, staying on ${_currentMirror.value} (grace period)" }
                    return@withLock false
                }

                // After a failed switch (no mirror worked), back off so dead mirrors
                // aren't re-probed (with full timeouts) on every single request.
                if (now - lastFailedSwitchMs.get() < SWITCH_FAILURE_BACKOFF_MS) {
                    logger.d { "Switch attempt on cooldown (backoff), keeping ${_currentMirror.value}" }
                    return@withLock false
                }

                syncStateFromPreferencesSnapshot()
                val currentDomain = _currentMirror.value
                val mirrors = _availableMirrors.value
                val currentIndex = mirrors.indexOf(currentDomain)

                logger.i { "Attempting to switch from $currentDomain to next mirror" }

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
                    logger.d { "Trying mirror: $mirror" }

                    val health = checkMirrorHealth(mirror)
                    if (health is MirrorHealth.Healthy || health is MirrorHealth.CloudflareProtected) {
                        val healthCheckDuration = System.currentTimeMillis() - healthCheckStart
                        logger.i {
                            "Mirror $mirror is ${health::class.simpleName} (health check: ${healthCheckDuration}ms), switching and saving to settings..."
                        }
                        setMirror(mirror) // This will save to settings via settingsRepository.updateSelectedMirror()
                        lastSuccessfulSwitchMs.set(now)
                        logger.i { "Successfully switched from $currentDomain to $mirror and saved to settings" }
                        return@withLock true
                    }
                }

                logger.e { "Failed to find any working mirror after trying ${availableMirrors.value.size} mirrors" }
                lastFailedSwitchMs.set(now)
                return@withLock false
            }

        /**
         * Add a custom mirror domain.
         *
         * @param domain Custom mirror domain (e.g., "<mirror-domain>")
         */

        /**
         * Add a custom mirror domain.
         *
         * The input is validated and sanitized via [MirrorDomainValidationPolicy]:
         * - Protocol prefixes, paths, ports, and fragments are stripped
         * - Local/private addresses are rejected
         * - Non-rutracker domains are accepted but logged as a warning
         *
         * @param domain Raw user input (e.g., "https://<mirror-domain>/forum/")
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
         * @return Current mirror domain (e.g., "<mirror-domain>")
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

            // Drop circuit-breaker state so a re-added mirror starts clean
            circuitFailureCounts.remove(domain)
            circuitOpenSince.remove(domain)
            halfOpenProbes.remove(domain)

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
         * Synchronous check for auto-switch — reads the in-memory cached flag
         * kept in sync by the settings collector. Use from OkHttp interceptors
         * where runBlocking would risk dispatcher starvation.
         */
        public fun isAutoSwitchEnabledSync(): Boolean = cachedAutoSwitchEnabled

        /**
         * Synchronous anti-flap check: true when a switch attempt is outside the
         * post-success grace period and the post-failure backoff window.
         *
         * Use from OkHttp interceptor threads to skip a doomed (blocking) switch
         * attempt before paying for it.
         */
        public fun canSwitchNowSync(): Boolean {
            val now = System.currentTimeMillis()
            return now - lastSuccessfulSwitchMs.get() >= SWITCH_SUCCESS_GRACE_MS &&
                now - lastFailedSwitchMs.get() >= SWITCH_FAILURE_BACKOFF_MS
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
