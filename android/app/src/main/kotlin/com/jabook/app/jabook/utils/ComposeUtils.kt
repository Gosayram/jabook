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

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.job

/**
 * Compose utilities (inspired by Flow pattern).
 *
 * Provides useful Compose functions for common UI operations.
 */

/**
 * Executes a block of code only on the first composition.
 *
 * Useful for one-time initialization that should happen when a composable
 * is first displayed, such as requesting focus, loading initial data, etc.
 *
 * The block is executed after the composition completes successfully.
 * If the composition fails, the block is not executed.
 *
 * @param block The code block to execute on first composition
 *
 * Example:
 * ```kotlin
 * RunOnFirstComposition {
 *     focusRequester.requestFocus()
 *     viewModel.loadInitialData()
 * }
 * ```
 */
@Composable
public fun RunOnFirstComposition(block: () -> Unit) {
    LaunchedEffect(Unit) {
        coroutineContext.job.invokeOnCompletion { error ->
            if (error == null) {
                block()
            }
        }
    }
}

/**
 * Remembers the system bar style based on the current theme.
 *
 * Automatically adapts to light/dark theme changes and provides
 * appropriate system bar styling for transparent backgrounds.
 *
 * @return SystemBarStyle configured for the current theme
 *
 * Example:
 * ```kotlin
 * val systemBarStyle = rememberSystemBarStyle()
 * // Use with enableEdgeToEdge() or similar
 * ```
 */
@Composable
public fun rememberSystemBarStyle(): SystemBarStyle {
    // Use isSystemInDarkTheme to detect dark theme (simpler and more reliable)
    val isDark = isSystemInDarkTheme()

    return remember(isDark) {
        if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    }
}

/**
 * Extension property to get ComponentActivity from any Context.
 *
 * Useful when you need to access Activity-specific features from
 * a Context that might be wrapped (e.g., in a ViewHolder, Service, etc.).
 *
 * @return The ComponentActivity instance
 * @throws IllegalStateException if no ComponentActivity is found in the context chain
 *
 * Example:
 * ```kotlin
 * val activity = context.componentActivity
 * activity.startActivity(intent)
 * ```
 */
public val Context.componentActivity: ComponentActivity
    get() {
        return when (this) {
            is ComponentActivity -> this
            is ContextWrapper -> this.baseContext.componentActivity
            else -> error("No ComponentActivity found in context chain")
        }
    }
