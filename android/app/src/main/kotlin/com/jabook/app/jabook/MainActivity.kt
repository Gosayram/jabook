package com.jabook.app.jabook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import com.jabook.app.jabook.audio.AudioPlayerMethodHandler
import com.jabook.app.jabook.download.DownloadServiceMethodHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var directoryPickerResult: MethodChannel.Result? = null

    companion object {
        private const val REQUEST_CODE_OPEN_DIRECTORY = 1001
    }

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

        // Register PermissionChannel for managing storage permissions
        val permissionChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "permission_channel")

        permissionChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "openManageExternalStorageSettings" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        var success = false
                        var lastError: Exception? = null

                        // Try 1: ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION with package name (standard Android 11+)
                        try {
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:$packageName")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                                android.util.Log.d(
                                    "MainActivity",
                                    "Opened MANAGE_APP_ALL_FILES_ACCESS_PERMISSION settings for package: $packageName",
                                )
                                success = true
                            } else {
                                android.util.Log.w("MainActivity", "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION intent not resolvable")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "MainActivity",
                                "Failed to open ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION: ${e.message}",
                                e,
                            )
                            lastError = e
                        }

                        // Try 2: ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION (general settings)
                        if (!success) {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                if (intent.resolveActivity(packageManager) != null) {
                                    startActivity(intent)
                                    android.util.Log.d("MainActivity", "Opened ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION settings")
                                    success = true
                                } else {
                                    android.util.Log.w("MainActivity", "ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION intent not resolvable")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(
                                    "MainActivity",
                                    "Failed to open ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION: ${e.message}",
                                    e,
                                )
                                lastError = e
                            }
                        }

                        // Try 3: Open app-specific settings page (fallback for OPPO/ColorOS and other custom ROMs)
                        if (!success) {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                if (intent.resolveActivity(packageManager) != null) {
                                    startActivity(intent)
                                    android.util.Log.d("MainActivity", "Opened application details settings as fallback")
                                    success = true
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to open application details settings: ${e.message}", e)
                                lastError = e
                            }
                        }

                        if (success) {
                            result.success(true)
                        } else {
                            val errorMessage = lastError?.message ?: "All intent resolution attempts failed"
                            android.util.Log.e("MainActivity", "Failed to open any settings: $errorMessage")
                            result.error("OPEN_SETTINGS_ERROR", errorMessage, null)
                        }
                    } else {
                        result.error("UNSUPPORTED", "MANAGE_EXTERNAL_STORAGE requires Android 11+", null)
                    }
                }
                "hasManageExternalStoragePermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Try standard method first
                        var hasPermission = Environment.isExternalStorageManager()
                        android.util.Log.d("MainActivity", "Environment.isExternalStorageManager() = $hasPermission")

                        // If standard method returns false, try AppOpsManager as fallback
                        // This is needed for some custom ROMs (OPPO/ColorOS, etc.) where
                        // Environment.isExternalStorageManager() may not work correctly
                        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                                // Use string constant for MANAGE_EXTERNAL_STORAGE (available from API 30+)
                                // The constant OPSTR_MANAGE_EXTERNAL_STORAGE doesn't exist, so we use the string directly
                                val opString =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        "android:manage_external_storage"
                                    } else {
                                        null
                                    }
                                if (opString != null) {
                                    val mode =
                                        appOpsManager.checkOpNoThrow(
                                            opString,
                                            android.os.Process.myUid(),
                                            packageName,
                                        )
                                    // MODE_ALLOWED = 0, MODE_IGNORED = 1, MODE_ERRORED = 2, MODE_DEFAULT = 3
                                    hasPermission = (mode == android.app.AppOpsManager.MODE_ALLOWED)
                                    android.util.Log.d("MainActivity", "AppOpsManager check: mode=$mode, hasPermission=$hasPermission")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "Failed to check via AppOpsManager: ${e.message}")
                                // Keep the result from Environment.isExternalStorageManager()
                            }
                        }

                        result.success(hasPermission)
                    } else {
                        // For Android 10 and below, always return true (permission not needed)
                        result.success(true)
                    }
                }
                "canRequestManageExternalStorage" -> {
                    // Check if the "All files access" option is available in settings
                    // This is useful to determine if we should suggest SAF fallback
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val canRequest =
                            try {
                                // Check if ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION intent can be resolved
                                // If it can't be resolved, the option is not available (e.g., Restricted settings on Android 13+)
                                val intent =
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                val resolved = intent.resolveActivity(packageManager) != null
                                android.util.Log.d("MainActivity", "Can request MANAGE_EXTERNAL_STORAGE: $resolved")
                                resolved
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "Failed to check if can request MANAGE_EXTERNAL_STORAGE: ${e.message}")
                                // If check fails, assume it's not available (safer to suggest SAF)
                                false
                            }
                        result.success(canRequest)
                    } else {
                        // For Android 10 and below, always return true (permission not needed)
                        result.success(true)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        // Register DeviceInfoChannel for device information queries
        val deviceInfoChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "device_info_channel",
            )

        deviceInfoChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getAppStandbyBucket" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                            val bucket = usageStatsManager.appStandbyBucket
                            result.success(bucket)
                        } catch (e: Exception) {
                            result.error("GET_STANDBY_BUCKET_ERROR", "Failed to get App Standby Bucket: ${e.message}", null)
                        }
                    } else {
                        result.error("UNSUPPORTED", "App Standby Bucket requires Android 9.0+ (API 28+)", null)
                    }
                }
                "getRomVersion" -> {
                    try {
                        val romVersion = getRomVersion()
                        result.success(romVersion)
                    } catch (e: Exception) {
                        result.error("GET_ROM_VERSION_ERROR", "Failed to get ROM version: ${e.message}", null)
                    }
                }
                "getFirmwareVersion" -> {
                    try {
                        val firmwareVersion = getFirmwareVersion()
                        result.success(firmwareVersion)
                    } catch (e: Exception) {
                        result.error("GET_FIRMWARE_VERSION_ERROR", "Failed to get firmware version: ${e.message}", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        // Register ManufacturerSettingsChannel for opening manufacturer-specific settings
        val manufacturerSettingsChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "manufacturer_settings_channel",
            )

        manufacturerSettingsChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "openAutostartSettings" -> {
                    try {
                        val success = ManufacturerSettingsHelper.openAutostartSettings(this, packageName)
                        result.success(success)
                    } catch (e: Exception) {
                        result.error("OPEN_AUTOSTART_ERROR", "Failed to open autostart settings: ${e.message}", null)
                    }
                }
                "openBatteryOptimizationSettings" -> {
                    try {
                        val success = ManufacturerSettingsHelper.openBatteryOptimizationSettings(this, packageName)
                        result.success(success)
                    } catch (e: Exception) {
                        result.error("OPEN_BATTERY_ERROR", "Failed to open battery optimization settings: ${e.message}", null)
                    }
                }
                "openBackgroundRestrictionsSettings" -> {
                    try {
                        val success = ManufacturerSettingsHelper.openBackgroundRestrictionsSettings(this, packageName)
                        result.success(success)
                    } catch (e: Exception) {
                        result.error("OPEN_BACKGROUND_ERROR", "Failed to open background restrictions settings: ${e.message}", null)
                    }
                }
                "checkAutostartEnabled" -> {
                    try {
                        val enabled = ManufacturerSettingsHelper.isAutostartEnabled(this, packageName)
                        result.success(enabled)
                    } catch (e: Exception) {
                        result.error("CHECK_AUTOSTART_ERROR", "Failed to check autostart status: ${e.message}", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        // Register AudioPlayerChannel for native audio playback
        val audioPlayerChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "com.jabook.app.jabook/audio_player",
            )
        audioPlayerChannel.setMethodCallHandler(
            AudioPlayerMethodHandler(this),
        )

        // Register notification intent handler channel
        val notificationChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "com.jabook.app.jabook/notification",
            )
        notificationChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "handleNotificationClick" -> {
                    // This will be called from Flutter when notification is clicked
                    // The actual navigation is handled in Flutter via GoRouter
                    result.success(true)
                }
                else -> result.notImplemented()
            }
        }

        // Register DownloadServiceChannel for download foreground service
        val downloadServiceChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "com.jabook.app.jabook/download_service",
            )
        downloadServiceChannel.setMethodCallHandler(
            DownloadServiceMethodHandler(this),
        )

        // Register ContentUriChannel for accessing files via ContentResolver
        val contentUriChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "content_uri_channel",
            )
        contentUriChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "listDirectory" -> {
                    val uriString = call.argument<String>("uri")
                    if (uriString != null) {
                        try {
                            val uri = Uri.parse(uriString)
                            val files = listDirectoryViaContentResolver(uri)
                            result.success(files)
                        } catch (e: Exception) {
                            result.error("LIST_ERROR", e.message, null)
                        }
                    } else {
                        result.error("INVALID_ARGUMENT", "URI is required", null)
                    }
                }
                "checkUriAccess" -> {
                    val uriString = call.argument<String>("uri")
                    if (uriString != null) {
                        try {
                            val uri = Uri.parse(uriString)
                            val hasAccess = checkUriAccess(uri)
                            result.success(hasAccess)
                        } catch (e: Exception) {
                            result.error("CHECK_ERROR", e.message, null)
                        }
                    } else {
                        result.error("INVALID_ARGUMENT", "URI is required", null)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    /**
     * Lists files in a directory using ContentResolver.
     * This is required for content:// URIs on Android 7+ (API 24+).
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun listDirectoryViaContentResolver(uri: Uri): List<Map<String, String>> {
        val files = mutableListOf<Map<String, String>>()

        try {
            val contentResolver = this.contentResolver

            // Use DocumentsContract for tree URIs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val childrenUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri),
                    )

                val cursor =
                    contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                        ),
                        null,
                        null,
                        null,
                    )

                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                    while (it.moveToNext()) {
                        val documentId = it.getString(idColumn)
                        val name = it.getString(nameColumn)
                        val mimeType = it.getString(mimeColumn)
                        val size = it.getLong(sizeColumn)

                        val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

                        files.add(
                            mapOf(
                                "uri" to childUri.toString(),
                                "name" to (name ?: ""),
                                "mimeType" to (mimeType ?: ""),
                                "size" to size.toString(),
                                "isDirectory" to (mimeType == DocumentsContract.Document.MIME_TYPE_DIR).toString(),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error listing directory via ContentResolver", e)
            throw e
        }

        return files
    }

    /**
     * Checks if the app has access to a content URI.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun checkUriAccess(uri: Uri): Boolean =
        try {
            val contentResolver = this.contentResolver
            val persistedUriPermissions = contentResolver.persistedUriPermissions

            android.util.Log.d("MainActivity", "Checking URI access for: $uri")
            android.util.Log.d("MainActivity", "Persisted permissions count: ${persistedUriPermissions.size}")

            // Check if we have persistable permission for this URI
            // Check both read and write permissions
            val hasPermission =
                persistedUriPermissions.any {
                    val uriMatches = it.uri == uri
                    val hasReadOrWrite = it.isReadPermission || it.isWritePermission
                    if (uriMatches) {
                        android.util.Log.d(
                            "MainActivity",
                            "Found matching URI permission: read=${it.isReadPermission}, write=${it.isWritePermission}",
                        )
                    }
                    uriMatches && hasReadOrWrite
                }

            if (!hasPermission) {
                android.util.Log.w("MainActivity", "No persistable permission found for URI: $uri")
                // Log all persisted permissions for debugging
                persistedUriPermissions.forEach { perm ->
                    android.util.Log.d(
                        "MainActivity",
                        "Persisted permission: ${perm.uri}, read=${perm.isReadPermission}, write=${perm.isWritePermission}",
                    )
                }
            }

            hasPermission
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking URI access", e)
            false
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun openDirectoryPicker() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Allow user to select any directory
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY) {
            if (resultCode == RESULT_OK && data != null) {
                val treeUri: Uri? = data.data
                if (treeUri != null) {
                    try {
                        android.util.Log.d("MainActivity", "Directory selected: $treeUri")

                        // Take persistable URI permission for long-term access
                        val flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                        try {
                            contentResolver.takePersistableUriPermission(treeUri, flags)
                            android.util.Log.d("MainActivity", "Persistable URI permission taken successfully")
                        } catch (e: SecurityException) {
                            android.util.Log.e("MainActivity", "SecurityException taking persistable URI permission: ${e.message}")
                            // This can happen if user didn't grant permission properly
                            // Still try to return URI, but log the issue
                            directoryPickerResult?.error(
                                "PERMISSION_DENIED",
                                "Failed to take persistable URI permission. User may need to grant permission in the file picker dialog.",
                                null,
                            )
                            return
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Exception taking persistable URI permission: ${e.message}", e)
                            directoryPickerResult?.error(
                                "PERMISSION_ERROR",
                                "Failed to take persistable URI permission: ${e.message}",
                                null,
                            )
                            return
                        }

                        // Verify permission was actually granted
                        val persistedPermissions = contentResolver.persistedUriPermissions
                        val hasPermission =
                            persistedPermissions.any {
                                it.uri == treeUri && (it.isReadPermission || it.isWritePermission)
                            }

                        if (!hasPermission) {
                            android.util.Log.w(
                                "MainActivity",
                                "Warning: URI permission not found in persisted permissions after takePersistableUriPermission",
                            )
                            // Still return URI, but log warning
                            // Some devices may have delayed permission persistence
                        } else {
                            android.util.Log.d("MainActivity", "URI permission verified in persisted permissions")
                        }

                        // Convert URI to path string for Flutter
                        val uriString = treeUri.toString()
                        android.util.Log.d("MainActivity", "Returning URI to Flutter: $uriString")

                        // Return URI string (Flutter can work with it)
                        // The URI is already persisted via takePersistableUriPermission above
                        directoryPickerResult?.success(uriString)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Unexpected error processing directory selection", e)
                        directoryPickerResult?.error(
                            "UNEXPECTED_ERROR",
                            "Unexpected error: ${e.message}",
                            null,
                        )
                    }
                } else {
                    directoryPickerResult?.error(
                        "NO_URI",
                        "No URI returned from directory picker",
                        null,
                    )
                }
            } else {
                // User cancelled
                directoryPickerResult?.success(null)
            }
            directoryPickerResult = null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle notification click - open player if requested
        if (intent.getBooleanExtra("open_player", false)) {
            // Send message to Flutter to open player
            // Flutter will handle navigation via GoRouter
            val messenger = flutterEngine?.dartExecutor?.binaryMessenger
            if (messenger != null) {
                val channel = MethodChannel(messenger, "com.jabook.app.jabook/notification")
                channel.invokeMethod("openPlayer", null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if we should open player from notification
        val intent = intent
        if (intent != null && intent.getBooleanExtra("open_player", false)) {
            // Clear the flag to avoid reopening on every resume
            intent.removeExtra("open_player")
            // Send message to Flutter to open player
            val messenger = flutterEngine?.dartExecutor?.binaryMessenger
            if (messenger != null) {
                val channel = MethodChannel(messenger, "com.jabook.app.jabook/notification")
                channel.invokeMethod("openPlayer", null)
            }
        }
    }

    /**
     * Gets the ROM version by reading system properties.
     *
     * Different manufacturers store ROM version in different system properties:
     * - MIUI: ro.miui.ui.version.name, ro.miui.ui.version.code
     * - EMUI: ro.build.version.emui
     * - ColorOS: ro.build.version.opporom
     * - One UI: ro.build.version.oneui
     * - FuntouchOS: ro.vivo.os.version
     * - Flyme: ro.build.display.id
     *
     * @return ROM version string (e.g., "MIUI 14.0", "EMUI 12.0") or null if unavailable
     */
    private fun getRomVersion(): String? =
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()

            // Try to get ROM version from system properties
            when {
                manufacturer.contains("xiaomi") ||
                    brand.contains("xiaomi") ||
                    brand.contains("redmi") ||
                    brand.contains("poco") -> {
                    // MIUI version
                    val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
                    val miuiCode = getSystemProperty("ro.miui.ui.version.code")
                    when {
                        miuiVersion != null -> "MIUI $miuiVersion"
                        miuiCode != null -> "MIUI $miuiCode"
                        else -> null
                    }
                }
                manufacturer.contains("huawei") ||
                    brand.contains("huawei") ||
                    brand.contains("honor") -> {
                    // EMUI/HarmonyOS version
                    val emuiVersion = getSystemProperty("ro.build.version.emui")
                    emuiVersion?.let { "EMUI $it" } ?: "EMUI"
                }
                manufacturer.contains("oppo") ||
                    brand.contains("oppo") ||
                    manufacturer.contains("realme") ||
                    brand.contains("realme") ||
                    manufacturer.contains("oneplus") ||
                    brand.contains("oneplus") -> {
                    // ColorOS/RealmeUI/OxygenOS version
                    val colorOsVersion = getSystemProperty("ro.build.version.opporom")
                    colorOsVersion?.let { "ColorOS $it" } ?: null
                }
                manufacturer.contains("samsung") || brand.contains("samsung") -> {
                    // One UI version - try multiple system properties
                    // ro.build.version.oneui or ro.build.version.sem should contain the One UI version
                    val oneUiVersion =
                        getSystemProperty("ro.build.version.oneui")
                            ?: getSystemProperty("ro.build.version.sem")

                    if (oneUiVersion != null) {
                        // Check if version looks valid (contains dot or is a reasonable number)
                        val isValidVersion =
                            oneUiVersion.contains(".") ||
                                (oneUiVersion.length <= 3 && oneUiVersion.toIntOrNull() != null && oneUiVersion.toInt() < 100)

                        if (isValidVersion) {
                            // Extract major version number
                            val majorVersion = oneUiVersion.split(".").firstOrNull()?.toIntOrNull()
                            if (majorVersion != null && majorVersion > 0 && majorVersion < 20) {
                                "One UI $majorVersion"
                            } else {
                                // If major version extraction failed, use Android API mapping
                                getOneUIVersionFromAndroidApi()
                            }
                        } else {
                            // Invalid version (like "3601", "80000"), use Android API mapping
                            getOneUIVersionFromAndroidApi()
                        }
                    } else {
                        // No system property available, use Android API mapping
                        getOneUIVersionFromAndroidApi()
                    }
                }
                manufacturer.contains("vivo") || brand.contains("vivo") -> {
                    // FuntouchOS/OriginOS version
                    val funtouchVersion = getSystemProperty("ro.vivo.os.version")
                    funtouchVersion?.let { "FuntouchOS $it" } ?: null
                }
                manufacturer.contains("meizu") || brand.contains("meizu") -> {
                    // Flyme version
                    val flymeVersion = getSystemProperty("ro.build.display.id")
                    flymeVersion?.let { "Flyme $it" } ?: null
                }
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to get ROM version: ${e.message}")
            null
        }

    /**
     * Gets One UI version based on Android API level.
     *
     * Mapping:
     * - API 36 (Android 16) = One UI 8
     * - API 35 (Android 15) = One UI 8
     * - API 34 (Android 14) = One UI 7
     * - API 33 (Android 13) = One UI 6
     * - API 32 (Android 12L) = One UI 5
     * - API 31 (Android 12) = One UI 4
     * - API 30 (Android 11) = One UI 3
     * - API 29 (Android 10) = One UI 2
     * - API 28 (Android 9) = One UI 1
     */
    private fun getOneUIVersionFromAndroidApi(): String {
        val androidApi = Build.VERSION.SDK_INT
        val oneUiVersion =
            when (androidApi) {
                36 -> 8 // Android 16
                35 -> 8 // Android 15
                34 -> 7 // Android 14
                33 -> 6 // Android 13
                32, 31 -> 5 // Android 12/12L
                30 -> 4 // Android 11
                29 -> 3 // Android 10
                28 -> 2 // Android 9
                27 -> 1 // Android 8.1
                else -> null
            }
        return oneUiVersion?.let { "One UI $it" } ?: "One UI"
    }

    /**
     * Gets the firmware version (build number) of the device.
     *
     * For Samsung devices, this typically includes the build number like "S918BXXU3AWGJ".
     * Uses ro.build.display.id or ro.build.version.incremental system property.
     *
     * @return Firmware version string or null if unavailable
     */
    private fun getFirmwareVersion(): String? {
        return try {
            // Try ro.build.display.id first (contains full firmware version like "BP2A.250605.031.A3.S918BXXU3AWGJ")
            val displayId = getSystemProperty("ro.build.display.id")
            if (!displayId.isNullOrEmpty() && displayId != "unknown") {
                // Extract the build number part (last segment after last dot)
                val parts = displayId.split(".")
                if (parts.isNotEmpty()) {
                    val buildNumber = parts.lastOrNull()
                    if (!buildNumber.isNullOrEmpty() && buildNumber.length >= 10) {
                        // Return the build number (e.g., "S918BXXU3AWGJ")
                        return buildNumber
                    }
                }
                // If extraction failed, return full display.id
                return displayId
            }

            // Fallback to ro.build.version.incremental (build number like "S918BXXU3AWGJ")
            val incremental = getSystemProperty("ro.build.version.incremental")
            if (!incremental.isNullOrEmpty() && incremental != "unknown") {
                return incremental
            }

            // Last resort: use Build.ID
            val buildId = Build.ID
            if (buildId.isNotEmpty() && buildId != "unknown") {
                return buildId
            }

            null
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to get firmware version: ${e.message}")
            null
        }
    }

    /**
     * Gets a system property value.
     *
     * @param key The system property key
     * @return The property value or null if unavailable
     */
    private fun getSystemProperty(key: String): String? =
        try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val reader =
                java.io.BufferedReader(
                    java.io.InputStreamReader(process.inputStream),
                )
            val value = reader.readLine()
            reader.close()
            process.waitFor()
            if (value.isNullOrEmpty()) null else value.trim()
        } catch (e: Exception) {
            null
        }
}
