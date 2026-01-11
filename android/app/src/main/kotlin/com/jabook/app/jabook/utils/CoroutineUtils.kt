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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * CoroutineScope extension utilities (inspired by Flow pattern).
 *
 * Provides useful extension functions for managing coroutine lifecycle
 * and simplifying common coroutine operations.
 */

/**
 * Creates a new cancelable scope from the current scope's context.
 * Useful for creating child scopes that can be cancelled independently.
 *
 * @return A new CoroutineScope with SupervisorJob for independent cancellation
 *
 * Example:
 * ```kotlin
 * val childScope = parentScope.newCancelableScope()
 * childScope.launch { /* work */ }
 * // Later: childScope.cancel() // Only cancels child scope, not parent
 * ```
 */
public fun CoroutineScope.newCancelableScope(): CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

/**
 * Relaunches a coroutine, cancelling all existing children first.
 * Useful for restarting operations when state changes.
 *
 * @param block The coroutine block to execute
 *
 * Example:
 * ```kotlin
 * scope.relaunch {
 *     // This will cancel any existing work in scope and start fresh
 *     loadData()
 * }
 * ```
 */
public fun CoroutineScope.relaunch(block: suspend CoroutineScope.() -> Unit) {
    coroutineContext.cancelChildren()
    launch(block = block)
}

/**
 * Creates a MutableSharedFlow that keeps only the latest value (inspired by Flow pattern).
 *
 * Useful for state that should only retain the most recent value, such as:
 * - Current settings
 * - Latest error message
 * - Current user preference
 *
 * When a new value is emitted and the buffer is full, the oldest value is dropped.
 * This ensures subscribers always get the latest state without missing updates.
 *
 * @return A MutableSharedFlow with buffer capacity of 1 and DROP_OLDEST strategy
 *
 * Example:
 * ```kotlin
 * private val mutableSettings = SingleItemMutableSharedFlow<Settings>()
 * val settings: SharedFlow<Settings> = mutableSettings.asSharedFlow()
 *
 * // Later:
 * mutableSettings.emit(newSettings) // Old value is automatically dropped
 * ```
 */
@Suppress("FunctionName")
public fun <T : Any> SingleItemMutableSharedFlow(): MutableSharedFlow<T> =
    MutableSharedFlow(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
