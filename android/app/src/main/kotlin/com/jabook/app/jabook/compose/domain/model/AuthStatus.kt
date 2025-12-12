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

package com.jabook.app.jabook.compose.domain.model

/**
 * Represents the current authentication status.
 */
sealed interface AuthStatus {
    /**
     * User is not logged in.
     */
    data object Unauthenticated : AuthStatus

    /**
     * User is successfully authenticated.
     */
    data class Authenticated(
        val username: String,
    ) : AuthStatus

    /**
     * Authentication failed or error occurred.
     */
    data class Error(
        val message: String,
    ) : AuthStatus
}
