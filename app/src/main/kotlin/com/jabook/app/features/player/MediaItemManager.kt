package com.jabook.app.features.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jabook.app.core.domain.model.Chapter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaItemManager @Inject constructor() {

    /** Create media items from chapters */
    fun createMediaItems(chapters: List<Chapter>): List<MediaItem> {
        return chapters.mapIndexed { index, chapter ->
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(chapter.filePath)))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.title)
                        .setTrackNumber(index + 1)
                        .setTotalTrackCount(chapters.size)
                        .build(),
                )
                .build()
        }
    }
}
