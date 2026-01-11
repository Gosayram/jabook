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
     * Message is evaluated lazily (only if logging is enabled).
     */
    public fun d(message: () -> String)

    /**
     * Log error message.
     * Message is evaluated lazily (only if logging is enabled).
     *
     * @param throwable Optional throwable to log
     */
    public fun e(message: () -> String, throwable: Throwable? = null)

    /**
     * Log info message.
     * Message is evaluated lazily (only if logging is enabled).
     */
    public fun i(message: () -> String)

    /**
     * Log warning message.
     * Message is evaluated lazily (only if logging is enabled).
     */
    public fun w(message: () -> String)

    /**
     * Log verbose message.
     * Message is evaluated lazily (only if logging is enabled).
     */
    public fun v(message: () -> String)
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
