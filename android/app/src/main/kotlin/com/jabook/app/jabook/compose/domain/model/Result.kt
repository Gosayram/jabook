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

package com.jabook.app.jabook.compose.domain.model

/**
 * A generic sealed interface that represents the result of an operation.
 *
 * This pattern is used throughout the app for type-safe error handling
 * and to represent async operations that can succeed, fail, or be loading.
 *
 * @param T The type of data on success
 */
sealed interface Result<out T> {
    /**
     * Operation completed successfully with data.
     *
     * @property data The successful result data
     */
    data class Success<T>(
        val data: T,
    ) : Result<T>

    /**
     * Operation failed with an exception.
     *
     * @property exception The exception that caused the failure
     * @property message Optional user-friendly error message
     */
    data class Error(
        val exception: Throwable,
        val message: String? = exception.message,
    ) : Result<Nothing>

    /**
     * Operation is currently in progress.
     */
    data object Loading : Result<Nothing>
}

/**
 * Maps a successful result to a new type.
 *
 * If the result is Error or Loading, it's passed through unchanged.
 *
 * @param transform Function to transform the success data
 * @return Transformed result
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> this
    }

/**
 * Returns the data if this is a Success result, or null otherwise.
 */
fun <T> Result<T>.getOrNull(): T? =
    when (this) {
        is Result.Success -> data
        else -> null
    }

/**
 * Returns the data if this is a Success result, or throws the exception if Error.
 */
fun <T> Result<T>.getOrThrow(): T =
    when (this) {
        is Result.Success -> data
        is Result.Error -> throw exception
        is Result.Loading -> error("Cannot get data from Loading result")
    }
