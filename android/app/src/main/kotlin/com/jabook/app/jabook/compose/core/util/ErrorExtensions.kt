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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.remote.RuTrackerError

/**
 * Extension functions for error handling in UI.
 *
 * Based on Flow project analysis - provides centralized error message
 * and icon mapping for different error types.
 */

/**
 * Get string resource ID for error title.
 */
@StringRes
public fun Throwable?.getErrorTitleRes(): Int = R.string.error_title

/**
 * Get string resource ID for error message.
 */
@StringRes
public fun Throwable?.getStringRes(): Int =
    when (this) {
        is RuTrackerError.Unauthorized -> R.string.notAuthenticated
        is RuTrackerError.NoData -> R.string.noDataAvailable
        is RuTrackerError.NoConnection -> R.string.noConnection
        is RuTrackerError.NotFound -> R.string.notFound
        is RuTrackerError.BadRequest -> R.string.badRequest
        is RuTrackerError.Forbidden -> R.string.forbidden
        is RuTrackerError.ParsingError -> R.string.parsingError
        is RuTrackerError.Unknown -> R.string.unknownError
        else -> R.string.error_something_goes_wrong
    }

/**
 * Get error message string.
 */
public fun Throwable?.getErrorMessage(): String =
    when (this) {
        is RuTrackerError.Unauthorized -> "Authentication required"
        is RuTrackerError.NoData -> "No data available"
        is RuTrackerError.NoConnection -> "No network connection"
        is RuTrackerError.NotFound -> "Resource not found"
        is RuTrackerError.BadRequest -> "Bad request"
        is RuTrackerError.Forbidden -> "Access forbidden"
        is RuTrackerError.ParsingError -> this.message
        is RuTrackerError.Unknown -> this.message
        else -> this?.message ?: "Something went wrong"
    }

/**
 * Get drawable resource ID for error illustration.
 *
 * Note: If custom illustrations are not available, returns 0
 * and UI should handle it gracefully.
 */
@DrawableRes
public fun Throwable?.getIllRes(): Int = 0 // Custom illustrations not yet implemented
