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

package com.jabook.app.jabook.utils

import kotlinx.coroutines.delay

/**
 * Retry utilities (inspired by Flow pattern).
 *
 * Provides reusable retry logic with exponential backoff for error recovery.
 */

/**
 * Retry configuration for exponential backoff.
 *
 * @param maxRetries Maximum number of retry attempts (default: 3)
 * @param initialDelayMs Initial delay before first retry in milliseconds (default: 500)
 * @param maxDelayMs Maximum delay between retries in milliseconds (default: 10_000)
 * @param backoffMultiplier Multiplier for exponential backoff (default: 2.0)
 * @param shouldRetry Predicate to determine if exception should trigger retry (default: retry on IOException)
 */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 10_000L,
    val backoffMultiplier: Double = 2.0,
    val shouldRetry: (Throwable) -> Boolean = { it is java.io.IOException || it is java.net.SocketTimeoutException },
) {
    /**
     * Calculates delay for exponential backoff.
     *
     * @param attempt Current attempt number (0-based)
     * @return Delay in milliseconds
     */
    fun calculateDelay(attempt: Int): Long {
        val delay = (initialDelayMs * kotlin.math.pow(backoffMultiplier, attempt.toDouble())).toLong()
        return delay.coerceAtMost(maxDelayMs)
    }
}

/**
 * Retries a suspend function with exponential backoff (inspired by Flow pattern).
 *
 * @param config Retry configuration
 * @param block The suspend function to retry
 * @return Result of the block execution
 * @throws Throwable The last exception if all retries fail
 *
 * Example:
 * ```kotlin
 * val result = retryWithBackoff {
 *     networkCall()
 * }
 * ```
 */
suspend fun <T> retryWithBackoff(
    config: RetryConfig = RetryConfig(),
    block: suspend () -> T,
): T {
    var lastException: Throwable? = null

    repeat(config.maxRetries + 1) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastException = e

            // Check if we should retry
            if (attempt < config.maxRetries && config.shouldRetry(e)) {
                val delay = config.calculateDelay(attempt)
                kotlinx.coroutines.delay(delay)
                // Continue to next attempt
            } else {
                // Don't retry or max retries reached
                throw e
            }
        }
    }

    // This should never be reached, but compiler needs it
    throw lastException ?: IllegalStateException("Retry failed without exception")
}

/**
 * Retries a suspend function with exponential backoff and returns Result.
 *
 * Similar to retryWithBackoff, but returns Result<T> instead of throwing.
 * Useful when you want to handle errors gracefully without try-catch.
 *
 * @param config Retry configuration
 * @param block The suspend function to retry
 * @return Result.success if successful, Result.failure if all retries fail
 *
 * Example:
 * ```kotlin
 * val result = retryWithBackoffResult {
 *     networkCall()
 * }
 * result.onSuccess { data ->
 *     handleSuccess(data)
 * }.onFailure { error ->
 *     handleError(error)
 * }
 * ```
 */
suspend fun <T> retryWithBackoffResult(
    config: RetryConfig = RetryConfig(),
    block: suspend () -> T,
): Result<T> =
    kotlin.runCatching {
        retryWithBackoff(config, block)
    }
