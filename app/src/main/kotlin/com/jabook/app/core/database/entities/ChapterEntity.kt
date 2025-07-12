package com.jabook.app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a chapter within an audiobook.
 * Linked to AudiobookEntity via foreign key relationship.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobook_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["audiobook_id"]),
        Index(value = ["chapter_number"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "audiobook_id")
    val audiobookId: String,
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "start_position_ms")
    val startPositionMs: Long = 0,
    @ColumnInfo(name = "end_position_ms")
    val endPositionMs: Long = durationMs,
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "download_progress")
    val downloadProgress: Float = 0f,
)
