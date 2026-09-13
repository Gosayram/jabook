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

import javax.inject.Inject

/**
 * Chains [Media3MetadataParser] (duration, cover art) with [ApeMetadataParser]
 * (APEv2 text tags that MediaMetadataRetriever cannot read — common on
 * RuTracker MP3s). APE values only fill fields the base parser left blank, so
 * well-tagged files behave exactly as before.
 */
public class HybridAudioMetadataParser
    @Inject
    constructor(
        private val base: Media3MetadataParser,
        private val ape: ApeMetadataParser,
    ) : AudioMetadataParser {
        override suspend fun parseMetadata(filePath: String): AudioMetadata? {
            val primary = base.parseMetadata(filePath) ?: return ape.parseMetadata(filePath)
            // ponytail: skip APE parsing when Media3 has title+artist (avoids double I/O on every file)
            if (!primary.title.isNullOrBlank() && !primary.artist.isNullOrBlank()) return primary
            val fallback = ape.parseMetadata(filePath) ?: return primary
            return primary.copy(
                title = primary.title.takeUnless { it.isNullOrBlank() } ?: fallback.title,
                artist = primary.artist.takeUnless { it.isNullOrBlank() } ?: fallback.artist,
                album = primary.album.takeUnless { it.isNullOrBlank() } ?: fallback.album,
                year = primary.year ?: fallback.year,
            )
        }
    }
