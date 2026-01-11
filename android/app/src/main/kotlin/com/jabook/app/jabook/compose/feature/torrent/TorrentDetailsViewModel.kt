// Copyright 2026 Jabook Contributors
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
public class TorrentDetailsViewModel
    @Inject
    constructor(
        private val torrentManager: TorrentManager,
        private val booksRepository: BooksRepository,
        savedStateHandle: SavedStateHandle,
        private val streamingMonitor: TorrentStreamingMonitor,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<TorrentDetailsRoute>()
        public val hash = route.hash

        public val download: StateFlow<TorrentDownload?> =
            torrentManager.downloadsFlow
                .map { it[hash] }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = null,
                )

        public val isBuffering = streamingMonitor.isBuffering

        private val _navigationEvent = MutableSharedFlow<String>()
        public val navigationEvent = _navigationEvent.asSharedFlow()

        public fun playFile(file: TorrentFile) {
            viewModelScope.launch {
                public val currentDownload = download.value ?: return@launch

                // 1. Enable streaming
                torrentManager.enableStreaming(hash)
                // Prioritize this file (7 = top priority)
                torrentManager.prioritizeFile(hash, file.index, 7)

                // 2. Wait for buffer
                // Monitor will update isBuffering state automatically
                public var attempts: Int = 0                public val maxAttempts: Int = 60 // 30 seconds (500ms * 60)
                while (!torrentManager.isFileReadyForStreaming(hash, file.index) && attempts < maxAttempts) {
                    kotlinx.coroutines.delay(500)
                    attempts++
                }

                if (attempts >= maxAttempts) {
                    // Timeout - try anyway or show error
                    // For now, proceed but it might fail
                }

                // 3. Start monitoring
                streamingMonitor.startMonitoring(hash, file.index)

                // 4. Prepare Book & Chapter
                public val bookId: String = "torrent_${hash}_${file.index}"                public val absolutePath = File(currentDownload.savePath, file.path).absolutePath
                public val title = File(file.path).name

                public val book =
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

                public val chapter =
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

                // 5. Save to repository (Using addBooks because it handles chapters)
                booksRepository.addBooks(listOf(book to listOf(chapter)))

                // 6. Navigate
                _navigationEvent.emit(bookId)
            }
        }

        override fun onCleared() {
            super.onCleared()
            // Stop monitoring when ViewModel is cleared (screen closed)
            // Note: If user navigates to PlayerScreen, this ViewModel might be kept alive if it's in backstack?
            // "TorrentStrings" screen is "TorrentDetailsScreen".
            // If we utilize Navigation, valid approach.
            // But if user plays in background?
            // The Monitoring should typically persist while playing.
            // But if we scope it to ViewModel, it dies with the UI flow.
            // Ideally Monitor should be started by Service or be global.
            // Since we made it Singleton, we can leave it running?
            // "stopMonitoring" call here might kill it prematurely if user goes to Player Screen.
            // Let's NOT call stopMonitoring here if we want background playback monitoring.
            // But we need to stop it EVENTUALLY.
            // The monitor tracks "isPlaying". If player stops, it does nothing.
            // But it keeps polling.
            // Let's rely on explicit stop or when new stream starts.
            // Or add a timeout in Monitor if not playing for X minutes.
            // For now, removing onCleared stop to allow background playback monitoring.
        }

        public fun updateFileSelection(selectedIndices: Set<Int>) {
            public val currentDownload = download.value ?: return
            public val files = currentDownload.files

            // Map to priorities list matching file order
            public val priorities =
                files.map { file ->
                    if (selectedIndices.contains(file.index)) {
                        4 // Default/Normal priority
                    } else {
                        0 // Do not download
                    }
                }

            torrentManager.prioritizeFiles(hash, priorities)
        }
    }
