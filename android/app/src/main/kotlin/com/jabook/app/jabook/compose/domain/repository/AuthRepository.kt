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

package com.jabook.app.jabook.compose.domain.repository

import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.CaptchaData
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing authentication state and operations.
 */
public interface AuthRepository {
    /**
     * Flow of current authentication status.
     */
    val authStatus: Flow<AuthStatus>

    /**
     * Attempt to log in with username and password.
     * @return Result.success(true) if successful, Result.failure if error.
     */
    public suspend fun login(credentials: UserCredentials): Result<Boolean>

    /**
     * Attempt to log in with captcha.
     */
    public suspend fun loginWithCaptcha(
        credentials: UserCredentials,
        captchaCode: String,
        captchaData: CaptchaData,
    ): Result<Boolean>

    /**
     * Log out the current user.
     */
    public suspend fun logout()

    /**
     * Check if user is currently logged in (valid session).
     */
    public suspend fun isLoggedIn(): Boolean

    /**
     * Save credentials for auto-login.
     */
    public suspend fun saveCredentials(credentials: UserCredentials)

    /**
     * Get stored credentials.
     */
    public suspend fun getStoredCredentials(): UserCredentials?

    /**
     * Sync cookies from system WebView to PersistentCookieJar.
     * Should be called after WebView login.
     */
    public suspend fun syncCookiesFromWebView()

    /**
     * Sync cookies from PersistentCookieJar to system WebView.
     * Should be called on app start or before WebView navigation.
     */
    public suspend fun syncCookiesToWebView()

    /**
     * Clear stored credentials.
     */
    public suspend fun clearStoredCredentials()
}
