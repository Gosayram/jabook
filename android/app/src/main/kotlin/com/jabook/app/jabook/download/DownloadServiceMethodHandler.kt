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

package com.jabook.app.jabook.download

import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel handler for download foreground service operations.
 *
 * This class handles method calls from Flutter to start/stop the download
 * foreground service and update download progress.
 */
class DownloadServiceMethodHandler(
    private val context: Context,
) : MethodChannel.MethodCallHandler {
    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        try {
            when (call.method) {
                "startService" -> {
                    val intent =
                        Intent(context, DownloadForegroundService::class.java).apply {
                            action = DownloadForegroundService.ACTION_START
                        }
                    context.startForegroundService(intent)
                    result.success(true)
                }
                "stopService" -> {
                    val intent =
                        Intent(context, DownloadForegroundService::class.java).apply {
                            action = DownloadForegroundService.ACTION_STOP
                        }
                    context.startService(intent)
                    result.success(true)
                }
                "updateProgress" -> {
                    val title = call.argument<String>("title") ?: "Downloading..."
                    val progress = call.argument<Double>("progress") ?: 0.0
                    val speed = call.argument<String>("speed") ?: ""

                    val intent =
                        Intent(context, DownloadForegroundService::class.java).apply {
                            action = DownloadForegroundService.ACTION_UPDATE_PROGRESS
                            putExtra("title", title)
                            putExtra("progress", progress)
                            putExtra("speed", speed)
                        }
                    context.startService(intent)
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadServiceMethodHandler", "Error handling method call: ${call.method}", e)
            result.error("EXCEPTION", e.message ?: "Unknown error", null)
        }
    }
}
