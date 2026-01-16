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

package com.jabook.app.jabook.util

import android.util.Log
import com.jabook.app.jabook.BuildConfig

/**
 * Centralized logger that handles log visibility based on build flavor.
 *
 * - Beta/Dev/Stage: All logs enabled (VERBOSE, DEBUG, INFO, WARN, ERROR)
 * - Prod: Only WARN and ERROR enabled
 */
public object LogUtils {
    private const val FLAVOR_PROD = "prod"
    private const val FLAVOR_BETA = "beta"

    // Check if we should log debug/info/verbose messages
    // Rely on DEBUG flag which is true for dev, beta, stage types usually
    private val isDebugLoggingEnabled: Boolean = BuildConfig.DEBUG

    /**
     * Log a VERBOSE message
     */
    public fun v(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (isDebugLoggingEnabled) {
            if (throwable != null) {
                Log.v(tag, message, throwable)
            } else {
                Log.v(tag, message)
            }
        }
    }

    /**
     * Log a DEBUG message
     */
    public fun d(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (isDebugLoggingEnabled) {
            if (throwable != null) {
                Log.d(tag, message, throwable)
            } else {
                Log.d(tag, message)
            }
        }
    }

    /**
     * Log a DEBUG message with a lazy message supplier (avoid string construction cost)
     */
    public fun d(
        tag: String,
        message: () -> String,
    ) {
        if (isDebugLoggingEnabled) {
            Log.d(tag, message())
        }
    }

    /**
     * Log an INFO message
     */
    public fun i(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (isDebugLoggingEnabled) {
            if (throwable != null) {
                Log.i(tag, message, throwable)
            } else {
                Log.i(tag, message)
            }
        }
    }

    /**
     * Log a WARNING message (Always logged)
     */
    public fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    /**
     * Log an ERROR message (Always logged)
     */
    public fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
