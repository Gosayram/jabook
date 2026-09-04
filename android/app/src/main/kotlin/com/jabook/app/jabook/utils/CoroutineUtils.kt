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

import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Creates a [CoroutineExceptionHandler] that logs uncaught exceptions via [LogUtils]
 * and reports them to [CrashDiagnostics].
 *
 * Without an explicit handler, exceptions that escape a `SupervisorJob`-based scope
 * are silently dropped on Android (no crash, no log). This handler guarantees that
 * every uncaught exception is at least logged at ERROR level — and surfaced as a
 * non-fatal in production — making debugging significantly easier.
 *
 * @param tag Log tag used in [LogUtils.e]. Defaults to "CoroutineException".
 * @return A [CoroutineExceptionHandler] suitable for inclusion in a scope context.
 *
 * Usage:
 * ```kotlin
 * private val scope = CoroutineScope(
 *     SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("MyService")
 * )
 * ```
 */
public fun loggingCoroutineExceptionHandler(tag: String = "CoroutineException"): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        LogUtils.e(tag, "Uncaught coroutine exception", throwable)
        CrashDiagnostics.reportNonFatal(tag, throwable, mapOf("scope" to tag))
    }
