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

import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import javax.inject.Inject

/**
 * Use case wrapper that ensures authentication before executing operations.
 *
 * Based on Flow project analysis - provides a composable way to add
 * authentication checks to any operation that requires authentication.
 *
 * Usage:
 * ```kotlin
 * val result = withAuthorisedCheckUseCase(html) { authenticatedHtml ->
 *     // Perform operation with authenticated HTML
 *     parseData(authenticatedHtml)
 * }
 * ```
 */
public class WithAuthorisedCheckUseCase
    @Inject
    constructor(
        private val verifyAuthorisedUseCase: VerifyAuthorisedUseCase,
    ) {
        /**
         * Execute operation only if user is authenticated.
         *
         * @param html HTML content to verify
         * @param mapper Operation to execute if authenticated
         * @return Result of the operation
         * @throws RuTrackerError.Unauthorized if not authenticated
         */
        public suspend operator fun <T> invoke(
            html: String,
            mapper: suspend (html: String) -> T,
        ): T =
            if (verifyAuthorisedUseCase(html)) {
                mapper(html)
            } else {
                throw RuTrackerError.Unauthorized
            }

        /**
         * Execute operation only if user is authenticated (using repository validation).
         *
         * This method uses network-based validation which is more reliable
         * but requires a network request.
         *
         * @param operationId Optional operation ID for logging correlation
         * @param block Operation to execute if authenticated
         * @return Result of the operation
         * @throws RuTrackerError.Unauthorized if not authenticated
         */
        public suspend operator fun <T> invoke(
            operationId: String? = null,
            block: suspend () -> T,
        ): T =
            if (verifyAuthorisedUseCase.verifyViaRepository(operationId)) {
                block()
            } else {
                throw RuTrackerError.Unauthorized
            }
    }
