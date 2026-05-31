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

package com.jabook.app.jabook.audio.session

import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_AUDIO_BOOK

/**
 * Builder for MediaSession metadata.
 *
 * Provides convenient methods for building MediaMetadata from domain models.
 */
public object MediaMetadataBuilder {
    /**
     * Builds MediaMetadata from book and chapter information.
     *
     * @param bookTitle The book title
     * @param chapterTitle The current chapter title
     * @param author The book author (optional)
     * @param duration The track duration in milliseconds (optional)
     * @param artworkUri Cover art URI (optional)
     * @return MediaMetadata instance
     */
    public fun buildMetadata(
        bookTitle: String,
        chapterTitle: String? = null,
        author: String? = null,
        duration: Long? = null,
        artworkUri: android.net.Uri? = null,
    ): MediaMetadata {
        val builder =
            MediaMetadata
                .Builder()
                .setTitle(chapterTitle ?: bookTitle)
                .setAlbumTitle(bookTitle)
                .setMediaType(MEDIA_TYPE_AUDIO_BOOK)

        if (author != null) {
            builder.setArtist(author)
        }
        if (artworkUri != null) {
            builder.setArtworkUri(artworkUri)
        }

        return builder.build()
    }

    /**
     * Builds MediaMetadata from a single title.
     */
    public fun buildSimpleMetadata(title: String): MediaMetadata =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .build()
}
