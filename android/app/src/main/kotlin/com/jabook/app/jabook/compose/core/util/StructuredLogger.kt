// Copyright 2025 Jabook Contributors
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

import android.util.Log

/**
 * Structured logger for consistent logging across the application.
 *
 * Based on Flow project analysis - provides operation tracking,
 * timing information, and consistent log format.
 *
 * Usage:
 * ```kotlin
 * val logger = StructuredLogger("MyClass")
 * val operationId = logger.startOperation("fetchData")
 * // ... perform operation
 * logger.endOperation(operationId, success = true)
 * ```
 */
class StructuredLogger(
    private val tag: String,
) {
    private data class Operation(
        val id: String,
        val name: String,
        val startTime: Long,
    )

    private val activeOperations = mutableMapOf<String, Operation>()

    /**
     * Start a new operation and return operation ID.
     *
     * @param operationName Name of the operation
     * @param operationId Optional operation ID (for correlation with parent operations)
     * @return Operation ID for tracking
     */
    fun startOperation(
        operationName: String,
        operationId: String? = null,
    ): String {
        val id = operationId ?: "${operationName}_${System.currentTimeMillis()}"
        val startTime = System.currentTimeMillis()
        activeOperations[id] = Operation(id, operationName, startTime)
        Log.d(tag, "[$id] $operationName started")
        return id
    }

    /**
     * End an operation and log duration.
     *
     * @param operationId Operation ID returned from startOperation
     * @param success Whether operation succeeded
     * @param additionalInfo Optional additional information to log
     */
    fun endOperation(
        operationId: String,
        success: Boolean = true,
        additionalInfo: String? = null,
    ) {
        val operation = activeOperations.remove(operationId)
        val duration =
            if (operation != null) {
                System.currentTimeMillis() - operation.startTime
            } else {
                0L
            }
        val status = if (success) "✅" else "❌"
        val message =
            buildString {
                append("[$operationId] ")
                append(if (success) "completed" else "failed")
                append(" (${duration}ms)")
                if (additionalInfo != null) {
                    append(": $additionalInfo")
                }
            }
        if (success) {
            Log.i(tag, "$status $message")
        } else {
            Log.w(tag, "$status $message")
        }
    }

    /**
     * Log operation with timing information.
     *
     * @param operationId Operation ID
     * @param message Log message
     * @param level Log level (default: DEBUG)
     */
    fun log(
        operationId: String,
        message: String,
        level: LogLevel = LogLevel.DEBUG,
    ) {
        val logMessage = "[$operationId] $message"
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, logMessage)
            LogLevel.DEBUG -> Log.d(tag, logMessage)
            LogLevel.INFO -> Log.i(tag, logMessage)
            LogLevel.WARN -> Log.w(tag, logMessage)
            LogLevel.ERROR -> Log.e(tag, logMessage)
        }
    }

    /**
     * Log operation with timing information.
     *
     * @param operationId Operation ID
     * @param message Log message
     * @param duration Duration in milliseconds
     * @param level Log level (default: DEBUG)
     */
    fun logWithDuration(
        operationId: String,
        message: String,
        duration: Long,
        level: LogLevel = LogLevel.DEBUG,
    ) {
        val logMessage = "[$operationId] $message (${duration}ms)"
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, logMessage)
            LogLevel.DEBUG -> Log.d(tag, logMessage)
            LogLevel.INFO -> Log.i(tag, logMessage)
            LogLevel.WARN -> Log.w(tag, logMessage)
            LogLevel.ERROR -> Log.e(tag, logMessage)
        }
    }

    /**
     * Log error with operation context.
     *
     * @param operationId Operation ID
     * @param message Error message
     * @param throwable Optional throwable
     */
    fun logError(
        operationId: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val logMessage = "[$operationId] ❌ $message"
        if (throwable != null) {
            Log.e(tag, logMessage, throwable)
        } else {
            Log.e(tag, logMessage)
        }
    }

    /**
     * Log warning with operation context.
     *
     * @param operationId Operation ID
     * @param message Warning message
     */
    fun logWarning(
        operationId: String,
        message: String,
    ) {
        Log.w(tag, "[$operationId] ⚠️ $message")
    }

    /**
     * Log success with operation context.
     *
     * @param operationId Operation ID
     * @param message Success message
     * @param duration Optional duration in milliseconds
     */
    fun logSuccess(
        operationId: String,
        message: String,
        duration: Long? = null,
    ) {
        val logMessage =
            buildString {
                append("[$operationId] ✅ $message")
                if (duration != null) {
                    append(" (${duration}ms)")
                }
            }
        Log.i(tag, logMessage)
    }

    /**
     * Execute operation with automatic logging.
     *
     * @param operationName Name of the operation
     * @param operationId Optional operation ID
     * @param block Operation to execute
     * @return Result of the operation
     */
    suspend fun <T> withOperation(
        operationName: String,
        operationId: String? = null,
        block: suspend (String) -> T,
    ): T {
        val id = startOperation(operationName, operationId)
        val startTime = System.currentTimeMillis()
        return try {
            val result = block(id)
            val duration = System.currentTimeMillis() - startTime
            logSuccess(id, "$operationName completed", duration)
            result
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            logError(id, "$operationName failed (${duration}ms)", e)
            throw e
        }
    }

    /**
     * Execute operation with automatic logging (non-suspend version).
     *
     * @param operationName Name of the operation
     * @param operationId Optional operation ID
     * @param block Operation to execute
     * @return Result of the operation
     */
    fun <T> withOperationSync(
        operationName: String,
        operationId: String? = null,
        block: (String) -> T,
    ): T {
        val id = startOperation(operationName, operationId)
        val startTime = System.currentTimeMillis()
        return try {
            val result = block(id)
            val duration = System.currentTimeMillis() - startTime
            logSuccess(id, "$operationName completed", duration)
            result
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            logError(id, "$operationName failed (${duration}ms)", e)
            throw e
        }
    }

    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }
}
