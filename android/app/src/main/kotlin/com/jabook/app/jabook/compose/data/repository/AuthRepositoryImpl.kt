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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    ) : AuthRepository {
        private val _authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Unauthenticated)
        override val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

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
            public val cookies = cookieJar.loadForRequest(rutrackerUrl)
            public val hasSession = cookies.any { it.name == "bb_session" }

            if (hasSession) {
                // Validate with server (strict check) with timeout handling
                public val isValid =
                    try {
                        authService.validateAuth()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        android.util.Log.w("AuthRepository", "Auth validation timeout - provider may be blocking", e)
                        false
                    } catch (e: Exception) {
                        android.util.Log.w("AuthRepository", "Auth validation error", e)
                        false
                    }

                if (isValid) {
                    public val stored = secureStorage.getCredentials()
                    // CRITICAL: Only show authenticated if we have stored credentials with username
                    // If no credentials stored, user is not actually authenticated
                    if (stored != null && stored.username.isNotBlank()) {
                        _authStatus.value = AuthStatus.Authenticated(stored.username)
                        syncCookiesToWebView()
                    } else {
                        // Session cookie exists but no stored credentials - invalid state
                        android.util.Log.w("AuthRepository", "Session cookie exists but no stored credentials - clearing session")
                        cookieJar.clear()
                        _authStatus.value = AuthStatus.Unauthenticated
                    }
                } else {
                    // Cookies present but invalid (expired or guest mode)
                    android.util.Log.d("AuthRepository", "Session expired or invalid, attempting re-login if credentials exist")

                    // Clear invalid cookies but DO NOT clear stored credentials (logout)
                    cookieJar.clear()
                    _authStatus.value = AuthStatus.Unauthenticated

                    // Attempt automatic re-login if we have credentials
                    public val stored = secureStorage.getCredentials()
                    if (stored != null) {
                        android.util.Log.d("AuthRepository", "Found stored credentials, attempting auto-relogin")
                        try {
                            login(stored)
                        } catch (e: Exception) {
                            android.util.Log.w("AuthRepository", "Auto-relogin failed", e)
                        }
                    }
                }
            } else {
                // No session cookie, check if we should auto-login
                public val stored = secureStorage.getCredentials()
                if (stored != null && _authStatus.value !is AuthStatus.Authenticated) {
                    android.util.Log.d("AuthRepository", "No session but found credentials, attempting auto-login")
                    try {
                        login(stored)
                    } catch (e: Exception) {
                        android.util.Log.w("AuthRepository", "Auto-login failed", e)
                        _authStatus.value = AuthStatus.Unauthenticated
                    }
                } else {
                    _authStatus.value = AuthStatus.Unauthenticated
                }
            }
        }

        override suspend fun login(credentials: UserCredentials): Result<Boolean> {
            // Check if login is already in progress
            if (loginMutex.isLocked) {
                android.util.Log.w("AuthRepository", "Login already in progress, ignoring duplicate request")
                return Result.failure(IllegalStateException("Login already in progress"))
            }

            return loginMutex.withLock {
                try {
                    public val operationId: String = "login_${System.currentTimeMillis()}"                    android.util.Log.d("AuthRepository", "[$operationId] Login attempt started")

                    when (val result = authService.login(credentials)) {
                        is RutrackerAuthService.AuthResult.Success -> {
                            // Validate authentication to ensure it actually worked
                            public val isValid =
                                try {
                                    authService.validateAuth(operationId)
                                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                    android.util.Log.w("AuthRepository", "[$operationId] Validation timeout - provider may be blocking", e)
                                    _authStatus.value =
                                        AuthStatus.Error("Таймаут при проверке авторизации. Возможно, провайдер блокирует соединение.")
                                    return@withLock Result.failure(Exception("Authentication validation timeout"))
                                } catch (e: Exception) {
                                    android.util.Log.w("AuthRepository", "[$operationId] Validation error", e)
                                    false
                                }

                            if (isValid) {
                                // Persist cookies to all layers (Database, WebView, SecureStorage)
                                try {
                                    cookiePersistence.persistCookiesMultiStage(rutrackerUrl.toString())
                                    android.util.Log.d("AuthRepository", "[$operationId] Cookies persisted to all layers")
                                } catch (e: Exception) {
                                    android.util.Log.w("AuthRepository", "[$operationId] Cookie persistence failed", e)
                                }

                                _authStatus.value = AuthStatus.Authenticated(credentials.username)
                                android.util.Log.i("AuthRepository", "[$operationId] Login successful and validated")
                                Result.success(true)
                            } else {
                                // Login appeared to succeed but validation failed
                                android.util.Log.w("AuthRepository", "[$operationId] Login succeeded but validation failed")
                                _authStatus.value = AuthStatus.Error("Проверка авторизации не прошла. Попробуйте еще раз.")
                                Result.failure(Exception("Authentication validation failed"))
                            }
                        }
                        is RutrackerAuthService.AuthResult.Error -> {
                            android.util.Log.w("AuthRepository", "[$operationId] Login failed: ${result.message}")
                            _authStatus.value = AuthStatus.Error(result.message)
                            Result.failure(Exception(result.message))
                        }
                        is RutrackerAuthService.AuthResult.Captcha -> {
                            android.util.Log.d("AuthRepository", "[$operationId] Captcha required")
                            // This login method doesn't support captcha return
                            // In a real app we might want a specific error type or flow
                            Result.failure(CaptchaRequiredException(result.data))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "Login exception", e)
                    _authStatus.value = AuthStatus.Error(e.message ?: "Unknown error")
                    Result.failure(e)
                }
            }
        }

        override suspend fun loginWithCaptcha(
            credentials: UserCredentials,
            captchaCode: String,
            captchaData: CaptchaData,
        ): Result<Boolean> {
            // Check if login is already in progress
            if (loginMutex.isLocked) {
                android.util.Log.w("AuthRepository", "Captcha login already in progress, ignoring duplicate request")
                return Result.failure(IllegalStateException("Login already in progress"))
            }

            return loginMutex.withLock {
                try {
                    public val operationId: String = "login_captcha_${System.currentTimeMillis()}"                    android.util.Log.d("AuthRepository", "[$operationId] Captcha login attempt started")

                    when (val result = authService.login(credentials, captchaCode, captchaData)) {
                        is RutrackerAuthService.AuthResult.Success -> {
                            // Validate authentication to ensure it actually worked
                            public val isValid = authService.validateAuth(operationId)

                            if (isValid) {
                                // Persist cookies to all layers
                                try {
                                    cookiePersistence.persistCookiesMultiStage(rutrackerUrl.toString())
                                    android.util.Log.d("AuthRepository", "[$operationId] Cookies persisted to all layers")
                                } catch (e: Exception) {
                                    android.util.Log.w("AuthRepository", "[$operationId] Cookie persistence failed", e)
                                }

                                _authStatus.value = AuthStatus.Authenticated(credentials.username)
                                android.util.Log.i("AuthRepository", "[$operationId] Captcha login successful and validated")
                                Result.success(true)
                            } else {
                                android.util.Log.w("AuthRepository", "[$operationId] Captcha login succeeded but validation failed")
                                _authStatus.value = AuthStatus.Error("Login validation failed")
                                Result.failure(Exception("Authentication validation failed"))
                            }
                        }
                        is RutrackerAuthService.AuthResult.Error -> {
                            android.util.Log.w("AuthRepository", "[$operationId] Captcha login failed: ${result.message}")
                            _authStatus.value = AuthStatus.Error(result.message)
                            Result.failure(Exception(result.message))
                        }
                        is RutrackerAuthService.AuthResult.Captcha -> {
                            android.util.Log.d("AuthRepository", "[$operationId] Captcha required again")
                            // Captcha failed or required again
                            Result.failure(CaptchaRequiredException(result.data))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "Captcha login exception", e)
                    Result.failure(e)
                }
            }
        }

        override suspend fun logout() {
            cookieJar.clear()
            secureStorage.clearCredentials()
            _authStatus.value = AuthStatus.Unauthenticated
        }

        override suspend fun isLoggedIn(): Boolean {
            // Also refresh status
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

        override suspend fun syncCookiesFromWebView() {
            public val url = rutrackerUrl.toString()

            // Use CookiePersistenceManager to sync from WebView to all layers
            cookiePersistence.syncCookiesFromWebView(url)

            // Refresh auth status immediately
            checkAuthStatus()
        }

        override suspend fun syncCookiesToWebView() {
            public val cookies = cookieJar.loadForRequest(rutrackerUrl)
            if (cookies.isEmpty()) return

            public val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            public val currentHost = mirrorManager.currentMirror.value
            public val url = rutrackerUrl.toString()
            // Ensure domain is set with leading dot for wildcard matching across subdomains
            public val domain = if (currentHost.startsWith(".")) currentHost else ".$currentHost"

            cookies.forEach { cookie ->
                public val cookieString =
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

        private fun parseCookieString(
            url: String,
            cookieHeader: String,
        ): List<okhttp3.Cookie> {
            public val cookies = mutableListOf<okhttp3.Cookie>()
            public val httpUrl = url.toHttpUrl()
            cookieHeader.split(";").forEach { pair ->
                // Format: name=value
                // WebView cookies don't have detailed attributes in getCookie() result (only name=value)
                // We assume domain is the url host and path is /
                public val parts = pair.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    public val name = parts[0]
                    public val value = parts[1]
                    public val cookie =
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

        suspend fun refreshAuthStatus() {
            checkAuthStatus()
        }
    }

// CaptchaRequiredException moved to domain package
