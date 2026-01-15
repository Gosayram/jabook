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

/**
 * Logger interface for structured logging.
 *
 * Based on Flow project analysis - provides lazy evaluation
 * and structured logging capabilities.
 */
public interface Logger {
    /**
     * Log debug message.
     */
    public fun d(message: () -> String)
    public fun d(message: () -> String, throwable: Throwable?)
    public fun d(throwable: Throwable?, message: () -> String)

    /**
     * Log error message.
     */
    public fun e(message: () -> String)
    public fun e(message: () -> String, throwable: Throwable?)
    public fun e(throwable: Throwable?, message: () -> String)

    /**
     * Log info message.
     */
    public fun i(message: () -> String)
    public fun i(message: () -> String, throwable: Throwable?)
    public fun i(throwable: Throwable?, message: () -> String)

    /**
     * Log warning message.
     */
    public fun w(message: () -> String)
    public fun w(message: () -> String, throwable: Throwable?)
    public fun w(throwable: Throwable?, message: () -> String)

    /**
     * Log verbose message.
     */
    public fun v(message: () -> String)
    public fun v(message: () -> String, throwable: Throwable?)
    public fun v(throwable: Throwable?, message: () -> String)
}

/**
 * Factory for creating Logger instances.
 */
public interface LoggerFactory {
    /**
     * Get logger for the given tag.
     */
    public fun get(tag: String): Logger

    /**
     * Get logger for the given class.
     */
    public fun get(clazz: kotlin.reflect.KClass<*>): Logger
}
