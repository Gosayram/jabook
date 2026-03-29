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
import android.content.Intent
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Handles uncaught exceptions by launching the CrashActivity.
 */
public class GlobalExceptionHandler(
    private val application: Application,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            // Log the exception
            android.util.Log.e("GlobalExceptionHandler", "Uncaught exception", throwable)
            CrashDiagnostics.reportUncaughtException(
                threadName = thread.name,
                throwable = throwable,
                attributes = mapOf("source" to "global_exception_handler"),
            )

            // Launch CrashActivity
            val intent =
                Intent(application, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, getStackTrace(throwable))
                }
            application.startActivity(intent)

            // Kill the process
            Process.killProcess(Process.myPid())
            exitProcess(10)
        } catch (e: Exception) {
            // If the crash handler itself crashes, fall back to default
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}
