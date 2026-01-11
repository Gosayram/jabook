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

package com.jabook.app.jabook.compose.domain.model

/**
 * Represents different types of authentication errors.
 * Based on Flutter's robust error handling patterns.
 */
public sealed class AuthError {
    /**
     * Invalid username or password.
     */
    public data class InvalidCredentials(
        val message: String = "Invalid username or password",
    ) : AuthError()

    /**
     * Network-related error (timeout, connection failure, etc.).
     */
    public data class NetworkError(
        val message: String,
        val cause: Throwable? = null,
    ) : AuthError()

    /**
     * Captcha verification required.
     */
    public data class CaptchaRequired(
        val data: CaptchaData,
    ) : AuthError()

    /**
     * Server error (5xx status codes).
     */
    public data class ServerError(
        val code: Int,
        val message: String,
    ) : AuthError()

    /**
     * Session expired or authentication required.
     */
    public data class SessionExpired(
        val message: String = "Session expired. Please log in again.",
    ) : AuthError()

    /**
     * Unknown or unexpected error.
     */
    public data class Unknown(
        val message: String,
        val cause: Throwable? = null,
    ) : AuthError()

    /**
     * Get user-friendly error message.
     */
    public fun getUserMessage(): String =
        when (this) {
            is InvalidCredentials -> message
            is NetworkError -> message
            is CaptchaRequired -> "Captcha verification required"
            is ServerError -> "Server error: $message"
            is SessionExpired -> message
            is Unknown -> message
        }
}
