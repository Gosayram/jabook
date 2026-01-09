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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart

/**
 * Flow extension utilities (inspired by Flow pattern).
 *
 * Provides useful extension functions for working with Flow and reactive programming.
 */

/**
 * Creates a Flow that emits the current value on start (inspired by Flow pattern).
 *
 * Useful for state that should emit its current value immediately when observed,
 * such as settings, preferences, or cached data.
 *
 * @param getCurrentValue Suspending function to get the current value
 * @return A Flow that emits current value on start, then updates from the source
 *
 * Example:
 * ```kotlin
 * private val mutableSettings = SingleItemMutableSharedFlow<Settings>()
 * val settings: Flow<Settings> = mutableSettings
 *     .asSharedFlow()
 *     .onStartWithCurrent { getSettings() }
 * ```
 */
fun <T> SharedFlow<T>.onStartWithCurrent(getCurrentValue: suspend () -> T): Flow<T> = onStart { emit(getCurrentValue()) }

/**
 * Creates a Flow that emits the current value on start and then continues from source.
 *
 * Similar to onStartWithCurrent, but works with any Flow, not just SharedFlow.
 *
 * @param getCurrentValue Suspending function to get the current value
 * @return A Flow that emits current value on start, then continues from source
 *
 * Example:
 * ```kotlin
 * val settingsFlow = settingsRepository.observeSettings()
 *     .onStartWithCurrent { settingsRepository.getSettings() }
 * ```
 */
fun <T> Flow<T>.onStartWithCurrent(getCurrentValue: suspend () -> T): Flow<T> = onStart { emit(getCurrentValue()) }

/**
 * Catches exceptions and emits a default value instead of failing.
 *
 * Useful when you want to provide a fallback value on error instead of
 * propagating the exception.
 *
 * @param defaultValue The value to emit on error
 * @return A Flow that emits defaultValue on error
 *
 * Example:
 * ```kotlin
 * val dataFlow = repository.getData()
 *     .catchWithDefault(emptyList())
 * ```
 */
fun <T> Flow<T>.catchWithDefault(defaultValue: T): Flow<T> = catch { emit(defaultValue) }
