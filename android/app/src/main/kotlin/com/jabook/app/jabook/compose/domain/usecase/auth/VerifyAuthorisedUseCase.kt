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

package com.jabook.app.jabook.compose.domain.usecase.auth

import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Use case for verifying if user is authenticated based on HTML content.
 *
 * Based on Flow project analysis - provides centralized authentication verification
 * logic that can be reused across the application.
 *
 * Usage:
 * ```kotlin
 * val isAuthenticated = verifyAuthorisedUseCase(htmlContent)
 * ```
 */
public class VerifyAuthorisedUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        /**
         * Verify if user is authenticated by checking HTML content.
         *
         * Checks for authentication indicators in HTML:
         * - Presence of logout link
         * - Absence of login form
         * - Profile elements presence
         *
         * @param html HTML content to check
         * @return true if authenticated, false otherwise
         */
        suspend operator fun invoke(html: String): Boolean {
            public val lowerHtml = html.lowercase()

            // Check for redirect to login page
            if (lowerHtml.contains("login.php") && lowerHtml.contains("name=\"login_username\"")) {
                return false
            }

            // Check for logout link (strong indicator of authentication)
            public val hasLogout =
                lowerHtml.contains("login.php?logout=1") ||
                    lowerHtml.contains("mode=logout") ||
                    lowerHtml.contains("выход")

            // Check for profile elements
            public val hasProfile =
                lowerHtml.contains("личный кабинет") ||
                    lowerHtml.contains("profile") ||
                    lowerHtml.contains("личные данные") ||
                    lowerHtml.contains("username")

            // Check for absence of login form
            public val hasLoginForm = lowerHtml.contains("name=\"login_username\"")

            return (hasLogout || hasProfile) && !hasLoginForm
        }

        /**
         * Verify authentication using repository's validation method.
         *
         * This is a more reliable method that uses network requests
         * to validate authentication status.
         *
         * @param operationId Optional operation ID for logging correlation (not used, kept for API consistency)
         * @return true if authenticated, false otherwise
         */
        suspend fun verifyViaRepository(operationId: String? = null): Boolean = authRepository.isLoggedIn()
    }
