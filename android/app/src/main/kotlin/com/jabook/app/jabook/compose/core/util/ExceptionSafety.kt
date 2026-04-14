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

@file:Suppress("NOTHING_TO_INLINE")

package com.jabook.app.jabook.compose.core.util

import kotlinx.coroutines.CancellationException

/**
 * Safety utilities for exception handling in coroutines.
 *
 * ## Problem
 * Kotlin coroutines use [CancellationException] to control structured concurrency.
 * Catching `Exception` or `Throwable` without rethrowing `CancellationException`
 * breaks coroutine cancellation and can cause:
 * - Resource leaks
 * - Deadlocks
 * - Incomplete cleanup
 * - Unexpected behavior
 *
 * ## Solution
 * Always rethrow `CancellationException` when catching broad exception types:
 * ```kotlin
 * try {
 *     riskyOperation()
 * } catch (e: Exception) {
 *     if (e is CancellationException) throw e // CRITICAL!
 *     logger.e(e) { "Operation failed" }
 * }
 * ```
 *
 * ## Helper Functions
 * Use [runCatchingCancelable] for automatic handling:
 * ```kotlin
 * runCatchingCancelable { riskyOperation() }
 *     .onFailure { logger.e(it) { "Failed" } }
 *     .getOrNull()
 * ```
 */
public object ExceptionSafety {
    /**
     * Checks if the exception is a coroutine cancellation signal.
     * Use this in catch blocks before handling the exception.
     *
     * @return `true` if this is [CancellationException], `false` otherwise
     */
    public fun isCancellation(e: Throwable): Boolean = e is CancellationException

    /**
     * Rethrows the exception if it's a cancellation signal.
     * Call this at the start of your catch block.
     *
     * @throws CancellationException if [e] is a cancellation
     */
    public fun rethrowIfCancellation(e: Throwable) {
        if (e is CancellationException) throw e
    }
}

/**
 * Runs the given [block] and catches any exception while preserving cancellation.
 *
 * Unlike `runCatching`, this function rethrows [CancellationException] to preserve
 * structured concurrency semantics.
 *
 * ## Example
 * ```kotlin
 * val result = runCatchingCancelable {
 *     repository.fetchData()
 * }.getOrElse { e ->
 *     logger.e(e) { "Fetch failed" }
 *     emptyList()
 * }
 * ```
 *
 * @param block The code to execute
 * @return [Result] with success or failure (never contains CancellationException)
 */
public inline fun <T> runCatchingCancelable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e // Always rethrow cancellation
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * Runs the given [block] and catches any throwable while preserving cancellation.
 *
 * Use this instead of `catch (e: Throwable)` when you need to catch [Error] types too.
 *
 * @param block The code to execute
 * @return [Result] with success or failure (never contains CancellationException)
 */
public inline fun <T> runCatchingCancelableThrowable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e // Always rethrow cancellation
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Extension to safely handle exceptions in catch blocks.
 * Call this at the beginning of your catch block.
 *
 * ## Example
 * ```kotlin
 * try {
 *     operation()
 * } catch (e: Exception) {
 *     e.rethrowCancellation() // Must be first line!
 *     logger.e(e) { "Operation failed" }
 * }
 * ```
 */
public inline fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}

/**
 * Safely executes a suspending block with exception handling that preserves cancellation.
 *
 * @param block The suspending code to execute
 * @param onFailure Handler called for non-cancellation exceptions
 * @return The result of [block] or null if an exception occurred
 */
public suspend inline fun <T> runCatchingCancelableSuspend(
    crossinline block: suspend () -> T,
    crossinline onFailure: (Exception) -> Unit = {},
): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e // Always rethrow cancellation
    } catch (e: Exception) {
        onFailure(e)
        null
    }
