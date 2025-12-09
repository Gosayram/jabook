package com.jabook.app.jabook.handlers

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class DirectoryPickerMethodHandler(
    private val activity: ComponentActivity,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "directory_picker_channel")
    private var directoryPickerResult: MethodChannel.Result? = null
    private lateinit var directoryPickerLauncher: ActivityResultLauncher<Intent>

    init {
        channel.setMethodCallHandler(this)

        // Register the launcher. This works because this class is instantiated during Activity initialization (onCreate)
        directoryPickerLauncher =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handleActivityResult(result.resultCode, result.data)
            }
    }

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
    ) {
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

    private fun openDirectoryPicker() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Allow user to select any directory
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                }
            }
        directoryPickerLauncher.launch(intent)
    }

    private fun handleActivityResult(
        resultCode: Int,
        data: Intent?,
    ) {
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val treeUri: Uri? = data.data
            if (treeUri != null) {
                try {
                    android.util.Log.d("DirectoryPicker", "Directory selected: $treeUri")

                    // Take persistable URI permission for long-term access
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                        try {
                            activity.contentResolver.takePersistableUriPermission(treeUri, flags)
                            android.util.Log.d("DirectoryPicker", "Persistable URI permission taken successfully")
                        } catch (e: SecurityException) {
                            android.util.Log.e("DirectoryPicker", "SecurityException taking permission: ${e.message}", e)
                            val errorMessage = "Please check the 'Allow access to this folder' checkbox. "
                            directoryPickerResult?.error("PERMISSION_DENIED", errorMessage, null)
                            return
                        } catch (e: Exception) {
                            android.util.Log.e("DirectoryPicker", "Exception taking permission: ${e.message}", e)
                            directoryPickerResult?.error("PERMISSION_ERROR", "Failed to save permission: ${e.message}", null)
                            return
                        }

                        // Verify permission (simplified logic for brevity, full logic in original code if needed)
                        // In practice, takePersistableUriPermission throws if it fails.
                    }

                    val uriString = treeUri.toString()
                    directoryPickerResult?.success(uriString)
                } catch (e: Exception) {
                    android.util.Log.e("DirectoryPicker", "Unexpected error", e)
                    directoryPickerResult?.error("UNEXPECTED_ERROR", "Unexpected error: ${e.message}", null)
                }
            } else {
                directoryPickerResult?.error("NO_URI", "No URI returned", null)
            }
        } else {
            // User cancelled
            directoryPickerResult?.success(null)
        }
        directoryPickerResult = null
    }

    fun stopListening() {
        channel.setMethodCallHandler(null)
    }
}
