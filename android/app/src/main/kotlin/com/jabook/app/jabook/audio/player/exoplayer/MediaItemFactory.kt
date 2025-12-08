// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.audio.player.exoplayer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.jabook.app.jabook.audio.core.model.Chapter
import com.jabook.app.jabook.audio.core.model.MediaItemData

/**
 * Factory for creating Media3 MediaItem from domain models.
 */
@OptIn(UnstableApi::class)
object MediaItemFactory {
    /**
     * Creates a Media3 MediaItem from MediaItemData.
     */
    fun createMediaItem(data: MediaItemData): MediaItem {
        val builder =
            MediaItem
                .Builder()
                .setUri(data.uri)
                .setMediaId(data.chapterId ?: data.uri.toString())

        // Set metadata if available
        val metadataBuilder = MediaMetadata.Builder()
        if (data.title != null) {
            metadataBuilder.setTitle(data.title)
        }
        if (data.artist != null) {
            metadataBuilder.setArtist(data.artist)
        }
        if (data.album != null) {
            metadataBuilder.setAlbumTitle(data.album)
        }
        if (data.duration != null && data.duration > 0) {
            metadataBuilder.setDurationMillis(data.duration)
        }

        builder.setMediaMetadata(metadataBuilder.build())
        return builder.build()
    }

    /**
     * Creates a Media3 MediaItem from Chapter.
     */
    fun createMediaItemFromChapter(
        chapter: Chapter,
        bookTitle: String? = null,
    ): MediaItem {
        val uri = chapter.filePath?.let { Uri.parse(it) } ?: Uri.EMPTY
        val mediaItemData =
            MediaItemData(
                uri = uri,
                title = chapter.title,
                artist = bookTitle,
                album = null,
                duration = chapter.getDurationMs(),
                chapterId = chapter.id,
                fileIndex = chapter.fileIndex,
            )
        return createMediaItem(mediaItemData)
    }

    /**
     * Creates a list of Media3 MediaItems from a list of Chapters.
     */
    fun createMediaItemsFromChapters(
        chapters: List<Chapter>,
        bookTitle: String? = null,
    ): List<MediaItem> = chapters.map { createMediaItemFromChapter(it, bookTitle) }

    /**
     * Creates a Media3 MediaItem from a file path.
     */
    fun createMediaItemFromPath(
        filePath: String,
        title: String? = null,
    ): MediaItem {
        val uri = Uri.parse(filePath)
        val mediaItemData =
            MediaItemData(
                uri = uri,
                title = title,
                artist = null,
                album = null,
                duration = null,
                chapterId = null,
                fileIndex = 0,
            )
        return createMediaItem(mediaItemData)
    }
}
