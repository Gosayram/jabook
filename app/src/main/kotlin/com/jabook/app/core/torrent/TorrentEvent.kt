package com.jabook.app.core.torrent

import com.jabook.app.core.domain.model.TorrentStatus

/** Events emitted by the torrent system for logging and notifications */
sealed class TorrentEvent {
  /** Torrent was added to download queue */
  data class TorrentAdded(
    val torrentId: String,
    val name: String,
    val magnetUri: String,
    val audiobookId: String,
  ) : TorrentEvent()

  /** Torrent download started */
  data class TorrentStarted(
    val torrentId: String,
    val name: String,
  ) : TorrentEvent()

  /** Torrent download paused */
  data class TorrentPaused(
    val torrentId: String,
    val name: String,
  ) : TorrentEvent()

  /** Torrent download resumed */
  data class TorrentResumed(
    val torrentId: String,
    val name: String,
  ) : TorrentEvent()

  /** Torrent download completed */
  data class TorrentCompleted(
    val torrentId: String,
    val name: String,
    val audiobookId: String,
    val downloadPath: String,
    val totalSize: Long,
    val downloadTimeMs: Long,
  ) : TorrentEvent()

  /** Torrent download failed */
  data class TorrentError(
    val torrentId: String,
    val name: String,
    val error: String,
    val errorCode: Int? = null,
  ) : TorrentEvent()

  /** Torrent removed from queue */
  data class TorrentRemoved(
    val torrentId: String,
    val name: String,
    val filesDeleted: Boolean,
  ) : TorrentEvent()

  /** Torrent status changed */
  data class TorrentStatusChanged(
    val torrentId: String,
    val name: String,
    val oldStatus: TorrentStatus,
    val newStatus: TorrentStatus,
  ) : TorrentEvent()

  /** Torrent progress updated */
  data class TorrentProgressUpdated(
    val torrentId: String,
    val name: String,
    val progress: Float,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val eta: Long,
    val seeders: Int,
    val leechers: Int,
  ) : TorrentEvent()

  /** Torrent metadata received */
  data class TorrentMetadataReceived(
    val torrentId: String,
    val name: String,
    val totalSize: Long,
    val fileCount: Int,
    val audioFileCount: Int,
  ) : TorrentEvent()

  /** Audio files extracted from torrent */
  data class AudioFilesExtracted(
    val torrentId: String,
    val audiobookId: String,
    val extractedPath: String,
    val audioFiles: List<String>,
    val totalDuration: Long,
  ) : TorrentEvent()

  /** Torrent seeding started */
  data class TorrentSeeding(
    val torrentId: String,
    val name: String,
    val uploadSpeed: Long,
    val ratio: Float,
  ) : TorrentEvent()

  /** Global torrent statistics updated */
  data class TorrentStatsUpdated(
    val activeTorrents: Int,
    val totalDownloaded: Long,
    val totalUploaded: Long,
    val globalDownloadSpeed: Long,
    val globalUploadSpeed: Long,
  ) : TorrentEvent()

  /** Torrent engine initialized */
  data object TorrentEngineInitialized : TorrentEvent()

  /** Torrent engine shutdown */
  data object TorrentEngineShutdown : TorrentEvent()

  /** Torrent engine error */
  data class TorrentEngineError(
    val error: String,
    val errorCode: Int? = null,
  ) : TorrentEvent()
}
