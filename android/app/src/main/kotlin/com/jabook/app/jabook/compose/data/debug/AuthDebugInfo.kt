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

package com.jabook.app.jabook.compose.data.debug

/**
 * Data class containing diagnostic information for RuTracker authentication and connectivity.
 */
data class AuthDebugInfo(
    val isAuthenticated: Boolean,
    val lastAuthAttempt: Long? = null,
    val lastAuthError: String? = null,
    val mirrorConnectivity: Map<String, Boolean> = emptyMap(),
    val validationResults: ValidationResults? = null,
)

/**
 * Results of 3-tier validation logic from Flutter implementation.
 */
data class ValidationResults(
    val profilePageCheck: Boolean,
    val searchPageCheck: Boolean,
    val indexPageCheck: Boolean,
    val lastValidation: Long,
)

/**
 * Extension to display validation check as emoji
 */
fun Boolean?.toIcon(): String =
    when (this) {
        true -> "✅"
        false -> "❌"
        null -> "❓"
    }
