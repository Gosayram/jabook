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

package com.jabook.app.jabook.compose.data.debug

/**
 * Data class containing diagnostic information for RuTracker authentication and connectivity.
 */
public data class AuthDebugInfo(
    public val isAuthenticated: Boolean,
    public val lastAuthAttempt: Long? = null,
    public val lastAuthError: String? = null,
    public val mirrorConnectivity: Map<String, Boolean> = emptyMap(),
    public val validationResults: ValidationResults? = null,
)

/**
 * Results of 3-tier validation logic from Flutter implementation.
 */
public data class ValidationResults(
    public val profilePageCheck: Boolean,
    public val searchPageCheck: Boolean,
    public val indexPageCheck: Boolean,
    public val lastValidation: Long,
)

/**
 * Extension to display validation check as emoji
 */
public fun Boolean?.toIcon(): String =
    when (this) {
        true -> "✅"
        false -> "❌"
        null -> "❓"
    }
