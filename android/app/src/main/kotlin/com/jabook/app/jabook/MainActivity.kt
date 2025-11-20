package com.jabook.app.jabook

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var directoryPickerResult: MethodChannel.Result? = null
    private val REQUEST_CODE_OPEN_DIRECTORY = 1001

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        // Register CookieChannel for cookie management
        val cookieChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "cookie_channel")
        val cookieManager = CookieManager.getInstance()
        
        cookieChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getCookiesForUrl" -> {
                    val url = call.argument<String>("url")
                    if (url == null) {
                        result.error("INVALID_ARGUMENT", "URL is required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        val cookies = cookieManager.getCookie(url)
                        result.success(cookies ?: "")
                    } catch (e: Exception) {
                        result.error("GET_COOKIES_ERROR", e.message, null)
                    }
                }
                "setCookie" -> {
                    val url = call.argument<String>("url")
                    val cookieString = call.argument<String>("cookie")
                    if (url == null || cookieString == null) {
                        result.error("INVALID_ARGUMENT", "URL and cookie are required", null)
                        return@setMethodCallHandler
                    }
                    try {
                        cookieManager.setCookie(url, cookieString)
                        cookieManager.flush()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("SET_COOKIE_ERROR", e.message, null)
                    }
                }
                "clearAllCookies" -> {
                    try {
                        cookieManager.removeAllCookies(null)
                        cookieManager.flush()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("CLEAR_COOKIES_ERROR", e.message, null)
                    }
                }
                "flushCookies" -> {
                    try {
                        cookieManager.flush()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("FLUSH_COOKIES_ERROR", e.message, null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
        
        // Register DirectoryPickerChannel for folder selection with proper SAF support
        val directoryChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "directory_picker_channel")
        
        directoryChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "pickDirectory" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        directoryPickerResult = result
                        openDirectoryPicker()
                    } else {
                        result.error("UNSUPPORTED", "Directory picker requires Android 5.0+", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Allow user to select any directory
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }
    
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY) {
            if (resultCode == RESULT_OK && data != null) {
                val treeUri: Uri? = data.data
                if (treeUri != null) {
                    try {
                        // Take persistable URI permission for long-term access
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(treeUri, flags)
                        
                        // Convert URI to path string for Flutter
                        val uriString = treeUri.toString()
                        
                        // Return URI string (Flutter can work with it)
                        // The URI is already persisted via takePersistableUriPermission above
                        directoryPickerResult?.success(uriString)
                    } catch (e: Exception) {
                        directoryPickerResult?.error(
                            "PERMISSION_ERROR",
                            "Failed to take persistable URI permission: ${e.message}",
                            null
                        )
                    }
                } else {
                    directoryPickerResult?.error(
                        "NO_URI",
                        "No URI returned from directory picker",
                        null
                    )
                }
            } else {
                // User cancelled
                directoryPickerResult?.success(null)
            }
            directoryPickerResult = null
        }
    }
}
