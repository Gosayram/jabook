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

import com.jabook.app.jabook.audio.core.result.Result as AudioResult

/**
 * A generic sealed interface that represents the result of an operation.
 *
 * This pattern is used throughout the app for type-safe error handling
 * and to represent async operations that can succeed, fail, or be loading.
 *
 * Improved version with typed errors based on analysis.
 *
 * @param T The type of data on success
 * @param E The type of error (defaults to AppError)
 */
public sealed interface Result<out T, out E : AppError> {
    /**
     * Operation completed successfully with data.
     *
     * @property data The successful result data
     */
    public data class Success<T>(
        val data: T,
    ) : Result<T, Nothing>

    /**
     * Operation failed with a typed error.
     *
     * @property error The typed error that caused the failure
     */
    public data class Error<E : AppError>(
        val error: E,
    ) : Result<Nothing, E>

    /**
     * Operation is currently in progress.
     *
     * @property progress Optional progress value (0.0 to 1.0)
     */
    public data class Loading(
        val progress: Float? = null,
    ) : Result<Nothing, Nothing>
}

/**
 * Type alias for Result with AppError as default error type.
 * This provides convenience for the common case where AppError is used.
 */
public typealias AppResult<T> = Result<T, AppError>

/**
 * Legacy Result type for backward compatibility.
 * @deprecated Use Result<T> instead
 */
@Deprecated(
    message = "Use Result<T> instead",
    replaceWith = ReplaceWith("Result<T>"),
)
public typealias LegacyResult<T> = AppResult<T>

/**
 * Maps a successful result to a new type.
 *
 * If the result is Error or Loading, it's passed through unchanged.
 *
 * @param transform Function to transform the success data
 * @return Transformed result
 */
public inline fun <T, R, E : AppError> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> this
    }

/**
 * Maps an error to a new error type.
 *
 * @param transform Function to transform the error
 * @return Transformed result
 */
public inline fun <T, E : AppError, F : AppError> Result<T, E>.mapError(transform: (E) -> F): Result<T, F> =
    when (this) {
        is Result.Success -> this
        is Result.Error -> Result.Error(transform(error))
        is Result.Loading -> this
    }

/**
 * Returns the data if this is a Success result, or null otherwise.
 */
public fun <T, E : AppError> Result<T, E>.getOrNull(): T? =
    when (this) {
        is Result.Success -> data
        else -> null
    }

/**
 * Returns the data if this is a Success result, or throws the error if Error.
 */
public fun <T, E : AppError> Result<T, E>.getOrThrow(): T =
    when (this) {
        is Result.Success -> data
        is Result.Error -> throw RuntimeException(error.message, error.cause)
        is Result.Loading -> error("Cannot get data from Loading result")
    }

/**
 * Returns the error if this is an Error result, or null otherwise.
 */
public fun <T, E : AppError> Result<T, E>.getErrorOrNull(): E? =
    when (this) {
        is Result.Error -> error
        else -> null
    }

/**
 * Folds the result into a single value.
 *
 * @param onSuccess Function to handle success case
 * @param onError Function to handle error case
 * @param onLoading Function to handle loading case
 * @return The folded value
 */
public inline fun <T, E : AppError, R> Result<T, E>.fold(
    onSuccess: (T) -> R,
    onError: (E) -> R,
    onLoading: () -> R,
): R =
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(error)
        is Result.Loading -> onLoading()
    }

/**
 * Executes action if result is Success.
 */
public inline fun <T, E : AppError> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

/**
 * Executes action if result is Error.
 */
public inline fun <T, E : AppError> Result<T, E>.onError(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Error) {
        action(error)
    }
    return this
}

/**
 * Executes action if result is Loading.
 */
public inline fun <T, E : AppError> Result<T, E>.onLoading(action: () -> Unit): Result<T, E> {
    if (this is Result.Loading) {
        action()
    }
    return this
}

/**
 * Converts a Throwable to AppError.Unknown.
 */
public fun Throwable.toAppError(): AppError.Unknown =
    AppError.Unknown(
        message = message ?: "Unknown error",
        cause = this,
    )

/**
 * Converts legacy Result<T> (with Throwable) to new Result<T, AppError>.
 */
public fun <T> AudioResult<T>.toTypedResult(): Result<T, AppError> =
    when (this) {
        is AudioResult.Success -> Result.Success(data)
        is AudioResult.Error -> Result.Error(exception.toAppError())
        is AudioResult.Loading -> Result.Loading()
    }

/**
 * Converts typed Result<T, AppError> to audio-layer Result<T>.
 */
public fun <T, E : AppError> Result<T, E>.toAudioResult(): AudioResult<T> =
    when (this) {
        is Result.Success -> AudioResult.Success(data)
        is Result.Error ->
            AudioResult.Error(
                error.cause as? Exception ?: RuntimeException(error.message, error.cause),
            )
        is Result.Loading -> AudioResult.Loading
    }

/**
 * Extension to handle both old Result<T> (with Throwable) and new Result<T, AppError>.
 * This allows gradual migration.
 */
public fun <T> Result<T, *>.getDataOrNull(): T? =
    when (this) {
        is Result.Success -> data
        else -> null
    }

/**
 * Extension to get error message from Result.
 * Works with new Result<T, AppError> type.
 */
public fun <E : AppError> Result<*, E>.getErrorMessageOrNull(): String? =
    when (this) {
        is Result.Error -> error.message
        else -> null
    }
