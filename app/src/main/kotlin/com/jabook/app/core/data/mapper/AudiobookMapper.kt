package com.jabook.app.core.data.mapper

import com.jabook.app.core.database.entities.AudiobookEntity
import com.jabook.app.core.database.entities.BookmarkEntity
import com.jabook.app.core.database.entities.ChapterEntity
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.core.domain.model.Bookmark
import com.jabook.app.core.domain.model.Chapter
import com.jabook.app.core.database.entities.DownloadStatus as EntityDownloadStatus
import com.jabook.app.core.domain.model.DownloadStatus as DomainDownloadStatus

/**
 * Mappers for converting between database entities and domain models.
 */
object AudiobookMapper {
    /**
     * Convert AudiobookEntity to Audiobook domain model.
     */
    fun AudiobookEntity.toDomain(): Audiobook {
        return Audiobook(
            id = id,
            title = title,
            author = author,
            narrator = narrator,
            description = description,
            category = category,
            coverUrl = coverUrl,
            localCoverPath = localCoverPath,
            localAudioPath = localAudioPath,
            fileSize = fileSize,
            durationMs = durationMs,
            quality = quality,
            bitrate = bitrate,
            sampleRate = sampleRate,
            torrentUrl = torrentUrl,
            magnetLink = magnetLink,
            seeders = seeders,
            leechers = leechers,
            downloadStatus = downloadStatus.toDomain(),
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            currentPositionMs = currentPositionMs,
            isCompleted = isCompleted,
            lastPlayedAt = lastPlayedAt,
            addedAt = addedAt,
            updatedAt = updatedAt,
            isFavorite = isFavorite,
            userRating = userRating,
            playbackSpeed = playbackSpeed,
            chapterCount = chapterCount,
        )
    }

    /**
     * Convert Audiobook domain model to AudiobookEntity.
     */
    fun Audiobook.toEntity(): AudiobookEntity {
        return AudiobookEntity(
            id = id,
            title = title,
            author = author,
            narrator = narrator,
            description = description,
            category = category,
            coverUrl = coverUrl,
            localCoverPath = localCoverPath,
            localAudioPath = localAudioPath,
            fileSize = fileSize,
            durationMs = durationMs,
            quality = quality,
            bitrate = bitrate,
            sampleRate = sampleRate,
            torrentUrl = torrentUrl,
            magnetLink = magnetLink,
            seeders = seeders,
            leechers = leechers,
            downloadStatus = downloadStatus.toEntity(),
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            currentPositionMs = currentPositionMs,
            isCompleted = isCompleted,
            lastPlayedAt = lastPlayedAt,
            addedAt = addedAt,
            updatedAt = updatedAt,
            isFavorite = isFavorite,
            userRating = userRating,
            playbackSpeed = playbackSpeed,
            chapterCount = chapterCount,
        )
    }

    /**
     * Convert ChapterEntity to Chapter domain model.
     */
    fun ChapterEntity.toDomain(): Chapter {
        return Chapter(
            id = id,
            audiobookId = audiobookId,
            chapterNumber = chapterNumber,
            title = title,
            filePath = filePath,
            durationMs = durationMs,
            startPositionMs = startPositionMs,
            endPositionMs = endPositionMs,
            fileSize = fileSize,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
        )
    }

    /**
     * Convert Chapter domain model to ChapterEntity.
     */
    fun Chapter.toEntity(): ChapterEntity {
        return ChapterEntity(
            id = id,
            audiobookId = audiobookId,
            chapterNumber = chapterNumber,
            title = title,
            filePath = filePath,
            durationMs = durationMs,
            startPositionMs = startPositionMs,
            endPositionMs = endPositionMs,
            fileSize = fileSize,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
        )
    }

    /**
     * Convert BookmarkEntity to Bookmark domain model.
     */
    fun BookmarkEntity.toDomain(): Bookmark {
        return Bookmark(
            id = id,
            audiobookId = audiobookId,
            title = title,
            note = note,
            positionMs = positionMs,
            chapterId = chapterId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * Convert Bookmark domain model to BookmarkEntity.
     */
    fun Bookmark.toEntity(): BookmarkEntity {
        return BookmarkEntity(
            id = id,
            audiobookId = audiobookId,
            title = title,
            note = note,
            positionMs = positionMs,
            chapterId = chapterId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * Convert EntityDownloadStatus to DomainDownloadStatus.
     */
    private fun EntityDownloadStatus.toDomain(): DomainDownloadStatus {
        return when (this) {
            EntityDownloadStatus.NOT_DOWNLOADED -> DomainDownloadStatus.NOT_DOWNLOADED
            EntityDownloadStatus.QUEUED -> DomainDownloadStatus.QUEUED
            EntityDownloadStatus.DOWNLOADING -> DomainDownloadStatus.DOWNLOADING
            EntityDownloadStatus.PAUSED -> DomainDownloadStatus.PAUSED
            EntityDownloadStatus.COMPLETED -> DomainDownloadStatus.COMPLETED
            EntityDownloadStatus.FAILED -> DomainDownloadStatus.FAILED
            EntityDownloadStatus.CANCELLED -> DomainDownloadStatus.CANCELLED
        }
    }

    /**
     * Convert DomainDownloadStatus to EntityDownloadStatus.
     */
    private fun DomainDownloadStatus.toEntity(): EntityDownloadStatus {
        return when (this) {
            DomainDownloadStatus.NOT_DOWNLOADED -> EntityDownloadStatus.NOT_DOWNLOADED
            DomainDownloadStatus.QUEUED -> EntityDownloadStatus.QUEUED
            DomainDownloadStatus.DOWNLOADING -> EntityDownloadStatus.DOWNLOADING
            DomainDownloadStatus.PAUSED -> EntityDownloadStatus.PAUSED
            DomainDownloadStatus.COMPLETED -> EntityDownloadStatus.COMPLETED
            DomainDownloadStatus.FAILED -> EntityDownloadStatus.FAILED
            DomainDownloadStatus.CANCELLED -> EntityDownloadStatus.CANCELLED
        }
    }

    /**
     * Convert list of AudiobookEntity to list of Audiobook.
     */
    fun List<AudiobookEntity>.toDomainList(): List<Audiobook> {
        return map { it.toDomain() }
    }

    /**
     * Convert list of ChapterEntity to list of Chapter.
     */
    fun List<ChapterEntity>.toChapterDomainList(): List<Chapter> {
        return map { it.toDomain() }
    }

    /**
     * Convert list of BookmarkEntity to list of Bookmark.
     */
    fun List<BookmarkEntity>.toBookmarkDomainList(): List<Bookmark> {
        return map { it.toDomain() }
    }
}
