package com.jabook.app.core.network.extractors

import com.jabook.app.core.domain.model.TorrentState
import org.jsoup.nodes.Document

object StateExtractor {
    // Torrent state is not typically shown on details page
    val DEFAULT_TORRENT_STATE = TorrentState.APPROVED

    fun extractTorrentState(doc: Document): TorrentState {
        // Try to extract torrent state from various selectors
        val stateSelectors = listOf(
            ".torrent-state",
            ".status",
            ".state",
            ".torrent-status",
        )

        for (selector in stateSelectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val stateText = element.text().lowercase().trim()
                return parseStateText(stateText)
            }
        }

        return DEFAULT_TORRENT_STATE
    }

    private fun parseStateText(stateText: String): TorrentState {
        return when {
            isApprovedState(stateText) -> TorrentState.APPROVED
            isPendingState(stateText) -> TorrentState.PENDING
            isRejectedState(stateText) -> TorrentState.REJECTED
            isDuplicateState(stateText) -> TorrentState.DUPLICATE
            isClosedState(stateText) -> TorrentState.CLOSED
            isNeedEditState(stateText) -> TorrentState.NEED_EDIT
            isCheckingState(stateText) -> TorrentState.CHECKING
            isAbsorbedState(stateText) -> TorrentState.ABSORBED
            else -> DEFAULT_TORRENT_STATE
        }
    }

    private fun isApprovedState(text: String) = text.contains("approved") || text.contains("одобрено")
    private fun isPendingState(text: String) = text.contains("pending") || text.contains("ожидание")
    private fun isRejectedState(text: String) = text.contains("rejected") || text.contains("отклонено")
    private fun isDuplicateState(text: String) = text.contains("duplicate") || text.contains("дубликат")
    private fun isClosedState(text: String) = text.contains("closed") || text.contains("закрыто")
    private fun isNeedEditState(text: String) = text.contains("need_edit") || text.contains("требует правки")
    private fun isCheckingState(text: String) = text.contains("checking") || text.contains("проверка")
    private fun isAbsorbedState(text: String) = text.contains("absorbed") || text.contains("поглощено")
}
