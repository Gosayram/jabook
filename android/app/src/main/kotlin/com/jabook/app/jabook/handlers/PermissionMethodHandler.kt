package com.jabook.app.jabook.handlers

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class PermissionMethodHandler(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "permission_channel")

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
        when (call.method) {
            "openManageExternalStorageSettings" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    var success = false
                    var lastError: Exception? = null

                    // Try 1: ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION with package name
                    try {
                        val intent =
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                            android.util.Log.d("PermissionHandler", "Opened MANAGE_APP_ALL_FILES_ACCESS_PERMISSION settings")
                            success = true
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }

                    // Try 2: ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION (general settings)
                    if (!success) {
                        try {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                                android.util.Log.d("PermissionHandler", "Opened ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION settings")
                                success = true
                            }
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }

                    // Try 3: Open app-specific settings page
                    if (!success) {
                        try {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                                android.util.Log.d("PermissionHandler", "Opened application details settings fallback")
                                success = true
                            }
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }

                    if (success) {
                        result.success(true)
                    } else {
                        val errorMessage = lastError?.message ?: "All intent resolution attempts failed"
                        result.error("OPEN_SETTINGS_ERROR", errorMessage, null)
                    }
                } else {
                    result.error("UNSUPPORTED", "MANAGE_EXTERNAL_STORAGE requires Android 11+", null)
                }
            }
            "hasManageExternalStoragePermission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    var hasPermission = Environment.isExternalStorageManager()

                    // Fallback using AppOpsManager
                    if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                            val opString = "android:manage_external_storage"
                            val mode =
                                appOpsManager.checkOpNoThrow(
                                    opString,
                                    android.os.Process.myUid(),
                                    context.packageName,
                                )
                            hasPermission = (mode == AppOpsManager.MODE_ALLOWED)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                    result.success(hasPermission)
                } else {
                    result.success(true)
                }
            }
            "canRequestManageExternalStorage" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val canRequest =
                        try {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            intent.resolveActivity(context.packageManager) != null
                        } catch (e: Exception) {
                            false
                        }
                    result.success(canRequest)
                } else {
                    result.success(true)
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }
}
