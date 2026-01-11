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
    override fun d(message: () -> String) {
        if (minLevel <= LogLevel.DEBUG) {
            Log.d(tag, message())
        }
    }

    override fun e(message: () -> String, throwable: Throwable?) {
        if (minLevel <= LogLevel.ERROR) {
            if (throwable != null) {
                Log.e(tag, message(), throwable)
            } else {
                Log.e(tag, message())
            }
        }
    }

    override fun i(message: () -> String) {
        if (minLevel <= LogLevel.INFO) {
            Log.i(tag, message())
        }
    }

    override fun w(message: () -> String) {
        if (minLevel <= LogLevel.WARN) {
            Log.w(tag, message())
        }
    }

    override fun v(message: () -> String) {
        if (minLevel <= LogLevel.VERBOSE) {
            Log.v(tag, message())
        }
    }
}

/**
 * No-op implementation of Logger for production builds.
 */
public object NoOpLogger : Logger {
    override fun d(message: () -> String) {}
    override fun e(message: () -> String, throwable: Throwable?) {}
    override fun i(message: () -> String) {}
    override fun w(message: () -> String) {}
    override fun v(message: () -> String) {}
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

    override fun get(tag: String): Logger {
        return loggers.getOrPut(tag) {
            AndroidLogger(tag, minLevel)
        }
    }

    override fun get(clazz: kotlin.reflect.KClass<*>): Logger {
        return get(clazz.simpleName ?: "Unknown")
    }
}
