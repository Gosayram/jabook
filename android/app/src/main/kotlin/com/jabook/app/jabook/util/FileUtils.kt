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

import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

public object FileUtils {
    /**
     * Calculate total size of a directory recursively.
     * Uses okio: single-syscall metadata, symlink-safe, no intermediate File objects.
     */
    public fun getDirectorySize(directory: File): Long {
        val path = directory.toOkioPath()
        val fs = FileSystem.SYSTEM
        if (!fs.exists(path)) return 0L
        return fs
            .listRecursively(path)
            .mapNotNull { fs.metadataOrNull(it)?.let { m -> if (m.isRegularFile) m.size else null } }
            .sum()
    }

    /**
     * Resolve file path from SAF URI string.
     *
     * Handles both /tree/volume:path and /document/volume:path — the innermost
     * (last) selector wins, so a "tree/primary:X/document/sdcard:Y" URI resolves
     * to the document's volume, not the tree's.
     */
    public fun resolvePathFromUri(uriString: String): String {
        try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "content" && uri.authority == "com.android.externalstorage.documents") {
                val path = uri.path ?: return uriString
                // Innermost selector wins: /tree/vol:x/document/vol:y → document's volume.
                val segment =
                    path.substringAfterLast("/document/", path.substringAfterLast("/tree/", ""))
                if (segment.isEmpty()) return uriString
                val colonIdx = segment.indexOf(':')
                if (colonIdx <= 0) return uriString
                val volumeId = segment.substring(0, colonIdx)
                val relativePath = segment.substring(colonIdx + 1)
                return if (volumeId.equals("primary", ignoreCase = true)) {
                    "/storage/emulated/0/$relativePath"
                } else {
                    // For SD cards, the path is typically /storage/VOLUME_ID/relativePath
                    "/storage/$volumeId/$relativePath"
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors and return original
        }
        return uriString
    }
}
