package com.jabook.app.core.domain.model

/** Download status enumeration for audiobooks. Represents the current state of audiobook download. */
enum class DownloadStatus {
    /** Audiobook is not downloaded yet. */
    NOT_DOWNLOADED,

    /** Audiobook is queued for download. */
    QUEUED,

    /** Audiobook is currently being downloaded. */
    DOWNLOADING,

    /** Download is paused by user. */
    PAUSED,

    /** Download completed successfully. */
    COMPLETED,

    /** Download failed due to error. */
    FAILED,

    /** Download was cancelled by user. */
    CANCELLED;

    /** Check if status indicates active download. */
    val isActiveDownload: Boolean
        get() = this in listOf(QUEUED, DOWNLOADING)

    /** Check if status indicates terminal state (no further action needed). */
    val isTerminal: Boolean
        get() = this in listOf(COMPLETED, FAILED, CANCELLED)

    /** Check if download can be resumed from this state. */
    val canResume: Boolean
        get() = this in listOf(PAUSED, FAILED)

    /** Check if download can be cancelled from this state. */
    val canCancel: Boolean
        get() = this in listOf(QUEUED, DOWNLOADING, PAUSED)
}
