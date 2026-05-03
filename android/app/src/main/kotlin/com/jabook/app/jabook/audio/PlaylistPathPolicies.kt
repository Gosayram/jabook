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

package com.jabook.app.jabook.audio

import android.net.Uri
import java.io.File

internal enum class MediaDataSourceRoute {
    NETWORK_CACHED,
    LOCAL_FILE,
    LOCAL_CONTENT,
    DEFAULT,
}

internal fun buildPlaybackUri(path: String): Uri {
    val isUrl = path.startsWith("http://") || path.startsWith("https://")
    if (isUrl || path.startsWith("content://") || path.startsWith("file://")) {
        return Uri.parse(path)
    }

    return Uri.fromFile(File(path))
}

internal fun resolveMediaDataSourceRoute(uri: Uri): MediaDataSourceRoute =
    when (uri.scheme) {
        "http",
        "https",
        -> MediaDataSourceRoute.NETWORK_CACHED

        "file",
        null,
        -> MediaDataSourceRoute.LOCAL_FILE

        "content",
        -> MediaDataSourceRoute.LOCAL_CONTENT

        else -> MediaDataSourceRoute.DEFAULT
    }

/**
 * Sorts file paths based on numeric prefix in file name.
 * Example: "01.mp3" < "2.mp3" < "10.mp3".
 */
internal fun sortFilesByNumericPrefix(filePaths: List<String>): List<String> {
    val numericPrefixRegex = Regex("^(\\d+)")

    return filePaths.sortedWith(
        Comparator { path1, path2 ->
            val name1 = path1.substringAfterLast('/')
            val name2 = path2.substringAfterLast('/')

            val match1 = numericPrefixRegex.find(name1)
            val match2 = numericPrefixRegex.find(name2)

            if (match1 != null && match2 != null) {
                val num1 = match1.groupValues[1].toLongOrNull() ?: 0L
                val num2 = match2.groupValues[1].toLongOrNull() ?: 0L

                val numberComparison = num1.compareTo(num2)
                if (numberComparison != 0) {
                    return@Comparator numberComparison
                }
            }

            name1.compareTo(name2, ignoreCase = true)
        },
    )
}
