package com.jabook.app.features.player

import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Chapter

class ChapterHandler(
  private val mediaItemManager: MediaItemManager,
) {
  fun createChapters(audiobook: Audiobook): List<Chapter> {
    // For now, create a single chapter from the audiobook
    // Actual chapter loading from repository will be implemented later
    return if (audiobook.localAudioPath != null) {
      listOf(
        Chapter(
          id = "${audiobook.id}_chapter_1",
          audiobookId = audiobook.id,
          chapterNumber = 1,
          title = audiobook.title,
          filePath = audiobook.localAudioPath,
          durationMs = audiobook.durationMs,
          isDownloaded = audiobook.isDownloaded,
        ),
      )
    } else {
      emptyList()
    }
  }

  fun createMediaItems(chapters: List<Chapter>): List<androidx.media3.common.MediaItem> = mediaItemManager.createMediaItems(chapters)
}
