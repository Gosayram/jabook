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

package com.jabook.app.jabook.compose.core.util

/**
 * Safely parse a string into an enum constant, returning [default] if the value
 * is null, empty, or doesn't match any constant.
 *
 * Eliminates the repeated `try { Enum.valueOf(s) } catch (_: Exception) { default }` pattern.
 */
public inline fun <reified T : Enum<T>> String?.safeEnum(default: T): T =
    if (!isNullOrEmpty()) {
        try {
            enumValueOf(this)
        } catch (_: IllegalArgumentException) {
            default
        }
    } else {
        default
    }
