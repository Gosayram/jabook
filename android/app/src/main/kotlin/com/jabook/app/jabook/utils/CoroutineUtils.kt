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
fun CoroutineScope.newCancelableScope(): CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

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
fun CoroutineScope.relaunch(block: suspend CoroutineScope.() -> Unit) {
    coroutineContext.cancelChildren()
    launch(block = block)
}
