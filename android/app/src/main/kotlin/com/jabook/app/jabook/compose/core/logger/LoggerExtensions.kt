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

package com.jabook.app.jabook.compose.core.logger

import java.util.UUID

/**
 * Extensions for Logger to support structured logging with operation IDs.
 * These extensions adapt the legacy execution tracking API to the simpler Logger interface.
 */

/**
 * Starts an operation and logs its beginning.
 * @param name Name of the operation
 * @param id Optional explicit ID. If null, a random UUID is generated.
 * @return The operation ID to be used in subsequent log calls.
 */
public fun Logger.startOperation(name: String, id: String? = null): String {
    val opId = id ?: UUID.randomUUID().toString()
    d { "[$opId] START: $name" }
    return opId
}

/**
 * Logs the end of an operation.
 */
public fun Logger.endOperation(operationId: String, success: Boolean = true, message: String? = null) {
    if (success) {
        d { "[$operationId] END: Success${if (message != null) " - $message" else ""}" }
    } else {
        w { "[$operationId] END: Failed${if (message != null) " - $message" else ""}" }
    }
}

/**
 * Logs a message associated with an operation.
 */
public fun Logger.log(operationId: String, message: String, level: LogLevel = LogLevel.DEBUG) {
    when (level) {
        LogLevel.VERBOSE -> v { "[$operationId] $message" }
        LogLevel.DEBUG -> d { "[$operationId] $message" }
        LogLevel.INFO -> i { "[$operationId] $message" }
        LogLevel.WARN -> w { "[$operationId] $message" }
        LogLevel.ERROR -> e { "[$operationId] $message" }
        LogLevel.NONE -> {}
    }
}

/**
 * Overload for simple log without level (defaults to DEBUG).
 */
public fun Logger.log(operationId: String, message: String) {
    d { "[$operationId] $message" }
}

/**
 * Logs a success message for an operation.
 */
public fun Logger.logSuccess(operationId: String, message: String, durationMs: Long? = null) {
    i { "[$operationId] ✅ $message${if (durationMs != null) " (${durationMs}ms)" else ""}" }
}

/**
 * Logs an error message for an operation.
 */
public fun Logger.logError(operationId: String, message: String, throwable: Throwable? = null) {
    e(throwable, { "[$operationId] ❌ $message" })
}

/**
 * Logs a warning message for an operation.
 */
public fun Logger.logWarning(operationId: String, message: String) {
    w { "[$operationId] ⚠️ $message" }
}

/**
 * Logs a message with duration.
 */
public fun Logger.logWithDuration(operationId: String, message: String, durationMs: Long) {
    d { "[$operationId] $message (${durationMs}ms)" }
}

/**
 * Executes a block within a tracked operation scope.
 */
public inline fun <T> Logger.withOperation(name: String, block: (String) -> T): T {
    val opId = startOperation(name)
    val startTime = System.currentTimeMillis()
    return try {
        val result = block(opId)
        val duration = System.currentTimeMillis() - startTime
        logSuccess(opId, "Completed", duration)
        result
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        logError(opId, "Failed after ${duration}ms: ${e.message}", e)
        throw e
    }
}
