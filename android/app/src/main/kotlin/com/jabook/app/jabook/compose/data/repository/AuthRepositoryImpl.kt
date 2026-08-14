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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.auth.CookiePersistenceManager
import com.jabook.app.jabook.compose.data.auth.RutrackerAuthService
import com.jabook.app.jabook.compose.data.auth.SecureCredentialStorage
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.network.PersistentCookieJar
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.CaptchaData
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.repository.CaptchaRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AuthRepository.
 */
@Singleton
public class AuthRepositoryImpl
    @Inject
    constructor(
        private val authService: RutrackerAuthService,
        private val secureStorage: SecureCredentialStorage,
        private val cookieJar: PersistentCookieJar,
        private val mirrorManager: MirrorManager,
        private val cookiePersistence: CookiePersistenceManager,
        private val loggerFactory: LoggerFactory,
    ) : AuthRepository {
        private val logger = loggerFactory.get("AuthRepository")
        private val _authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)
        override val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

        private val scope =
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
            )

        /**
         * Mutex to prevent concurrent login attempts.
         * Based on Flutter's _isLoggingIn pattern.
         */
        private val loginMutex = Mutex()

        /**
         * Current RuTracker URL based on selected mirror.
         */
        private val rutrackerUrl: okhttp3.HttpUrl
            get() = "https://${mirrorManager.currentMirror.value}".toHttpUrl()

        init {
            scope.launch {
                checkAuthStatus()
            }
        }

        private suspend fun checkAuthStatus() {
            val cookies = cookieJar.loadForRequest(rutrackerUrl)
            val hasSession = cookies.any { it.name == "bb_session" }

            if (hasSession) {
                // Validate with server (strict check) with timeout handling
                val isValid =
                    try {
                        authService.validateAuth()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        logger.e({ "Auth validation timeout - provider may be blocking" }, e)
                        false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e({ "Auth validation error" }, e)
                        false
                    }

                if (isValid) {
                    val stored = secureStorage.getCredentials()
                    // WebView login deliberately never exposes or stores the entered password.
                    // A server-validated session is sufficient to be authenticated.
                    _authStatus.value = AuthStatus.Authenticated(stored?.username?.takeIf { it.isNotBlank() } ?: "User")
                } else {
                    // Cookies present but invalid (expired or guest mode)
                    logger.d { "Session expired or invalid, attempting re-login if credentials exist" }

                    // Clear invalid cookies but DO NOT clear stored credentials (logout)
                    cookieJar.clear()
                    _authStatus.value = AuthStatus.Unauthenticated

                    // Attempt automatic re-login if we have credentials
                    val stored = secureStorage.getCredentials()
                    if (stored != null) {
                        logger.d { "Found stored credentials, attempting auto-relogin" }
                        try {
                            login(stored)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.e({ "Auto-relogin failed" }, e)
                        }
                    }
                }
            } else {
                // No session cookie, check if we should auto-login
                val stored = secureStorage.getCredentials()
                if (stored != null && _authStatus.value !is AuthStatus.Authenticated) {
                    logger.d { "No session but found credentials, attempting auto-login" }
                    try {
                        login(stored)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e({ "Auto-login failed" }, e)
                        _authStatus.value = AuthStatus.Unauthenticated
                    }
                } else {
                    _authStatus.value = AuthStatus.Unauthenticated
                }
            }
        }

        override suspend fun login(credentials: UserCredentials): Result<Boolean> {
            useTrustedAuthenticationMirror()
            // Check if login is already in progress
            if (!loginMutex.tryLock()) {
                logger.w { "Login already in progress, ignoring duplicate request" }
                return Result.failure(IllegalStateException("Login already in progress"))
            }

            return try {
                try {
                    val operationId: String = "login_${System.currentTimeMillis()}"
                    logger.d { "[$operationId] Login attempt started" }

                    when (val result = authService.login(credentials)) {
                        is RutrackerAuthService.AuthResult.Success -> {
                            // Validate authentication to ensure it actually worked
                            val isValid =
                                try {
                                    authService.validateAuth(operationId)
                                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                    logger.e({ "[$operationId] Validation timeout - provider may be blocking" }, e)
                                    _authStatus.value =
                                        AuthStatus.Error(
                                            "Таймаут при проверке авторизации. Возможно, провайдер блокирует соединение.",
                                        )
                                    return Result.failure(Exception("Authentication validation timeout"))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.e({ "[$operationId] Validation error" }, e)
                                    false
                                }

                            if (isValid) {
                                // Persist cookies to all layers (Database, WebView, SecureStorage)
                                try {
                                    cookiePersistence.persistCookiesMultiStage(rutrackerUrl.toString())
                                    logger.d { "[$operationId] Cookies persisted to all layers" }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.e({ "[$operationId] Cookie persistence failed" }, e)
                                }

                                _authStatus.value = AuthStatus.Authenticated(credentials.username)
                                logger.i { "[$operationId] Login successful and validated" }
                                Result.success(true)
                            } else {
                                // Login appeared to succeed but validation failed
                                logger.w { "[$operationId] Login succeeded but validation failed" }
                                _authStatus.value =
                                    AuthStatus.Error("Проверка авторизации не прошла. Попробуйте еще раз.")
                                Result.failure(Exception("Authentication validation failed"))
                            }
                        }
                        is RutrackerAuthService.AuthResult.Error -> {
                            logger.w { "[$operationId] Login failed: ${result.message}" }
                            _authStatus.value = AuthStatus.Error(result.message)
                            Result.failure(Exception(result.message))
                        }
                        is RutrackerAuthService.AuthResult.Captcha -> {
                            logger.d { "[$operationId] Captcha required" }
                            // This login method doesn't support captcha return
                            // In a real app we might want a specific error type or flow
                            Result.failure(CaptchaRequiredException(result.data))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Login exception" }, e)
                    _authStatus.value = AuthStatus.Error(e.message ?: "Unknown error")
                    Result.failure(e)
                }
            } finally {
                loginMutex.unlock()
            }
        }

        override suspend fun loginWithCaptcha(
            credentials: UserCredentials,
            captchaCode: String,
            captchaData: CaptchaData,
        ): Result<Boolean> {
            useTrustedAuthenticationMirror()
            // Check if login is already in progress
            if (!loginMutex.tryLock()) {
                logger.w { "Captcha login already in progress, ignoring duplicate request" }
                return Result.failure(IllegalStateException("Login already in progress"))
            }

            return try {
                try {
                    val operationId: String = "login_captcha_${System.currentTimeMillis()}"
                    logger.d { "[$operationId] Captcha login attempt started" }

                    when (val result = authService.login(credentials, captchaCode, captchaData)) {
                        is RutrackerAuthService.AuthResult.Success -> {
                            // Validate authentication to ensure it actually worked
                            val isValid = authService.validateAuth(operationId)

                            if (isValid) {
                                // Persist cookies to all layers
                                try {
                                    cookiePersistence.persistCookiesMultiStage(rutrackerUrl.toString())
                                    logger.d { "[$operationId] Cookies persisted to all layers" }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logger.e({ "[$operationId] Cookie persistence failed" }, e)
                                }

                                _authStatus.value = AuthStatus.Authenticated(credentials.username)
                                logger.i { "[$operationId] Captcha login successful and validated" }
                                Result.success(true)
                            } else {
                                logger.w { "[$operationId] Captcha login succeeded but validation failed" }
                                _authStatus.value = AuthStatus.Error("Login validation failed")
                                Result.failure(Exception("Authentication validation failed"))
                            }
                        }
                        is RutrackerAuthService.AuthResult.Error -> {
                            logger.w { "[$operationId] Captcha login failed: ${result.message}" }
                            _authStatus.value = AuthStatus.Error(result.message)
                            Result.failure(Exception(result.message))
                        }
                        is RutrackerAuthService.AuthResult.Captcha -> {
                            logger.d { "[$operationId] Captcha required again" }
                            // Captcha failed or required again
                            Result.failure(CaptchaRequiredException(result.data))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Captcha login exception" }, e)
                    Result.failure(e)
                }
            } finally {
                loginMutex.unlock()
            }
        }

        override suspend fun logout() {
            loginMutex.withLock {
                cookieJar.clear()
                secureStorage.clearCredentials()
                cookiePersistence.clearWebViewSession(rutrackerUrl.toString())
                _authStatus.value = AuthStatus.Unauthenticated
            }
        }

        private suspend fun useTrustedAuthenticationMirror() {
            if (mirrorManager.currentMirror.value !in MirrorManager.DEFAULT_MIRRORS) {
                mirrorManager.setMirror(MirrorManager.DEFAULT_MIRRORS.first())
            }
        }

        override suspend fun isLoggedIn(): Boolean {
            checkAuthStatus()
            return _authStatus.value is AuthStatus.Authenticated
        }

        override suspend fun saveCredentials(credentials: UserCredentials) {
            secureStorage.saveCredentials(credentials)
            // If we just saved credentials and are authenticated, likely we need to update status username if it was "User"
            if (_authStatus.value is AuthStatus.Authenticated) {
                _authStatus.value = AuthStatus.Authenticated(credentials.username)
            }
        }

        override suspend fun getStoredCredentials(): UserCredentials? = secureStorage.getCredentials()

        override suspend fun syncCookiesFromWebView(url: String?) {
            // Use actual WebView URL if provided, fallback to base mirror URL
            val primaryUrl = url ?: rutrackerUrl.toString()
            val baseMirrorUrl = rutrackerUrl.toString()

            // Try with actual WebView URL first
            cookiePersistence.syncCookiesFromWebView(primaryUrl)

            // Also try base mirror URL as fallback (cookies may have been set before redirect)
            if (primaryUrl != baseMirrorUrl) {
                cookiePersistence.syncCookiesFromWebView(baseMirrorUrl)
            }

            // Check bb_session presence directly — no HTTP validation (Cloudflare blocks it)
            val cookies = cookieJar.loadForRequest(rutrackerUrl)
            val hasSession = cookies.any { it.name == "bb_session" }
            if (hasSession) {
                val stored = secureStorage.getCredentials()
                var username = stored?.username?.takeIf { it.isNotBlank() } ?: "User"

                // Fetch real username from forum page (best-effort)
                val fetched =
                    try {
                        authService.fetchUsername()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.d { "Username fetch failed during cookie sync: ${e.message}" }
                        null
                    }
                if (fetched != null) {
                    username = fetched
                }

                _authStatus.value = AuthStatus.Authenticated(username)
            }
        }

        override suspend fun syncCookiesToWebView() {
            val cookies = cookieJar.loadForRequest(rutrackerUrl)
            if (cookies.isEmpty()) return

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            val currentHost = mirrorManager.currentMirror.value
            val url = rutrackerUrl.toString()
            // Ensure domain is set with leading dot for wildcard matching across subdomains
            val domain = if (currentHost.startsWith(".")) currentHost else ".$currentHost"

            cookies.forEach { cookie ->
                val cookieString =
                    buildString {
                        append("${cookie.name}=${cookie.value}")
                        // Force domain to match current mirror
                        append("; Domain=$domain")
                        append("; Path=/")
                        if (cookie.secure) append("; Secure")
                        if (cookie.httpOnly) append("; HttpOnly")
                    }
                cookieManager.setCookie(url, cookieString)
            }
            cookieManager.flush()
        }

        override suspend fun syncCookiesToWebView(url: String) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val cookies = cookieJar.loadForRequest(httpUrl)
            if (cookies.isEmpty()) return

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            val domain = if (httpUrl.host.startsWith(".")) httpUrl.host else ".${httpUrl.host}"

            cookies.forEach { cookie ->
                val cookieString =
                    buildString {
                        append("${cookie.name}=${cookie.value}")
                        append("; Domain=$domain")
                        append("; Path=/")
                        if (cookie.secure) append("; Secure")
                        if (cookie.httpOnly) append("; HttpOnly")
                    }
                cookieManager.setCookie(url, cookieString)
            }
            cookieManager.flush()
        }

        private fun parseCookieString(
            url: String,
            cookieHeader: String,
        ): List<okhttp3.Cookie> {
            val cookies = mutableListOf<okhttp3.Cookie>()
            val httpUrl = url.toHttpUrl()
            cookieHeader.split(";").forEach { pair ->
                // Format: name=value
                // WebView cookies don't have detailed attributes in getCookie() result (only name=value)
                // We assume domain is the url host and path is /
                val parts = pair.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0]
                    val value = parts[1]
                    val cookie =
                        okhttp3.Cookie
                            .Builder()
                            .name(name)
                            .value(value)
                            .domain(httpUrl.host)
                            .path("/")
                            .build()
                    cookies.add(cookie)
                }
            }
            return cookies
        }

        override suspend fun clearStoredCredentials() {
            secureStorage.clearCredentials()
        }

        public suspend fun refreshAuthStatus() {
            checkAuthStatus()
        }
    }

// CaptchaRequiredException moved to domain package
