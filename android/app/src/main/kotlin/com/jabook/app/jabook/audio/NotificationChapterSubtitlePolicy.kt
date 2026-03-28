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

import java.io.File
import java.net.URI

internal object NotificationChapterSubtitlePolicy {
    fun resolveSubtitle(
        path: String,
        index: Int,
        metadata: Map<String, String>?,
    ): String {
        val explicitTrackTitle = metadata?.get("trackTitle")?.trim().orEmpty()
        if (explicitTrackTitle.isNotEmpty()) {
            return explicitTrackTitle
        }

        val fileBasedTitle =
            if (path.startsWith("http://") || path.startsWith("https://")) {
                runCatching {
                    URI(path).path
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                }.getOrDefault("")
            } else {
                File(path).nameWithoutExtension
            }.trim()

        return if (fileBasedTitle.isNotEmpty()) {
            fileBasedTitle
        } else {
            "Track ${index + 1}"
        }
    }
}
