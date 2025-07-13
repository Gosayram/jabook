package com.jabook.app.shared.debug

import com.jabook.app.core.torrent.TorrentEvent

object TorrentEventFormatter {

    fun formatTorrentEventMessage(event: TorrentEvent): String {
        return formatEventInternal(event)
    }

    private fun formatEventInternal(event: TorrentEvent): String {
        return when (event) {
            is TorrentEvent.TorrentAdded,
            is TorrentEvent.TorrentStarted,
            is TorrentEvent.TorrentCompleted,
            is TorrentEvent.TorrentPaused,
            is TorrentEvent.TorrentResumed,
            -> formatBasicTorrentEvent(event)
            is TorrentEvent.TorrentError -> formatErrorEvent(event)
            is TorrentEvent.TorrentRemoved -> formatRemovedEvent(event)
            is TorrentEvent.TorrentStatusChanged -> formatStatusChangedEvent(event)
            is TorrentEvent.TorrentProgressUpdated -> formatProgressEvent(event)
            is TorrentEvent.TorrentMetadataReceived -> formatMetadataEvent(event)
            is TorrentEvent.AudioFilesExtracted -> formatAudioFilesEvent(event)
            is TorrentEvent.TorrentSeeding -> formatSeedingEvent(event)
            is TorrentEvent.TorrentStatsUpdated -> formatStatsEvent(event)
            is TorrentEvent.TorrentEngineInitialized,
            is TorrentEvent.TorrentEngineShutdown,
            is TorrentEvent.TorrentEngineError,
            -> formatEngineEvent(event)
        }
    }

    private fun formatBasicTorrentEvent(event: TorrentEvent): String {
        return when (event) {
            is TorrentEvent.TorrentAdded -> formatBasicEvent("Torrent added", event.name, event.torrentId)
            is TorrentEvent.TorrentStarted -> formatBasicEvent("Torrent started", event.name, event.torrentId)
            is TorrentEvent.TorrentCompleted -> formatBasicEvent("Torrent completed", event.name, event.torrentId)
            is TorrentEvent.TorrentPaused -> formatBasicEvent("Torrent paused", event.name, event.torrentId)
            is TorrentEvent.TorrentResumed -> formatBasicEvent("Torrent resumed", event.name, event.torrentId)
            else -> "Unknown event"
        }
    }

    private fun formatEngineEvent(event: TorrentEvent): String {
        return when (event) {
            is TorrentEvent.TorrentEngineInitialized -> "Torrent engine initialized"
            is TorrentEvent.TorrentEngineShutdown -> "Torrent engine shutdown"
            is TorrentEvent.TorrentEngineError -> "Torrent engine error: ${event.error}"
            else -> "Unknown engine event"
        }
    }

    private fun formatBasicEvent(action: String, name: String, torrentId: String): String {
        return "$action: $name ($torrentId)"
    }

    private fun formatErrorEvent(event: TorrentEvent.TorrentError): String {
        return "Torrent error: ${event.name} (${event.torrentId}) - ${event.error}"
    }

    private fun formatRemovedEvent(event: TorrentEvent.TorrentRemoved): String {
        return "Torrent removed: ${event.name} (${event.torrentId}) files deleted: ${event.filesDeleted}"
    }

    private fun formatStatusChangedEvent(event: TorrentEvent.TorrentStatusChanged): String {
        return "Torrent status changed: ${event.name} (${event.torrentId}) ${event.oldStatus} -> ${event.newStatus}"
    }

    private fun formatProgressEvent(event: TorrentEvent.TorrentProgressUpdated): String {
        return "Torrent progress: ${event.name} (${event.torrentId}) ${(event.progress * 100).toInt()}% ${event.downloadSpeed}B/s"
    }

    private fun formatMetadataEvent(event: TorrentEvent.TorrentMetadataReceived): String {
        return "Torrent metadata: ${event.name} (${event.torrentId}) ${event.totalSize}B ${event.audioFileCount} audio files"
    }

    private fun formatAudioFilesEvent(event: TorrentEvent.AudioFilesExtracted): String {
        return "Audio files extracted: ${event.audiobookId} (${event.torrentId}) ${event.audioFiles.size} files"
    }

    private fun formatSeedingEvent(event: TorrentEvent.TorrentSeeding): String {
        return "Torrent seeding: ${event.name} (${event.torrentId}) ${event.uploadSpeed}B/s ratio: ${event.ratio}"
    }

    private fun formatStatsEvent(event: TorrentEvent.TorrentStatsUpdated): String {
        return "Torrent stats: ${event.activeTorrents} active, ${event.totalDownloaded}B downloaded, ${event.totalUploaded}B uploaded"
    }
}
