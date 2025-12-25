// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.feature.torrent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.data.model.DownloadStatus
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.data.torrent.TorrentFile
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.navigation.TorrentDetailsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class TorrentDetailsViewModel
    @Inject
    constructor(
        private val torrentManager: TorrentManager,
        private val booksRepository: BooksRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<TorrentDetailsRoute>()
        val hash = route.hash

        val download: StateFlow<TorrentDownload?> =
            torrentManager.downloadsFlow
                .map { it[hash] }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        private val _navigationEvent = MutableSharedFlow<String>()
        val navigationEvent = _navigationEvent.asSharedFlow()

        fun playFile(file: TorrentFile) {
            viewModelScope.launch {
                val currentDownload = download.value ?: return@launch

                // 1. Enable streaming
                torrentManager.enableStreaming(hash)
                // Prioritize this file (7 = top priority)
                torrentManager.prioritizeFile(hash, file.index, 7)

                // 2. Prepare Book & Chapter
                val bookId = "torrent_${hash}_${file.index}"
                val absolutePath = File(currentDownload.savePath, file.path).absolutePath
                val title = File(file.path).name

                val book =
                    Book(
                        id = bookId,
                        title = title,
                        author = "Torrent Stream",
                        coverUrl = null,
                        description = "Streaming from torrent: ${currentDownload.name}",
                        totalDuration = 0.seconds,
                        currentPosition = 0.seconds,
                        progress = 0f,
                        currentChapterIndex = 0,
                        downloadStatus = DownloadStatus.DOWNLOADED, // Mark as downloaded to allow playback
                        downloadProgress = file.progress,
                        localPath = absolutePath,
                        addedDate = System.currentTimeMillis(),
                        lastPlayedDate = System.currentTimeMillis(),
                        isFavorite = false,
                        sourceUrl = null,
                    )

                val chapter =
                    Chapter(
                        id = "${bookId}_ch1",
                        bookId = bookId,
                        title = title,
                        chapterIndex = 0,
                        fileIndex = 0,
                        duration = 0.seconds,
                        fileUrl = absolutePath,
                        position = 0.seconds,
                        isCompleted = false,
                        isDownloaded = true,
                    )

                // 3. Save to repository (Using addBooks because it handles chapters)
                booksRepository.addBooks(listOf(book to listOf(chapter)))

                // 4. Navigate
                _navigationEvent.emit(bookId)
            }
        }
    }
