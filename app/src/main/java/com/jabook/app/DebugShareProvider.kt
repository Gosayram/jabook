package com.jabook.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.jabook.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * FileProvider for sharing debug logs and other files
 */
class DebugShareProvider : FileProvider() {
    
    companion object {
        private const val AUTHORITY = "com.jabook.app.fileprovider"
        private const val LOGS_DIR = "logs"
        private const val EXPORTS_DIR = "exports"
        
        /**
         * Gets the authority for this FileProvider
         */
        fun getAuthority(): String = AUTHORITY
        
        /**
         * Shares logs as a ZIP file
         */
        suspend fun shareLogs(context: Context): Intent? {
            return withContext(Dispatchers.IO) {
                try {
                    val appLog = AppLog.getInstance(context)
                    val intent = appLog.shareLogs()
                    
                    if (intent != null) {
                        // Add package name to intent
                        intent.setPackage(context.packageName)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        
                        // Create chooser intent
                        Intent.createChooser(intent, "Share JaBook Logs")
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    AppLog.getInstance(context).error("DebugShareProvider", "Failed to share logs", e)
                    null
                }
            }
        }
        
        /**
         * Exports app data
         */
        suspend fun exportAppData(context: Context): Intent? {
            return withContext(Dispatchers.IO) {
                try {
                    val exportsDir = File(context.filesDir, EXPORTS_DIR)
                    if (!exportsDir.exists()) {
                        exportsDir.mkdirs()
                    }
                    
                    val exportFile = File(exportsDir, "jabook-export-${System.currentTimeMillis()}.zip")
                    
                    // Create ZIP with app data
                    createExportArchive(context, exportFile)
                    
                    // Create share intent
                    val uri = FileProvider.getUriForFile(
                        context,
                        AUTHORITY,
                        exportFile
                    )
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    
                    Intent.createChooser(intent, "Export JaBook Data")
                } catch (e: Exception) {
                    AppLog.getInstance(context).error("DebugShareProvider", "Failed to export app data", e)
                    null
                }
            }
        }
        
        /**
         * Creates an archive with app data
         */
        private suspend fun createExportArchive(context: Context, outputFile: File) {
            withContext(Dispatchers.IO) {
                java.util.zip.ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
                    // Add logs
                    val logsDir = File(context.filesDir, LOGS_DIR)
                    if (logsDir.exists()) {
                        addDirectoryToZip(logsDir, "logs", zipOut)
                    }
                    
                    // Add preferences
                    val prefsFile = File(context.filesDir, "shared_prefs")
                    if (prefsFile.exists()) {
                        addDirectoryToZip(prefsFile, "preferences", zipOut)
                    }
                    
                    // Add torrents data
                    val torrentsDir = File(context.filesDir, "torrents")
                    if (torrentsDir.exists()) {
                        addDirectoryToZip(torrentsDir, "torrents", zipOut)
                    }
                    
                    // Add audio files
                    val audioDir = File(context.filesDir, "audio")
                    if (audioDir.exists()) {
                        addDirectoryToZip(audioDir, "audio", zipOut)
                    }
                    
                    // Add app info
                    addAppInfo(context, zipOut)
                }
            }
        }
        
        /**
         * Adds a directory to ZIP archive
         */
        private fun addDirectoryToZip(directory: File, zipPath: String, zipOut: java.util.zip.ZipOutputStream) {
            directory.listFiles()?.forEach { file ->
                val entryPath = "$zipPath/${file.name}"
                if (file.isDirectory) {
                    addDirectoryToZip(file, entryPath, zipOut)
                } else {
                    zipOut.putNextEntry(java.util.zip.ZipEntry(entryPath))
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
        }
        
        /**
         * Adds app info to ZIP archive
         */
        private fun addAppInfo(context: Context, zipOut: java.util.zip.ZipOutputStream) {
            val appInfo = """
                JaBook App Export
                ================
                
                Export Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
                
                App Information:
                - Package Name: ${context.packageName}
                - Version Name: ${try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { "Unknown" }}
                - Version Code: ${try { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode } catch (e: Exception) { "Unknown" }}
                - SDK Version: ${Build.VERSION.SDK_INT}
                - Device: ${Build.MANUFACTURER} ${Build.MODEL}
                - Android Version: ${Build.VERSION.RELEASE}
                
                Storage Information:
                - Internal Storage: ${context.filesDir.absolutePath}
                - Cache: ${context.cacheDir.absolutePath}
                - External Storage: ${context.getExternalFilesDir(null)?.absolutePath ?: "N/A"}
                
                This export contains:
                - Application logs
                - User preferences
                - Torrent download data
                - Audio files
                - App configuration
                
                Note: This export may contain sensitive information. Handle with care.
            """.trimIndent()
            
            zipOut.putNextEntry(java.util.zip.ZipEntry("app-info.txt"))
            zipOut.write(appInfo.toByteArray())
            zipOut.closeEntry()
        }
    }
    
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return try {
            val file = getFileForUri(uri)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            AppLog.getInstance(context).error("DebugShareProvider", "Failed to open file: $uri", e)
            throw FileNotFoundException("File not found: $uri")
        }
    }
    
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val file = getFileForUri(uri)
        
        if (projection == null) {
            return null
        }
        
        val values = ContentValues().apply {
            put(OpenableColumns.DISPLAY_NAME, file.name)
            put(OpenableColumns.SIZE, file.length())
        }
        
        return MatrixCursor(projection).apply {
            addRow(arrayOf(
                values.getAsString(OpenableColumns.DISPLAY_NAME),
                values.getAsLong(OpenableColumns.SIZE)
            ))
        }
    }
    
    override fun getType(uri: Uri): String? {
        return when (uri.pathSegments?.lastOrNull()) {
            "logs" -> "application/zip"
            "exports" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val file = getFileForUri(uri)
        return if (file.delete()) 1 else 0
    }
    
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Not supported")
    }
    
    override fun onCreate(): Boolean {
        return true
    }
    
    /**
     * Gets the file for the given URI
     */
    private fun getFileForUri(uri: Uri): File {
        val path = uri.pathSegments?.joinToString("/") ?: ""
        return when {
            path.startsWith(LOGS_DIR) -> File(context.filesDir, path)
            path.startsWith(EXPORTS_DIR) -> File(context.filesDir, path)
            else -> throw FileNotFoundException("File not found: $uri")
        }
    }
}