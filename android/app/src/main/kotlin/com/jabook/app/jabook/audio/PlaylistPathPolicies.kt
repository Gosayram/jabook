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
 * Sorts file paths using natural sort order (numeric-aware).
 * Example: "Глава 2.mp3" < "Глава 10.mp3" < "Глава 100.mp3".
 * Falls back to lexicographic comparison when segments are non-numeric.
 */
internal fun sortFilesByNumericPrefix(filePaths: List<String>): List<String> = filePaths.sortedWith(NaturalOrderComparator)

private val segmentRegex = Regex("\\d+|\\D+")

private object NaturalOrderComparator : Comparator<String> {
    override fun compare(
        a: String,
        b: String,
    ): Int {
        val nameA = a.substringAfterLast('/')
        val nameB = b.substringAfterLast('/')

        val segsA = segmentRegex.findAll(nameA).map { it.value }.toList()
        val segsB = segmentRegex.findAll(nameB).map { it.value }.toList()

        for (i in 0 until maxOf(segsA.size, segsB.size)) {
            val sa = segsA.getOrNull(i) ?: return -1
            val sb = segsB.getOrNull(i) ?: return 1

            val na = sa.toLongOrNull()
            val nb = sb.toLongOrNull()

            val cmp =
                when {
                    na != null && nb != null -> na.compareTo(nb)
                    else -> sa.compareTo(sb, ignoreCase = true)
                }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
