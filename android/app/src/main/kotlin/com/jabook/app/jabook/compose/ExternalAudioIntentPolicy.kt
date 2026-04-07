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

package com.jabook.app.jabook.compose

import android.content.Intent
import android.net.Uri

internal object ExternalAudioIntentPolicy {
    private const val EXTERNAL_GROUP_PREFIX = "external-share:"

    internal fun extractAudioUris(intent: Intent?): List<Uri> {
        if (intent == null) {
            return emptyList()
        }
        val action = intent.action
        return when (action) {
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null && (isAudioMime(intent.type) || isLikelyAudioUri(data))) {
                    listOf(data)
                } else {
                    emptyList()
                }
            }

            Intent.ACTION_SEND -> {
                if (!isAudioMime(intent.type)) {
                    return emptyList()
                }
                @Suppress("DEPRECATION")
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                if (!isAudioMime(intent.type)) {
                    return emptyList()
                }
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }

            else -> emptyList()
        }
    }

    internal fun buildExternalGroupPath(uris: List<Uri>): String {
        if (uris.isEmpty()) {
            return "${EXTERNAL_GROUP_PREFIX}empty"
        }
        val payload = uris.joinToString("|") { it.toString() }
        return EXTERNAL_GROUP_PREFIX + payload.hashCode()
    }

    private fun isAudioMime(mimeType: String?): Boolean = mimeType?.startsWith("audio/") == true

    private fun isLikelyAudioUri(uri: Uri): Boolean {
        val lowerPath = uri.toString().lowercase()
        return lowerPath.endsWith(".mp3") ||
            lowerPath.endsWith(".m4a") ||
            lowerPath.endsWith(".m4b") ||
            lowerPath.endsWith(".aac") ||
            lowerPath.endsWith(".flac") ||
            lowerPath.endsWith(".ogg") ||
            lowerPath.endsWith(".opus") ||
            lowerPath.endsWith(".wav")
    }
}
