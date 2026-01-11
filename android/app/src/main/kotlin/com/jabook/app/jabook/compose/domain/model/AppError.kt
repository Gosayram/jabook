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
 * Base sealed interface for all application errors.
 *
 * Provides type-safe error handling with clear error categories.
 * Based on analysis of Flow project and best practices.
 */
public sealed interface AppError {
    /**
     * User-friendly error message.
     */
    public val message: String

    /**
     * Optional underlying cause/exception.
     */
    public val cause: Throwable?

    /**
     * Network-related errors.
     */
    public sealed interface NetworkError : AppError {
        /**
         * No network connection available.
         */
        public data object NoConnection : NetworkError {
            override val message: String = "No network connection"
            override val cause: Throwable? = null
        }

        /**
         * Request timeout.
         */
        public data object Timeout : NetworkError {
            override val message: String = "Request timeout"
            override val cause: Throwable? = null
        }

        /**
         * HTTP error with status code.
         */
        public data class HttpError(
            public val code: Int,
            public val response: String? = null,
            override val cause: Throwable? = null,
        ) : NetworkError {
            override val message: String = "HTTP error $code"
        }

        /**
         * DNS resolution failed.
         */
        public data class DnsError(
            override val message: String = "DNS resolution failed",
            override val cause: Throwable? = null,
        ) : NetworkError
    }

    /**
     * Authentication and authorization errors.
     */
    public sealed interface AuthError : AppError {
        /**
         * User is not authenticated.
         */
        public data object Unauthorized : AuthError {
            override val message: String = "Authentication required"
            override val cause: Throwable? = null
        }

        /**
         * Invalid credentials provided.
         */
        public data object InvalidCredentials : AuthError {
            override val message: String = "Invalid credentials"
            override val cause: Throwable? = null
        }

        /**
         * Captcha verification required.
         */
        public data class CaptchaRequired(
            public val captchaData: CaptchaData,
        ) : AuthError {
            override val message: String = "Captcha verification required"
            override val cause: Throwable? = null
        }

        /**
         * Session expired.
         */
        public data class SessionExpired(
            override val message: String = "Session expired. Please log in again.",
            override val cause: Throwable? = null,
        ) : AuthError
    }

    /**
     * Data parsing errors.
     */
    public sealed interface ParsingError : AppError {
        /**
         * Invalid format for a field.
         */
        public data class InvalidFormat(
            public val field: String,
            public val expected: String,
            override val cause: Throwable? = null,
        ) : ParsingError {
            override val message: String = "Invalid format for field '$field': expected $expected"
        }

        /**
         * Missing required field.
         */
        public data class MissingField(
            public val field: String,
            override val cause: Throwable? = null,
        ) : ParsingError {
            override val message: String = "Missing required field: $field"
        }

        /**
         * Partial parsing success with some errors.
         */
        public data class PartialSuccess(
            public val errors: List<ParsingError>,
            override val cause: Throwable? = null,
        ) : ParsingError {
            override val message: String = "Partial parsing success with ${errors.size} errors"
        }
    }

    /**
     * Data-related errors.
     */
    public sealed interface DataError : AppError {
        /**
         * Data not found.
         */
        public data object NotFound : DataError {
            override val message: String = "Data not found"
            override val cause: Throwable? = null
        }

        /**
         * No data available.
         */
        public data object Empty : DataError {
            override val message: String = "No data available"
            override val cause: Throwable? = null
        }
    }

    /**
     * Unknown or unexpected error.
     */
    public data class Unknown(
        override val message: String,
        override val cause: Throwable? = null,
    ) : AppError
}
