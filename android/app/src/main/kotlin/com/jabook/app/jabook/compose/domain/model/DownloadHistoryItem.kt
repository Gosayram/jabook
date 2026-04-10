package com.jabook.app.jabook.compose.domain.model

import androidx.compose.runtime.Immutable

public enum class DownloadStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}

@Immutable
public data class DownloadHistoryItem(
    val id: Int = 0,
    val bookId: String,
    val bookTitle: String,
    val status: DownloadStatus,
    val startedAt: Long,
    val completedAt: Long?,
    val totalBytes: Long?,
    val errorMessage: String?,
)