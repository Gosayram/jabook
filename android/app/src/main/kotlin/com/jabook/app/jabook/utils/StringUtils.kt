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
 * String extension utilities (inspired by Flow pattern).
 *
 * Provides useful extension functions for working with strings and null safety.
 */

/**
 * Returns the string if it's not null and not blank, otherwise returns the default value.
 *
 * Useful for providing fallback values when dealing with nullable strings.
 *
 * @param default The default value to return if string is null or blank
 * @return The string if not null/blank, otherwise the default value
 *
 * Example:
 * ```kotlin
 * val title = metadata?.get("title").orIfBlank("Unknown Title")
 * ```
 */
public fun String?.orIfBlank(default: String): String = if (this.isNullOrBlank()) default else this

/**
 * Returns the string if it's not null and not empty, otherwise returns the default value.
 *
 * Similar to orIfBlank, but only checks for empty (not blank).
 * Useful when you want to preserve whitespace-only strings.
 *
 * @param default The default value to return if string is null or empty
 * @return The string if not null/empty, otherwise the default value
 *
 * Example:
 * ```kotlin
 * val artist = metadata?.get("artist").orIfEmpty("Unknown Artist")
 * ```
 */
public fun String?.orIfEmpty(default: String): String = if (this.isNullOrEmpty()) default else this

/**
 * Formats a flavor suffix for display in titles.
 *
 * If the suffix is empty, returns empty string.
 * Otherwise, returns the suffix prefixed with " - ".
 *
 * @return Formatted flavor suffix (e.g., " - Dev" or "")
 *
 * Example:
 * ```kotlin
 * val flavor = "dev"
 * val title = "My App${flavor.formatFlavorSuffix()}" // "My App - Dev"
 * ```
 */
public fun String.formatFlavorSuffix(): String = takeIf { isNotEmpty() }?.let { " - $it" }.orEmpty()

/**
 * Appends a flavor suffix to a base name if the suffix is not empty.
 *
 * @param flavorSuffix The flavor suffix to append
 * @return Base name with flavor suffix if suffix is not empty, otherwise just base name
 *
 * Example:
 * ```kotlin
 * val baseName = "jabook"
 * val flavor = "dev"
 * val fullName = baseName.appendFlavorSuffix(flavor) // "jabook - dev"
 * ```
 */
public fun String.appendFlavorSuffix(flavorSuffix: String): String = if (flavorSuffix.isEmpty()) this else "$this - $flavorSuffix"

/**
 * Capitalizes the first letter of the string if not null/empty.
 *
 * Returns empty string if the string is null or empty.
 *
 * @return Capitalized string or empty string
 *
 * Example:
 * ```kotlin
 * "hello".capitalizeFirst() // "Hello"
 * "".capitalizeFirst() // ""
 * null.capitalizeFirst() // ""
 * ```
 */
public fun String?.capitalizeFirst(): String =
    when {
        this.isNullOrEmpty() -> ""
        this.length == 1 -> this.uppercase()
        else -> this.substring(0, 1).uppercase() + this.substring(1)
    }
