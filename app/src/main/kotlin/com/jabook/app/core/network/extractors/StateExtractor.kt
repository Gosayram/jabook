package com.jabook.app.core.network.extractors

import com.jabook.app.core.domain.model.TorrentState
import org.jsoup.nodes.Document

object StateExtractor {
    fun extractTorrentState(doc: Document): TorrentState {
        // Torrent state is not typically shown on details page
        return TorrentState.APPROVED
    }
}
