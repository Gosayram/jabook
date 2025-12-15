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

package com.jabook.app.jabook.compose.data.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for collecting and exporting application logs.
 * Based on Flutter's StructuredLogger implementation.
 */
@Singleton
class DebugLogService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "DebugLogService"
            private const val LOG_FILE_PREFIX = "jabook_logs"
            private const val MAX_LOG_LINES = 5000
        }

        /**
         * Collects recent logs from logcat.
         * Returns log content as string.
         */
        suspend fun collectLogs(): String =
            withContext(Dispatchers.IO) {
                try {
                    val process =
                        Runtime.getRuntime().exec(
                            arrayOf(
                                "logcat",
                                "-d", // dump and exit
                                "-v",
                                "time", // timestamp format
                                "-t",
                                MAX_LOG_LINES.toString(), // last N lines
                                TAG + ":V", // our app logs (verbose)
                                "*:E", // all errors
                            ),
                        )

                    val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                    val logs = StringBuilder()

                    // Add header with comprehensive device info
                    logs.append("╔════════════════════════════════════════════╗\n")
                    logs.append("║       JABOOK DEBUG LOGS & DIAGNOSTICS      ║\n")
                    logs.append("╚════════════════════════════════════════════╝\n\n")
                    
                    val currentDate = Date()
                    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    logs.append("📅 Captured: ${dateFormatter.format(currentDate)}\n")
                    logs.append("📦 Package: ${context.packageName}\n")
                    logs.append("🔖 Version: ${getAppVersion()}\n\n")
                    
                    logs.append("📱 DEVICE INFORMATION\n")
                    logs.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    logs.append("Manufacturer: ${android.os.Build.MANUFACTURER}\n")
                    logs.append("Model: ${android.os.Build.MODEL}\n")
                    logs.append("Brand: ${android.os.Build.BRAND}\n")
                    logs.append("Product: ${android.os.Build.PRODUCT}\n")
                    logs.append("Board: ${android.os.Build.BOARD}\n")
                    logs.append("Hardware: ${android.os.Build.HARDWARE}\n")
                    logs.append("Build: ${android.os.Build.FINGERPRINT}\n")
                    logs.append("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n\n")

                    logs.append("🖥️ DISPLAY & SCREEN\n")
                    logs.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    val displayMetrics = context.resources.displayMetrics
                    logs.append("Resolution: ${displayMetrics.widthPixels}×${displayMetrics.heightPixels}px\n")
                    logs.append("Density: ${displayMetrics.densityDpi}dpi (${displayMetrics.density}x)\n\n")

                    logs.append("💾 MEMORY\n")
                    logs.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val memInfo = android.app.ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memInfo)
                    logs.append("Total RAM: ${memInfo.totalMem / (1024 * 1024)}MB\n")
                    logs.append("Available: ${memInfo.availMem / (1024 * 1024)}MB\n\n")

                    logs.append("⚙️ CPU ARCHITECTURE\n")
                    logs.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    logs.append("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}\n\n")

                    logs.append("📋 SYSTEM LOGS (logcat)\n")
                    logs.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    logs.append("⚠️  Note: Dates below use system format MM-DD HH:mm:ss\n")
                    logs.append("    (MM-DD = Month-Day, not Day-Month)\n\n")

                    // Collect logs
                    var line: String?
                    while (bufferedReader.readLine().also { line = it } != null) {
                        logs.append(line).append("\n")
                    }

                    bufferedReader.close()
                    process.waitFor()

                    logs.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to collect logs", e)
                    "Error collecting logs: ${e.message}"
                }
            }

        /**
         * Exports logs to a file and returns the file URI.
         */
        suspend fun exportLogsToFile(): Uri =
            withContext(Dispatchers.IO) {
                val logs = collectLogs()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "${LOG_FILE_PREFIX}_$timestamp.txt"

                // Save to cache directory (will be cleared on uninstall)
                val logFile = File(context.cacheDir, fileName)
                logFile.writeText(logs)

                Log.d(TAG, "Logs exported to ${logFile.absolutePath} (${logFile.length()} bytes)")

                // Return FileProvider URI for sharing
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    logFile,
                )
            }

        /**
         * Shares logs via Android Share API.
         * Opens share sheet for user to choose how to send logs.
         */
        suspend fun shareLogs() =
            withContext(Dispatchers.Main) {
                try {
                    val uri = exportLogsToFile()

                    val intent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Jabook Debug Logs")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Please find attached debug logs from Jabook app.",
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                    val chooser = Intent.createChooser(intent, "Share logs via")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)

                    Log.i(TAG, "Share dialog opened for logs")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to share logs", e)
                    throw e
                }
            }

        /**
         * Clears old log files from cache.
         */
        suspend fun clearOldLogFiles() =
            withContext(Dispatchers.IO) {
                try {
                    val cacheDir = context.cacheDir
                    val logFiles =
                        cacheDir.listFiles { file ->
                            file.name.startsWith(LOG_FILE_PREFIX)
                        } ?: emptyArray()

                    val now = System.currentTimeMillis()
                    val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days

                    var deletedCount = 0
                    logFiles.forEach { file ->
                        if (now - file.lastModified() > maxAge) {
                            if (file.delete()) {
                                deletedCount++
                            }
                        }
                    }

                    Log.d(TAG, "Cleared $deletedCount old log files")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear old log files", e)
                }
            }

        /**
         * Gets app version name.
         */
        private fun getAppVersion(): String =
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
    }
