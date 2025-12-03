package com.jabook.app.jabook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.KeyEvent
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import com.jabook.app.jabook.audio.AudioPlayerMethodHandler
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.download.DownloadServiceMethodHandler
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var directoryPickerResult: MethodChannel.Result? = null
    private var positionSaveReceiver: BroadcastReceiver? = null
    private var exitAppReceiver: BroadcastReceiver? = null

    // Flag to prevent app exit during player initialization
    @Volatile
    private var isPlayerInitializing = false

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

        // Register BatteryChannel for battery level monitoring
        val batteryChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "com.jabook.app.jabook/battery",
            )

        batteryChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getBatteryLevel" -> {
                    try {
                        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        result.success(batteryLevel)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to get battery level: ${e.message}")
                        result.error("BATTERY_ERROR", "Failed to get battery level: ${e.message}", null)
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

        // Register PlayerLifecycleChannel for tracking player initialization state
        val playerLifecycleChannel =
            MethodChannel(
                flutterEngine.dartExecutor.binaryMessenger,
                "com.jabook.app.jabook/player_lifecycle",
            )
        playerLifecycleChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "setPlayerInitializing" -> {
                    val isInitializing = call.argument<Boolean>("isInitializing") ?: false
                    val oldValue = isPlayerInitializing
                    isPlayerInitializing = isInitializing
                    android.util.Log.i(
                        "MainActivity",
                        "Player initialization state changed: $oldValue -> $isInitializing",
                    )
                    result.success(true)
                }
                "isPlayerInitializing" -> {
                    android.util.Log.d(
                        "MainActivity",
                        "isPlayerInitializing query: $isPlayerInitializing",
                    )
                    result.success(isPlayerInitializing)
                }
                else -> result.notImplemented()
            }
        }

        // Register BroadcastReceiver for saving position before unload
        registerPositionSaveReceiver(flutterEngine, audioPlayerChannel)

        // Register BroadcastReceiver for app exit (sleep timer)
        registerExitAppReceiver()

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
                val childrenUri: Uri =
                    run {
                        // Check if this is a tree URI
                        if (DocumentsContract.isTreeUri(uri)) {
                            // For tree URIs, use buildChildDocumentsUriUsingTree
                            val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
                            android.util.Log.d("MainActivity", "Listing tree URI, tree document ID: $treeDocumentId")
                            DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeDocumentId)
                        } else {
                            // For document URIs, try to get the document ID and build children URI
                            // First, check if we can get document ID
                            try {
                                val documentId = DocumentsContract.getDocumentId(uri)
                                android.util.Log.d("MainActivity", "Listing document URI, document ID: $documentId")
                                // Try to find the tree URI from persisted permissions
                                val persistedPermissions = contentResolver.persistedUriPermissions
                                val treeUri =
                                    persistedPermissions
                                        .firstOrNull { perm ->
                                            DocumentsContract.isTreeUri(perm.uri) &&
                                                (
                                                    documentId.startsWith("${DocumentsContract.getTreeDocumentId(perm.uri)}/") ||
                                                        documentId == DocumentsContract.getTreeDocumentId(perm.uri)
                                                )
                                        }?.uri

                                if (treeUri != null) {
                                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                                } else {
                                    // Fallback: try to use the URI directly as a document URI
                                    android.util.Log.w("MainActivity", "No tree URI found for document, trying direct query")
                                    DocumentsContract.buildChildDocumentsUri(uri.authority ?: "", documentId)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Cannot get document ID, trying direct query: ${e.message}")
                                // Fallback: try to use the URI directly - but we need document ID
                                try {
                                    val documentId = DocumentsContract.getDocumentId(uri)
                                    DocumentsContract.buildChildDocumentsUri(uri.authority ?: "", documentId)
                                } catch (e2: Exception) {
                                    android.util.Log.e("MainActivity", "Cannot build children URI: ${e2.message}")
                                    throw Exception("Cannot list directory: ${e.message}", e)
                                }
                            }
                        }
                    }

                android.util.Log.d("MainActivity", "Querying children URI: $childrenUri")
                android.util.Log.d("MainActivity", "Original URI: $uri")

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

                if (cursor == null) {
                    android.util.Log.w("MainActivity", "Query returned null for URI: $childrenUri")
                    android.util.Log.w("MainActivity", "This may indicate permission issues or invalid URI")
                    return files
                }

                var entryCount = 0
                cursor.use {
                    val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                    android.util.Log.d("MainActivity", "Cursor has ${it.count} entries")

                    while (it.moveToNext()) {
                        entryCount++
                        val documentId = it.getString(idColumn)
                        val name = it.getString(nameColumn)
                        val mimeType = it.getString(mimeColumn)
                        val size = it.getLong(sizeColumn)

                        android.util.Log.d(
                            "MainActivity",
                            "Entry $entryCount: name=$name, mimeType=$mimeType, isDir=${mimeType == DocumentsContract.Document.MIME_TYPE_DIR}",
                        )

                        // Build child URI - use tree URI if available, otherwise use document URI
                        val childUri =
                            if (DocumentsContract.isTreeUri(uri)) {
                                DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
                            } else {
                                // Try to find tree URI from persisted permissions
                                val persistedPermissions = contentResolver.persistedUriPermissions
                                val treeUri =
                                    persistedPermissions
                                        .firstOrNull { perm ->
                                            DocumentsContract.isTreeUri(perm.uri)
                                        }?.uri

                                if (treeUri != null) {
                                    DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                                } else {
                                    DocumentsContract.buildDocumentUri(uri.authority ?: "", documentId)
                                }
                            }

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

                    android.util.Log.d("MainActivity", "Listed $entryCount entries, returning ${files.size} files")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error listing directory via ContentResolver", e)
            android.util.Log.e("MainActivity", "URI that caused error: $uri", e)
            android.util.Log.e("MainActivity", "Error type: ${e.javaClass.simpleName}", e)
            throw e
        }

        android.util.Log.d("MainActivity", "Returning ${files.size} files from listDirectoryViaContentResolver")
        return files
    }

    /**
     * Checks if the app has access to a content URI.
     *
     * This method checks access in multiple ways:
     * 1. Exact URI match in persisted permissions
     * 2. Tree URI match (if URI is part of a tree)
     * 3. Real access verification by attempting to query the URI
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun checkUriAccess(uri: Uri): Boolean {
        try {
            val contentResolver = this.contentResolver
            val persistedUriPermissions = contentResolver.persistedUriPermissions

            android.util.Log.d("MainActivity", "Checking URI access for: $uri")
            android.util.Log.d("MainActivity", "Persisted permissions count: ${persistedUriPermissions.size}")

            // Normalize URI for comparison (handles encoding differences)
            val normalizedUri = uri.normalizeScheme()

            // Step 1: Check if we have persistable permission for this exact URI
            var hasPermission =
                persistedUriPermissions.any {
                    val persistedUri = it.uri.normalizeScheme()
                    val uriMatches = persistedUri == normalizedUri
                    val hasReadOrWrite = it.isReadPermission || it.isWritePermission

                    if (uriMatches) {
                        android.util.Log.d(
                            "MainActivity",
                            "Found matching URI permission: read=${it.isReadPermission}, write=${it.isWritePermission}",
                        )
                    }
                    uriMatches && hasReadOrWrite
                }

            // Step 2: If not found by exact match, check if URI is part of a tree
            // Tree URIs grant access to all child documents
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // Check if the requested URI is a tree URI or part of one
                    val isTreeUri = DocumentsContract.isTreeUri(uri)
                    if (isTreeUri) {
                        val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
                        android.util.Log.d("MainActivity", "Checking tree URI access, tree document ID: $treeDocumentId")

                        hasPermission =
                            persistedUriPermissions.any { perm ->
                                if (DocumentsContract.isTreeUri(perm.uri)) {
                                    val persistedTreeId = DocumentsContract.getTreeDocumentId(perm.uri)
                                    // Check if requested tree is the same as persisted tree
                                    // or if requested tree is a child of persisted tree
                                    val isPartOfTree =
                                        treeDocumentId == persistedTreeId ||
                                            treeDocumentId.startsWith("$persistedTreeId/")
                                    val hasReadOrWrite = perm.isReadPermission || perm.isWritePermission

                                    if (isPartOfTree && hasReadOrWrite) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Found tree URI permission: persistedTree=$persistedTreeId, requestedTree=$treeDocumentId",
                                        )
                                    }
                                    isPartOfTree && hasReadOrWrite
                                } else {
                                    false
                                }
                            }
                    } else {
                        // Not a tree URI, might be a document URI - check if it's under any persisted tree
                        try {
                            val documentId = DocumentsContract.getDocumentId(uri)
                            android.util.Log.d("MainActivity", "Checking document URI access, document ID: $documentId")

                            hasPermission =
                                persistedUriPermissions.any { perm ->
                                    if (DocumentsContract.isTreeUri(perm.uri)) {
                                        val persistedTreeId = DocumentsContract.getTreeDocumentId(perm.uri)
                                        // Check if document is under the persisted tree
                                        val isUnderTree =
                                            documentId.startsWith("$persistedTreeId/") ||
                                                documentId == persistedTreeId
                                        val hasReadOrWrite = perm.isReadPermission || perm.isWritePermission

                                        if (isUnderTree && hasReadOrWrite) {
                                            android.util.Log.d(
                                                "MainActivity",
                                                "Found document under tree: tree=$persistedTreeId, document=$documentId",
                                            )
                                        }
                                        isUnderTree && hasReadOrWrite
                                    } else {
                                        false
                                    }
                                }
                        } catch (e: Exception) {
                            android.util.Log.d("MainActivity", "Cannot get document ID from URI: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("MainActivity", "Error checking tree URI access: ${e.message}")
                }
            }

            // Step 3: If still not found, verify access by attempting to query the URI
            // This is the most reliable way to check actual access
            if (!hasPermission) {
                try {
                    android.util.Log.d("MainActivity", "Attempting to verify access by querying URI")
                    val cursor =
                        contentResolver.query(
                            uri,
                            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                            null,
                            null,
                            null,
                        )
                    if (cursor != null) {
                        cursor.close()
                        android.util.Log.d("MainActivity", "URI access verified by query: $uri")
                        hasPermission = true
                    } else {
                        android.util.Log.d("MainActivity", "Query returned null, no access")
                    }
                } catch (e: SecurityException) {
                    android.util.Log.d("MainActivity", "SecurityException when querying URI: ${e.message}")
                } catch (e: Exception) {
                    android.util.Log.d("MainActivity", "Exception when querying URI: ${e.message}")
                }
            }

            if (!hasPermission) {
                android.util.Log.w("MainActivity", "No access found for URI: $uri")
                // Log all persisted permissions for debugging
                persistedUriPermissions.forEach { perm ->
                    android.util.Log.d(
                        "MainActivity",
                        "Persisted permission: ${perm.uri}, read=${perm.isReadPermission}, write=${perm.isWritePermission}",
                    )
                }
            } else {
                android.util.Log.d("MainActivity", "Access granted for URI: $uri")
            }

            return hasPermission
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking URI access", e)
            return false
        }
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
                            android.util.Log.e("MainActivity", "SecurityException taking persistable URI permission: ${e.message}", e)
                            // This happens when user didn't check the "Allow access to this folder" checkbox
                            // in the file picker dialog. The checkbox must be checked for persistable permission.
                            val errorMessage =
                                buildString {
                                    append(
                                        "Please check the 'Allow access to this folder' checkbox " +
                                            "in the file picker dialog and try again. ",
                                    )
                                    append("Without this checkbox, the app cannot access the selected folder. ")
                                    append("If you already checked it, try selecting the folder again.")
                                }
                            directoryPickerResult?.error(
                                "PERMISSION_DENIED",
                                errorMessage,
                                null,
                            )
                            return
                        } catch (e: IllegalArgumentException) {
                            android.util.Log.e(
                                "MainActivity",
                                "IllegalArgumentException taking persistable URI permission: ${e.message}",
                                e,
                            )
                            // This can happen if the URI is invalid or if FLAG_GRANT_PERSISTABLE_URI_PERMISSION was not set in Intent
                            val errorMessage =
                                buildString {
                                    append("Invalid folder selection. ")
                                    append("Please try selecting the folder again. ")
                                    append("If the problem persists, the folder may not be accessible.")
                                }
                            directoryPickerResult?.error(
                                "INVALID_URI",
                                errorMessage,
                                null,
                            )
                            return
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Exception taking persistable URI permission: ${e.message}", e)
                            val errorMessage =
                                buildString {
                                    append("Failed to save folder access permission: ${e.message}. ")
                                    append("Please try selecting the folder again. ")
                                    append("If the problem persists, check your device settings.")
                                }
                            directoryPickerResult?.error(
                                "PERMISSION_ERROR",
                                errorMessage,
                                null,
                            )
                            return
                        }

                        // Verify permission was actually granted
                        // Use normalized URI for comparison (same as in checkUriAccess)
                        val normalizedTreeUri = treeUri.normalizeScheme()
                        val persistedPermissions = contentResolver.persistedUriPermissions

                        android.util.Log.d("MainActivity", "Verifying permission for URI: $treeUri (normalized: $normalizedTreeUri)")
                        android.util.Log.d("MainActivity", "Persisted permissions count: ${persistedPermissions.size}")

                        var hasPermission =
                            persistedPermissions.any {
                                val persistedUri = it.uri.normalizeScheme()
                                val uriMatches = persistedUri == normalizedTreeUri
                                val hasReadOrWrite = it.isReadPermission || it.isWritePermission

                                if (uriMatches) {
                                    android.util.Log.d(
                                        "MainActivity",
                                        "Found matching persisted permission: read=${it.isReadPermission}, write=${it.isWritePermission}",
                                    )
                                }
                                uriMatches && hasReadOrWrite
                            }

                        // If not found, wait a bit and check again (some devices have delayed persistence)
                        // Try multiple times with increasing delays for devices with slow permission persistence
                        if (!hasPermission) {
                            val retryDelays = listOf(200L, 500L, 1000L) // 200ms, 500ms, 1s
                            var retryCount = 0

                            for (delay in retryDelays) {
                                android.util.Log.d(
                                    "MainActivity",
                                    "Permission not found, waiting ${delay}ms and retrying (attempt ${retryCount + 1}/${retryDelays.size})...",
                                )
                                Thread.sleep(delay)

                                val retryPermissions = contentResolver.persistedUriPermissions
                                hasPermission =
                                    retryPermissions.any {
                                        val persistedUri = it.uri.normalizeScheme()
                                        val uriMatches = persistedUri == normalizedTreeUri
                                        val hasReadOrWrite = it.isReadPermission || it.isWritePermission
                                        uriMatches && hasReadOrWrite
                                    }

                                if (hasPermission) {
                                    android.util.Log.d("MainActivity", "Permission found after retry (attempt ${retryCount + 1})")
                                    break
                                }
                                retryCount++
                            }
                        }

                        if (!hasPermission) {
                            android.util.Log.w(
                                "MainActivity",
                                "Warning: URI permission not found in persisted permissions after takePersistableUriPermission and retries",
                            )
                            // Log all persisted permissions for debugging
                            persistedPermissions.forEach { perm ->
                                android.util.Log.d(
                                    "MainActivity",
                                    "Persisted permission: ${perm.uri} (normalized: ${perm.uri.normalizeScheme()}), read=${perm.isReadPermission}, write=${perm.isWritePermission}",
                                )
                            }
                            // Log the requested URI for comparison
                            android.util.Log.d(
                                "MainActivity",
                                "Requested URI (normalized): $normalizedTreeUri",
                            )
                            // Still return URI - the permission might be granted but not yet persisted
                            // Some devices (especially custom ROMs) may have delayed persistence
                            // The checkUriAccess method will handle this with retry logic
                            android.util.Log.d("MainActivity", "Returning URI anyway - permission may be granted but not yet persisted")
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

    override fun onDestroy() {
        super.onDestroy()
        // Unregister position save receiver
        unregisterPositionSaveReceiver()
        unregisterExitAppReceiver()
    }

    /**
     * Handles key events, including media button events from headphones.
     *
     * When Activity is in foreground, it receives key events first.
     * For media buttons, we return false to let the system handle them
     * through MediaSession (which works when app is in background).
     * This ensures media buttons work consistently whether app is foreground or background.
     *
     * Note: MediaSessionService automatically handles media buttons when app is in background.
     * When app is in foreground, we need to delegate media buttons to MediaSession.
     */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // Check if this is a media button
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                // Don't handle media buttons in Activity - let system route them to MediaSession
                // This ensures MediaSessionService handles them consistently
                android.util.Log.d(
                    "MainActivity",
                    "Media button pressed (keyCode=$keyCode), delegating to MediaSession",
                )
                return false // Let system handle via MediaSession
            }
        }
        // For non-media buttons, use default handling
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Handles key up events for media buttons.
     * Returns false to let system handle via MediaSession.
     */
    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // Check if this is a media button
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            -> {
                android.util.Log.d(
                    "MainActivity",
                    "Media button released (keyCode=$keyCode), delegating to MediaSession",
                )
                return false // Let system handle via MediaSession
            }
        }
        // For non-media buttons, use default handling
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Registers BroadcastReceiver for saving position before player unload.
     *
     * This receiver listens for ACTION_SAVE_POSITION_BEFORE_UNLOAD broadcast
     * from AudioPlayerService and triggers position saving through MethodChannel.
     */
    private fun registerPositionSaveReceiver(
        flutterEngine: FlutterEngine,
        @Suppress("UNUSED_PARAMETER") audioPlayerChannel: MethodChannel,
    ) {
        positionSaveReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == AudioPlayerService.ACTION_SAVE_POSITION_BEFORE_UNLOAD) {
                        val trackIndex = intent.getIntExtra(AudioPlayerService.EXTRA_TRACK_INDEX, -1)
                        val positionMs = intent.getLongExtra(AudioPlayerService.EXTRA_POSITION_MS, -1L)

                        android.util.Log.d(
                            "MainActivity",
                            "Received position save broadcast: track=$trackIndex, position=${positionMs}ms",
                        )

                        // Call Flutter method to save position
                        // Note: This is a fire-and-forget call - we don't wait for result
                        // Position is already saved periodically, this is just an additional safety measure
                        try {
                            // Get MethodChannel from flutterEngine to ensure it's available
                            val messenger = flutterEngine.dartExecutor.binaryMessenger
                            val channel = MethodChannel(messenger, "com.jabook.app.jabook/audio_player")
                            channel.invokeMethod(
                                "saveCurrentPosition",
                                null,
                                object : MethodChannel.Result {
                                    override fun success(result: Any?) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Position save triggered successfully via MethodChannel",
                                        )
                                    }

                                    override fun error(
                                        errorCode: String,
                                        errorMessage: String?,
                                        errorDetails: Any?,
                                    ) {
                                        android.util.Log.w(
                                            "MainActivity",
                                            "Failed to trigger position save via MethodChannel: $errorCode - $errorMessage",
                                        )
                                        // Not critical - position is already saved periodically
                                    }

                                    override fun notImplemented() {
                                        android.util.Log.w(
                                            "MainActivity",
                                            "saveCurrentPosition method not implemented in Flutter",
                                        )
                                    }
                                },
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("MainActivity", "Failed to invoke saveCurrentPosition method", e)
                            // Not critical - position is already saved periodically
                        }
                    }
                }
            }

        // Register receiver for local broadcasts
        val filter = IntentFilter(AudioPlayerService.ACTION_SAVE_POSITION_BEFORE_UNLOAD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionSaveReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(positionSaveReceiver, filter)
        }

        android.util.Log.d("MainActivity", "Position save receiver registered")
    }

    /**
     * Unregisters position save receiver.
     */
    private fun unregisterPositionSaveReceiver() {
        positionSaveReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
                android.util.Log.d("MainActivity", "Position save receiver unregistered")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to unregister position save receiver", e)
            }
        }
        positionSaveReceiver = null
    }

    /**
     * Registers BroadcastReceiver for app exit (sleep timer).
     *
     * This receiver listens for EXIT_APP broadcast from AudioPlayerService
     * and finishes the activity to exit the app completely.
     */
    private fun registerExitAppReceiver() {
        exitAppReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == "com.jabook.app.jabook.EXIT_APP") {
                        // CRITICAL: Check if player is initializing - if so, ignore exit request
                        // This prevents app exit during player initialization which causes white screen
                        android.util.Log.d(
                            "MainActivity",
                            "Exit app broadcast received, checking player initialization state: isPlayerInitializing=$isPlayerInitializing",
                        )
                        if (isPlayerInitializing) {
                            android.util.Log.w(
                                "MainActivity",
                                "Exit app broadcast received but player is initializing (isPlayerInitializing=$isPlayerInitializing), ignoring to prevent white screen",
                            )
                            return
                        }

                        android.util.Log.i("MainActivity", "Exit app broadcast received, player not initializing, finishing activity")
                        // Finish all activities and remove from recent apps
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAndRemoveTask()
                            } else {
                                finishAffinity()
                            }
                            android.util.Log.i("MainActivity", "Activity finished successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to finish activity", e)
                        }
                    }
                }
            }

        // Register receiver for local broadcasts
        val filter = IntentFilter("com.jabook.app.jabook.EXIT_APP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(exitAppReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(exitAppReceiver, filter)
        }

        android.util.Log.d("MainActivity", "Exit app receiver registered")
    }

    /**
     * Unregisters exit app receiver.
     */
    private fun unregisterExitAppReceiver() {
        exitAppReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
                android.util.Log.d("MainActivity", "Exit app receiver unregistered")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to unregister exit app receiver", e)
            }
        }
        exitAppReceiver = null
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
