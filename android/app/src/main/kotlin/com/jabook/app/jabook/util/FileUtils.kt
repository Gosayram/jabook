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

package com.jabook.app.jabook.util

import java.io.File

public object FileUtils {
    /**
     * Calculate total size of a directory recursively
     */
    public fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0
        if (!directory.isDirectory) return directory.length()

        var length: Int = 0
        directory.listFiles()?.forEach { file ->
            length += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return length
    }

    /**
     * Resolve file path from URI string
     */
    public fun resolvePathFromUri(uriString: String): String {
        try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
                val path = uri.path ?: return uriString
                // Handle /tree/primary:Folder or /document/primary:File
                // The path usually comes as /tree/volumeID:path or /document/volumeID:path

                // We split by ':' to separate volumeID from relative path
                val split = path.split(":")

                if (split.size > 1) {
                    val volumeIdSection = split[0] // e.g. "/tree/primary" or "/tree/1234-5678"
                    val relativePath = split[1] // e.g. "Downloads/MyFolder"

                    val volumeId = volumeIdSection.substringAfterLast("/")

                    if (volumeId.equals("primary", ignoreCase = true)) {
                        return "/storage/emulated/0/$relativePath"
                    } else {
                        // For SD cards, the path is typically /storage/VOLUME_ID/relativePath
                        return "/storage/$volumeId/$relativePath"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors and return original
        }
        return uriString
    }

    /**
     * Formats size in bytes to human-readable string.
     */
    public fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}
