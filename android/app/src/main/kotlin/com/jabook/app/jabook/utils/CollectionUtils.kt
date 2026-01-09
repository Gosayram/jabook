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
 * Collection extension utilities (inspired by Flow pattern).
 *
 * Provides useful extension functions for working with collections.
 */

/**
 * Filters elements by type and maps them using the provided transform function.
 *
 * This is a convenient shorthand for `filterIsInstance<R>().map(transform)`.
 * Useful when you need to filter a list by type and transform the filtered elements.
 *
 * @param T The base type of the list elements
 * @param R The target type to filter and transform
 * @param Q The result type of the transformation
 * @param transform The transformation function to apply to filtered elements
 * @return A list of transformed elements of type Q
 *
 * Example:
 * ```kotlin
 * val items: List<Any> = listOf("hello", 42, "world", 100)
 * val strings = items.mapInstanceOf<String, String, String> { it.uppercase() }
 * // Result: ["HELLO", "WORLD"]
 * ```
 */
inline fun <reified T, reified R : T, Q> List<T>.mapInstanceOf(transform: (R) -> Q): List<Q> = filterIsInstance<R>().map(transform)
