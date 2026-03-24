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

/**
 * Nullable extension utilities (inspired by Flow pattern).
 *
 * Provides useful extension functions for working with nullable values
 * and safer null handling.
 */

/**
 * Executes the action if the value is not null.
 *
 * @param action Function to execute with the non-null value
 * @return The original value unchanged
 *
 * Example:
 * ```kotlin
 * user?.let { println(it.name) }
 * // Can be written as:
 * user.ifNotNull { println(it.name) }
 * ```
 */
public inline fun <T> T?.ifNotNull(action: (T) -> Unit): T? {
    if (this != null) {
        action(this)
    }
    return this
}

/**
 * Executes the action if the value is null.
 *
 * @param action Function to execute when value is null
 * @return The original value unchanged
 *
 * Example:
 * ```kotlin
 * user.ifNull { println("User is null") }
 * ```
 */
public inline fun <T> T?.ifNull(action: () -> Unit): T? {
    if (this == null) {
        action()
    }
    return this
}

/**
 * Returns the value if not null, or throws the provided exception.
 *
 * @param exceptionProvider Function that provides the exception to throw
 * @return The non-null value
 * @throws Throwable The exception provided by exceptionProvider
 *
 * Example:
 * ```kotlin
 * val userId = user?.id ?: throw IllegalArgumentException("User is required")
 * // Can be written as:
 * val userId = user?.id.requireNotNull { IllegalArgumentException("User is required") }
 * ```
 */
public inline fun <T> T?.requireNotNull(exceptionProvider: () -> Throwable): T = this ?: throw exceptionProvider()

/**
 * Returns the value if not null, or returns the default value.
 *
 * More concise than `value ?: defaultValue`.
 *
 * @param defaultValue The default value to return if null
 * @return The value if not null, or the default value
 *
 * Example:
 * ```kotlin
 * val name = user?.name.orDefault("Unknown")
 * ```
 */
public fun <T> T?.orDefault(defaultValue: T): T = this ?: defaultValue

/**
 * Returns the value if not null, or computes the default value.
 *
 * Useful when the default value is expensive to compute.
 *
 * @param defaultValue Function that computes the default value
 * @return The value if not null, or the computed default value
 *
 * Example:
 * ```kotlin
 * val config = cachedConfig.orDefaultLazy { loadConfigFromDisk() }
 * ```
 */
public inline fun <T> T?.orDefaultLazy(defaultValue: () -> T): T = this ?: defaultValue()
