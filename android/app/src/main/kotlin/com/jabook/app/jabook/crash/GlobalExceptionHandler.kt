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

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.util.LogUtils
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Handles uncaught exceptions by writing crash report to disk.
 *
 * On next app startup, [CrashActivity] is launched if a report exists.
 * This is more reliable than trying to launch UI from a dying process.
 */
public class GlobalExceptionHandler(
    private val application: Application,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    private val prefs by lazy {
        application.getSharedPreferences("jabook_crash_handler", Context.MODE_PRIVATE)
    }

    public companion object {
        private const val CRASH_LOOP_THRESHOLD_MS: Long = 60_000L
        private const val MAX_CONSECUTIVE_CRASHES: Int = 3
        private const val CRASH_REPORT_FILE = "last_crash_report.txt"
        private const val CRASH_REPORT_MARKER = "has_crash_report"
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            LogUtils.e("GlobalExceptionHandler", "Uncaught exception", throwable)
            CrashDiagnostics.reportUncaughtException(
                threadName = thread.name,
                throwable = throwable,
                attributes = mapOf("source" to "global_exception_handler"),
            )

            val now = System.currentTimeMillis()
            val lastCrashTime = prefs.getLong("last_crash_time", 0L)
            val crashCount = prefs.getInt("crash_count", 0)

            if (now - lastCrashTime < CRASH_LOOP_THRESHOLD_MS && crashCount >= MAX_CONSECUTIVE_CRASHES) {
                LogUtils.e(
                    "GlobalExceptionHandler",
                    "Crash loop detected ($crashCount crashes in ${CRASH_LOOP_THRESHOLD_MS}ms), breaking loop",
                )
                prefs.edit().remove("last_crash_time").remove("crash_count").remove(CRASH_REPORT_MARKER).apply()
                deleteCrashReport()
                defaultHandler?.uncaughtException(thread, throwable) ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
                return
            }

            val newCount = if (now - lastCrashTime < CRASH_LOOP_THRESHOLD_MS) crashCount + 1 else 1
            prefs.edit()
                .putLong("last_crash_time", now)
                .putInt("crash_count", newCount)
                .apply()

            writeCrashReport(thread, throwable)

            defaultHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        try {
            val report = buildCrashReport(thread, throwable)
            val file = File(application.filesDir, CRASH_REPORT_FILE)
            file.writeText(report)
            prefs.edit().putBoolean(CRASH_REPORT_MARKER, true).apply()
        } catch (e: Exception) {
            LogUtils.e("GlobalExceptionHandler", "Failed to write crash report", e)
        }
    }

    private fun deleteCrashReport() {
        try {
            File(application.filesDir, CRASH_REPORT_FILE).delete()
        } catch (_: Exception) {}
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String = buildString {
        appendLine("=== JaBook Crash Report ===")
        appendLine("Time: ${java.util.Date()}")
        appendLine("Thread: ${thread.name}")
        appendLine("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) flavor=${BuildConfig.FLAVOR}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine("Stack trace:")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        appendLine(sw.toString())
    }
}
