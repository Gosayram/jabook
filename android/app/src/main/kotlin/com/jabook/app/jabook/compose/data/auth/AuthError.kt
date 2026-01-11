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

package com.jabook.app.jabook.compose.data.auth

import com.jabook.app.jabook.compose.domain.model.CaptchaData

/**
 * Structured authentication errors.
 */
public sealed class AuthError {
    public data class InvalidCredentials(
        public val message: String,
    ) : AuthError()

    public data class NetworkError(
        public val message: String,
        public val cause: Throwable?,
    ) : AuthError()

    public data class CaptchaRequired(
        public val data: CaptchaData,
    ) : AuthError()

    public data class ServerError(
        public val code: Int,
        public val message: String,
    ) : AuthError()

    public data class Unknown(
        public val message: String,
        public val cause: Throwable?,
    ) : AuthError()
}
