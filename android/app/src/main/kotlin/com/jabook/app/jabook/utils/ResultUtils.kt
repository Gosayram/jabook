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

import com.jabook.app.jabook.compose.domain.model.Result

/**
 * Result extension utilities (inspired by Flow pattern).
 *
 * Provides useful extension functions for working with Result types
 * for better error handling and functional programming.
 */

/**
 * Returns true if this is a Success result.
 */
public fun <T> Result<T>.isSuccess(): Boolean = this is Result.Success

/**
 * Returns true if this is an Error result.
 */
public fun <T> Result<T>.isError(): Boolean = this is Result.Error

/**
 * Returns true if this is a Loading result.
 */
public fun <T> Result<T>.isLoading(): Boolean = this is Result.Loading

/**
 * Returns the data if this is a Success result, or the default value otherwise.
 *
 * @param defaultValue Function that provides the default value
 * @return The data if Success, or the default value
 *
 * Example:
 * ```kotlin
 * val value = result.getOrElse { "default" }
 * ```
 */
inline fun <T> Result<T>.getOrElse(defaultValue: () -> T): T =
    when (this) {
        is Result.Success -> data
        else -> defaultValue()
    }

/**
 * Executes the action if this is a Success result.
 *
 * @param action Function to execute with the success data
 * @return This result unchanged
 *
 * Example:
 * ```kotlin
 * result.onSuccess { data ->
 *     println("Success: $data")
 * }
 * ```
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

/**
 * Executes the action if this is an Error result.
 *
 * @param action Function to execute with the error
 * @return This result unchanged
 *
 * Example:
 * ```kotlin
 * result.onFailure { error ->
 *     logError(error)
 * }
 * ```
 */
inline fun <T> Result<T>.onFailure(action: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(exception)
    }
    return this
}

/**
 * Executes the action if this is a Loading result.
 *
 * @param action Function to execute
 * @return This result unchanged
 *
 * Example:
 * ```kotlin
 * result.onLoading {
 *     showLoadingIndicator()
 * }
 * ```
 */
inline fun <T> Result<T>.onLoading(action: () -> Unit): Result<T> {
    if (this is Result.Loading) {
        action()
    }
    return this
}

/**
 * Transforms the result using different functions for each state.
 *
 * @param onSuccess Function to transform Success data
 * @param onError Function to transform Error
 * @param onLoading Function to transform Loading
 * @return Transformed result
 *
 * Example:
 * ```kotlin
 * val message = result.fold(
 *     onSuccess = { "Success: $it" },
 *     onError = { "Error: ${it.message}" },
 *     onLoading = { "Loading..." }
 * )
 * ```
 */
inline fun <T, R> Result<T>.fold(
    onSuccess: (T) -> R,
    onError: (Throwable) -> R,
    onLoading: () -> R,
): R =
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(exception)
        is Result.Loading -> onLoading()
    }

/**
 * Chains another result-returning operation.
 *
 * @param transform Function that returns a new Result
 * @return The new Result
 *
 * Example:
 * ```kotlin
 * val result = fetchUser()
 *     .flatMap { user -> fetchUserDetails(user.id) }
 * ```
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> this
        is Result.Loading -> this
    }

/**
 * Recovers from an error by providing an alternative value.
 *
 * @param recover Function that provides a recovery value from the error
 * @return Success with original data, or Success with recovered value
 *
 * Example:
 * ```kotlin
 * val result = fetchData()
 *     .recover { error ->
 *         if (error is NetworkException) {
 *             getCachedData()
 *         } else {
 *             throw error
 *         }
 *     }
 * ```
 */
inline fun <T> Result<T>.recover(recover: (Throwable) -> T): Result<T> =
    when (this) {
        is Result.Success -> this
        is Result.Error -> Result.Success(recover(exception))
        is Result.Loading -> this
    }

/**
 * Recovers from an error by providing an alternative Result.
 *
 * @param recover Function that provides a recovery Result from the error
 * @return Success with original data, or recovered Result
 *
 * Example:
 * ```kotlin
 * val result = fetchData()
 *     .recoverWith { error ->
 *         if (error is NetworkException) {
 *             getCachedDataResult()
 *         } else {
 *             Result.Error(error)
 *         }
 *     }
 * ```
 */
inline fun <T> Result<T>.recoverWith(recover: (Throwable) -> Result<T>): Result<T> =
    when (this) {
        is Result.Success -> this
        is Result.Error -> recover(exception)
        is Result.Loading -> this
    }
