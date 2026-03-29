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

package com.jabook.app.jabook.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jabook.app.jabook.BuildConfig

/**
 * Centralized helpers for runtime crash diagnostics.
 *
 * Includes:
 * - lightweight playback context keys
 * - non-fatal reporting for recoverable failures
 */
public object CrashDiagnostics {
    private const val VALUE_LIMIT = 120
    private const val MAX_EXTRA_ATTRIBUTES = 8

    internal var sinkFactory: () -> CrashDiagnosticsSink = { FirebaseCrashDiagnosticsSink() }
    internal var isEnabledOverride: Boolean? = null

    public fun setPlaybackContext(
        bookTitle: String?,
        playerState: String?,
        playbackSpeed: Float?,
        sleepMode: String?,
    ) {
        if (!isEnabled()) return
        safeRun {
            val sink = sinkFactory()
            sink.setCustomKey("current_book", sanitize(bookTitle))
            sink.setCustomKey("player_state", sanitize(playerState))
            sink.setCustomKey("playback_speed", playbackSpeed?.toString() ?: "unknown")
            sink.setCustomKey("sleep_mode", sanitize(sleepMode))
        }
    }

    public fun configureRuntimeContext(
        buildType: String,
        flavor: String,
        versionName: String,
        versionCode: Long,
    ) {
        if (!isEnabled()) return
        safeRun {
            val sink = sinkFactory()
            sink.setCustomKey("build_type", sanitize(buildType))
            sink.setCustomKey("flavor", sanitize(flavor))
            sink.setCustomKey("version_name", sanitize(versionName))
            sink.setCustomKey("version_code", versionCode.toString())
            sink.log("crash_diagnostics_initialized")
        }
    }

    public fun log(message: String) {
        if (!isEnabled()) return
        safeRun {
            sinkFactory().log(sanitize(message))
        }
    }

    public fun reportUncaughtException(
        threadName: String,
        throwable: Throwable,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        if (!isEnabled()) return
        safeRun {
            val sink = sinkFactory()
            sink.setCustomKey("uncaught_thread_name", sanitize(threadName))
            attributes.entries
                .sortedBy { it.key }
                .take(MAX_EXTRA_ATTRIBUTES)
                .forEach { (key, value) ->
                    sink.setCustomKey("ue_${key.take(32)}", sanitize(value))
                }
            sink.log("uncaught_exception")
            sink.recordException(throwable)
        }
    }

    public fun reportNonFatal(
        tag: String,
        throwable: Throwable,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        if (!isEnabled()) return
        safeRun {
            val sink = sinkFactory()
            sink.setCustomKey("non_fatal_tag", sanitize(tag))
            attributes.entries
                .sortedBy { it.key }
                .take(MAX_EXTRA_ATTRIBUTES)
                .forEach { (key, value) ->
                    sink.setCustomKey("nf_${key.take(32)}", sanitize(value))
                }
            sink.log("non_fatal:$tag")
            sink.recordException(throwable)
        }
    }

    internal fun isEnabled(): Boolean = isEnabledOverride ?: (BuildConfig.HAS_GOOGLE_SERVICES && !BuildConfig.DEBUG)

    private fun sanitize(value: Any?): String {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return "unknown"
        return if (text.length <= VALUE_LIMIT) text else text.take(VALUE_LIMIT)
    }

    private inline fun safeRun(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // Never crash app from diagnostics path.
        }
    }
}

internal interface CrashDiagnosticsSink {
    fun setCustomKey(
        key: String,
        value: String,
    )

    fun recordException(throwable: Throwable)

    fun log(message: String)
}

private class FirebaseCrashDiagnosticsSink : CrashDiagnosticsSink {
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        crashlytics.setCustomKey(key, value)
    }

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }
}
