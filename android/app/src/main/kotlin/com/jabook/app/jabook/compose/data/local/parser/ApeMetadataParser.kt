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

package com.jabook.app.jabook.compose.data.local.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * [AudioMetadataParser] fallback for APEv2-only tags, which
 * MediaMetadataRetriever cannot read at all. Only the text fields APE
 * provides are filled; duration/cover/genre stay empty on purpose — callers
 * are expected to merge these into an existing (possibly title-less)
 * [AudioMetadata] rather than use this standalone.
 */
public class ApeMetadataParser
    @Inject
    constructor() : AudioMetadataParser {
        override suspend fun parseMetadata(filePath: String): AudioMetadata? =
            withContext(Dispatchers.IO) {
                if (!filePath.endsWith(".mp3", ignoreCase = true)) return@withContext null
                val tags = ApeTagReader.read(File(filePath)) ?: return@withContext null
                AudioMetadata(
                    title = tags.title,
                    artist = tags.artist,
                    album = tags.album,
                    albumArtist = null,
                    duration = 0L,
                    genre = null,
                    year = tags.year,
                    trackNumber = null,
                    coverArt = null,
                )
            }
    }
