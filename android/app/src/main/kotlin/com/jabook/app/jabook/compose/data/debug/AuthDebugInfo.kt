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
 * Debug information about RuTracker authentication status.
 * Used for diagnostics in Debug screen.
 */
data class AuthDebugInfo(
    val isAuthenticated: Boolean,
    val lastAuthAttemptTime: Long?,
    val lastAuthError: String?,
    val currentMirror: String,
    val mirrorConnectivity: Map<String, MirrorStatus>,
    val validationResults: ValidationResults,
)

/**
 * Status of a mirror connectivity check.
 */
data class MirrorStatus(
    val isReachable: Boolean,
    val responseTimeMs: Long?,
    val lastChecked: Long,
    val error: String? = null,
)

/**
 * Results of 3-tier authentication validation.
 */
data class ValidationResults(
    val profilePageCheck: CheckResult,
    val searchPageCheck: CheckResult,
    val indexPageCheck: CheckResult,
    val lastValidationTime: Long,
)

/**
 * Result of a single validation check.
 */
data class CheckResult(
    val passed: Boolean,
    val durationMs: Long,
    val error: String? = null,
)
