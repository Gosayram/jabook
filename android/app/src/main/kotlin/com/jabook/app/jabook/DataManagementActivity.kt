package com.jabook.app.jabook

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Activity for managing app storage space.
 *
 * This activity is called by the system when user wants to manage app storage
 * (e.g., from Settings > Apps > jabook > Storage > Manage space).
 * It's required for apps that use MANAGE_EXTERNAL_STORAGE permission.
 *
 * The activity shows storage information and allows clearing cache.
 */
class DataManagementActivity : AppCompatActivity() {
    private lateinit var cacheSizeText: TextView
    private lateinit var dataSizeText: TextView
    private lateinit var totalSizeText: TextView
    private lateinit var clearCacheButton: Button
    private lateinit var openAppButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_management)

        // Initialize views
        cacheSizeText = findViewById(R.id.cache_size_text)
        dataSizeText = findViewById(R.id.data_size_text)
        totalSizeText = findViewById(R.id.total_size_text)
        clearCacheButton = findViewById(R.id.clear_cache_button)
        openAppButton = findViewById(R.id.open_app_button)
        progressBar = findViewById(R.id.progress_bar)

        // Load storage information
        loadStorageInfo()

        // Set up buttons
        clearCacheButton.setOnClickListener {
            showClearCacheDialog()
        }

        openAppButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Loads storage information asynchronously.
     */
    private fun loadStorageInfo() {
        Thread {
            try {
                val cacheSize = calculateCacheSize()
                val dataSize = calculateDataSize()
                val totalSize = cacheSize + dataSize

                runOnUiThread {
                    cacheSizeText.text = "Cache: ${formatSize(cacheSize)}"
                    dataSizeText.text = "Data: ${formatSize(dataSize)}"
                    totalSizeText.text = "Total: ${formatSize(totalSize)}"
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                android.util.Log.e("DataManagementActivity", "Error loading storage info", e)
                runOnUiThread {
                    cacheSizeText.text = "Cache: Error"
                    dataSizeText.text = "Data: Error"
                    totalSizeText.text = "Total: Error"
                    progressBar.visibility = View.GONE
                }
            }
        }.start()
    }

    /**
     * Calculates cache size.
     */
    private fun calculateCacheSize(): Long {
        var totalSize = 0L
        try {
            // Internal cache
            val internalCache = cacheDir
            totalSize += getDirectorySize(internalCache)

            // External cache
            externalCacheDir?.let {
                if (it.exists()) {
                    totalSize += getDirectorySize(it)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DataManagementActivity", "Error calculating cache size", e)
        }
        return totalSize
    }

    /**
     * Calculates data size (excluding cache).
     */
    private fun calculateDataSize(): Long {
        var totalSize = 0L
        try {
            // Internal files
            val internalFiles = filesDir
            if (internalFiles.exists()) {
                totalSize += getDirectorySize(internalFiles)
            }

            // External files (if accessible)
            getExternalFilesDir(null)?.let {
                if (it.exists()) {
                    totalSize += getDirectorySize(it)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DataManagementActivity", "Error calculating data size", e)
        }
        return totalSize
    }

    /**
     * Recursively calculates directory size.
     */
    private fun getDirectorySize(directory: File): Long {
        var size = 0L
        try {
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        size +=
                            if (file.isDirectory) {
                                getDirectorySize(file)
                            } else {
                                file.length()
                            }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DataManagementActivity", "Error calculating size for ${directory.path}", e)
        }
        return size
    }

    /**
     * Formats size in bytes to human-readable string.
     */
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    /**
     * Shows dialog to confirm cache clearing.
     */
    private fun showClearCacheDialog() {
        AlertDialog
            .Builder(this)
            .setTitle("Clear Cache")
            .setMessage("This will clear all cached data. Downloaded audiobooks will not be affected. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                clearCache()
            }.setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Clears app cache.
     */
    private fun clearCache() {
        progressBar.visibility = View.VISIBLE
        clearCacheButton.isEnabled = false

        Thread {
            try {
                var clearedSize = 0L

                // Clear internal cache
                val internalCache = cacheDir
                clearedSize += getDirectorySize(internalCache)
                deleteDirectory(internalCache)

                // Clear external cache
                externalCacheDir?.let {
                    if (it.exists()) {
                        clearedSize += getDirectorySize(it)
                        deleteDirectory(it)
                    }
                }

                val clearedSizeFormatted = formatSize(clearedSize)

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    clearCacheButton.isEnabled = true
                    Toast
                        .makeText(
                            this@DataManagementActivity,
                            "Cache cleared: $clearedSizeFormatted",
                            Toast.LENGTH_SHORT,
                        ).show()

                    // Reload storage info
                    loadStorageInfo()
                }
            } catch (e: Exception) {
                android.util.Log.e("DataManagementActivity", "Error clearing cache", e)
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    clearCacheButton.isEnabled = true
                    Toast
                        .makeText(
                            this@DataManagementActivity,
                            "Error clearing cache: ${e.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }.start()
    }

    /**
     * Recursively deletes directory.
     */
    private fun deleteDirectory(directory: File) {
        try {
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            deleteDirectory(file)
                        } else {
                            file.delete()
                        }
                    }
                }
                // Don't delete the directory itself, just its contents
                // (Android system manages cache directories)
            }
        } catch (e: Exception) {
            android.util.Log.w("DataManagementActivity", "Error deleting ${directory.path}", e)
        }
    }
}
