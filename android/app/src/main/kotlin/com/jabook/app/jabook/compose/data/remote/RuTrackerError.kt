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

package com.jabook.app.jabook.compose.data.remote

/**
 * Typed errors for RuTracker operations.
 *
 * Based on Flow project analysis - provides clear error classification
 * instead of generic Exception with string messages.
 *
 * Usage:
 * ```kotlin
 * when (val result = runCatching { someOperation() }) {
 *     is Result.Success -> result.getOrNull()
 *     is Result.Failure -> {
 *         when (val error = result.exceptionOrNull()) {
 *             is RuTrackerError.Unauthorized -> handleUnauthorized()
 *             is RuTrackerError.NoData -> handleNoData()
 *             else -> handleUnknownError(error)
 *         }
 *     }
 * }
 * ```
 */
public sealed class RuTrackerError : Throwable() {
    /**
     * User is not authenticated or token is invalid.
     */
    data object Unauthorized : RuTrackerError() {
        override val message: String = "Authentication required"
    }

    /**
     * No data found (empty response, topic not found, etc.).
     */
    data object NoData : RuTrackerError() {
        override val message: String = "No data available"
    }

    /**
     * Network connection error (no internet, DNS failure, etc.).
     */
    data object NoConnection : RuTrackerError() {
        override val message: String = "No network connection"
    }

    /**
     * Resource not found (404, topic deleted, etc.).
     */
    data object NotFound : RuTrackerError() {
        override val message: String = "Resource not found"
    }

    /**
     * Bad request (400, invalid parameters, etc.).
     */
    data object BadRequest : RuTrackerError() {
        override val message: String = "Bad request"
    }

    /**
     * Access forbidden (403, insufficient permissions, etc.).
     */
    data object Forbidden : RuTrackerError() {
        override val message: String = "Access forbidden"
    }

    /**
     * Parsing error (HTML structure changed, invalid format, etc.).
     */
    public data class ParsingError(
        override val message: String,
    ) : RuTrackerError()

    /**
     * Unknown error with optional message.
     */
    public data class Unknown(
        private val errorMessage: String? = null,
    ) : RuTrackerError() {
        override val message: String
            get() = errorMessage ?: "Unknown error"
    }
}
