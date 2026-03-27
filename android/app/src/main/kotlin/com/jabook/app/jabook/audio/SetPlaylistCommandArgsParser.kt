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

import android.os.Bundle

internal object SetPlaylistCommandArgsParser {
    data class ParsedSetPlaylistArgs(
        val filePaths: List<String>,
        val metadata: Map<String, String>?,
        val initialTrackIndex: Int?,
        val initialPositionMs: Long?,
        val groupPath: String?,
    )

    fun parse(args: Bundle): ParsedSetPlaylistArgs? {
        val filePathsArray = args.getStringArray(AudioPlayerLibrarySessionCallback.ARG_FILE_PATHS) ?: return null
        val filePaths =
            filePathsArray
                .mapNotNull { it?.trim() }
                .filter { it.isNotEmpty() }
        if (filePaths.isEmpty()) {
            return null
        }

        val metadata =
            args.getBundle(AudioPlayerLibrarySessionCallback.ARG_METADATA)?.let { metadataBundle ->
                metadataBundle
                    .keySet()
                    .associateWith { key -> metadataBundle.getString(key).orEmpty() }
                    .filterValues { it.isNotBlank() }
                    .ifEmpty { null }
            }

        val initialTrackIndex =
            if (args.containsKey(AudioPlayerLibrarySessionCallback.ARG_INITIAL_TRACK_INDEX)) {
                val parsedIndex = args.getInt(AudioPlayerLibrarySessionCallback.ARG_INITIAL_TRACK_INDEX)
                if (parsedIndex !in filePaths.indices) {
                    return null
                }
                parsedIndex
            } else {
                null
            }

        val initialPositionMs =
            if (args.containsKey(AudioPlayerLibrarySessionCallback.ARG_INITIAL_POSITION)) {
                args.getLong(AudioPlayerLibrarySessionCallback.ARG_INITIAL_POSITION).coerceAtLeast(0L)
            } else {
                null
            }

        return ParsedSetPlaylistArgs(
            filePaths = filePaths,
            metadata = metadata,
            initialTrackIndex = initialTrackIndex,
            initialPositionMs = initialPositionMs,
            groupPath = args.getString(AudioPlayerLibrarySessionCallback.ARG_GROUP_PATH),
        )
    }
}
