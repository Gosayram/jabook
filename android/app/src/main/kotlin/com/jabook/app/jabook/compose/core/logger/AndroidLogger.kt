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

import android.util.Log
import com.jabook.app.jabook.BuildConfig

/**
 * Log level for filtering logs.
 */
public enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE,
}

/**
 * Android implementation of Logger using android.util.Log.
 *
 * Provides lazy evaluation - messages are only evaluated if logging is enabled.
 */
public class AndroidLogger(
    private val tag: String,
    private val minLevel: LogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR,
) : Logger {
    override fun d(message: () -> String): Unit = log(LogLevel.DEBUG, message, null)
    override fun d(message: () -> String, throwable: Throwable?): Unit = log(LogLevel.DEBUG, message, throwable)
    override fun d(throwable: Throwable?, message: () -> String): Unit = log(LogLevel.DEBUG, message, throwable)

    override fun e(message: () -> String): Unit = log(LogLevel.ERROR, message, null)
    override fun e(message: () -> String, throwable: Throwable?): Unit = log(LogLevel.ERROR, message, throwable)
    override fun e(throwable: Throwable?, message: () -> String): Unit = log(LogLevel.ERROR, message, throwable)

    override fun i(message: () -> String): Unit = log(LogLevel.INFO, message, null)
    override fun i(message: () -> String, throwable: Throwable?): Unit = log(LogLevel.INFO, message, throwable)
    override fun i(throwable: Throwable?, message: () -> String): Unit = log(LogLevel.INFO, message, throwable)

    override fun w(message: () -> String): Unit = log(LogLevel.WARN, message, null)
    override fun w(message: () -> String, throwable: Throwable?): Unit = log(LogLevel.WARN, message, throwable)
    override fun w(throwable: Throwable?, message: () -> String): Unit = log(LogLevel.WARN, message, throwable)

    override fun v(message: () -> String): Unit = log(LogLevel.VERBOSE, message, null)
    override fun v(message: () -> String, throwable: Throwable?): Unit = log(LogLevel.VERBOSE, message, throwable)
    override fun v(throwable: Throwable?, message: () -> String): Unit = log(LogLevel.VERBOSE, message, throwable)

    private fun log(level: LogLevel, message: () -> String, throwable: Throwable?) {
        if (minLevel <= level) {
            val msg = message()
            when (level) {
                LogLevel.DEBUG -> if (throwable != null) Log.d(tag, msg, throwable) else Log.d(tag, msg)
                LogLevel.INFO -> if (throwable != null) Log.i(tag, msg, throwable) else Log.i(tag, msg)
                LogLevel.WARN -> if (throwable != null) Log.w(tag, msg, throwable) else Log.w(tag, msg)
                LogLevel.ERROR -> if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
                LogLevel.VERBOSE -> if (throwable != null) Log.v(tag, msg, throwable) else Log.v(tag, msg)
                LogLevel.NONE -> {}
            }
        }
    }
}

/**
 * No-op implementation of Logger for production builds.
 */
public object NoOpLogger : Logger {
    override fun d(message: () -> String) {}
    override fun d(message: () -> String, throwable: Throwable?) {}
    override fun d(throwable: Throwable?, message: () -> String) {}

    override fun e(message: () -> String) {}
    override fun e(message: () -> String, throwable: Throwable?) {}
    override fun e(throwable: Throwable?, message: () -> String) {}

    override fun i(message: () -> String) {}
    override fun i(message: () -> String, throwable: Throwable?) {}
    override fun i(throwable: Throwable?, message: () -> String) {}

    override fun w(message: () -> String) {}
    override fun w(message: () -> String, throwable: Throwable?) {}
    override fun w(throwable: Throwable?, message: () -> String) {}

    override fun v(message: () -> String) {}
    override fun v(message: () -> String, throwable: Throwable?) {}
    override fun v(throwable: Throwable?, message: () -> String) {}
}

/**
 * No-op implementation of LoggerFactory for production builds.
 */
public object NoOpLoggerFactory : LoggerFactory {
    override fun get(tag: String): Logger = NoOpLogger

    override fun get(clazz: kotlin.reflect.KClass<*>): Logger = NoOpLogger
}

/**
 * Android implementation of LoggerFactory.
 */
public class LoggerFactoryImpl(
    private val minLevel: LogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR,
) : LoggerFactory {
    private val loggers = mutableMapOf<String, Logger>()

    override fun get(tag: String): Logger =
        loggers.getOrPut(tag) {
            AndroidLogger(tag, minLevel)
        }

    override fun get(clazz: kotlin.reflect.KClass<*>): Logger = get(clazz.simpleName ?: "Unknown")
}
