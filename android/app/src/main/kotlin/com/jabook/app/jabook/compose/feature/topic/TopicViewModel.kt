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

package com.jabook.app.jabook.compose.feature.topic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.auth.WithAuthorisedCheckUseCase
import com.jabook.app.jabook.compose.navigation.TopicRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * UI State for Topic Screen.
 */
public sealed interface TopicUiState {
    data object Loading : TopicUiState

    public data class Success(
        val details: RutrackerTopicDetails,
    ) : TopicUiState

    public data class Error(
        val message: String,
    ) : TopicUiState
}

/**
 * ViewModel for Topic Screen.
 *
 * Loads and displays detailed information about a RuTracker topic.
 */
@HiltViewModel
public class TopicViewModel
    @Inject
    constructor(
        private val rutrackerRepository: RutrackerRepository,
        private val authRepository: AuthRepository,
        private val torrentManager: TorrentManager,
        private val rutrackerApi: RutrackerApi,
        private val mirrorManager: MirrorManager,
        private val withAuthorisedCheckUseCase: WithAuthorisedCheckUseCase,
        private val avatarPreloader: AvatarPreloader,
        @param:ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val topicId: String = savedStateHandle.toRoute<TopicRoute>().topicId

        private val _uiState = MutableStateFlow<TopicUiState>(TopicUiState.Loading)
        val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        val authStatus: StateFlow<AuthStatus> =
            authRepository.authStatus.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AuthStatus.Unauthenticated,
            )

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        // Pagination state
        private val _isLoadingMoreComments = MutableStateFlow(false)
        val isLoadingMoreComments: StateFlow<Boolean> = _isLoadingMoreComments.asStateFlow()

        private val loadedComments = mutableListOf<com.jabook.app.jabook.compose.domain.model.RutrackerComment>()
        private var currentLoadedPage = 1
        private var nextPageToLoad: Int? = null // For reverse pagination: tracks next page to load (descending)

        init {
            loadTopicDetails()
        }

        private fun loadTopicDetails() {
            viewModelScope.launch {
                _uiState.value = TopicUiState.Loading
                // Reset pagination state on initial load
                loadedComments.clear()
                currentLoadedPage = 1
                nextPageToLoad = null

                // Fetch page 1 to get metadata (title, torrent info, total pages)
                val result = rutrackerRepository.getTopicDetails(topicId)

                when (result) {
                    is com.jabook.app.jabook.compose.domain.model.Result.Success -> {
                        val details = result.data
                        val totalPages = details.totalPages

                        // If multiple pages, fetch the LAST page first (newest comments)
                        if (totalPages > 1) {
                            val lastPageResult = rutrackerRepository.getTopicDetailsPage(topicId, totalPages)
                            when (lastPageResult) {
                                is com.jabook.app.jabook.compose.domain.model.Result.Success -> {
                                    // Preload avatars for last page comments
                                    avatarPreloader.preloadAvatars(context, lastPageResult.data.comments)

                                    // Add comments from last page (already newest to oldest on that page)
                                    loadedComments.addAll(lastPageResult.data.comments)
                                    currentLoadedPage = totalPages
                                    nextPageToLoad = totalPages - 1 // Next page to load is N-1

                                    _uiState.value =
                                        TopicUiState.Success(
                                            details.copy(
                                                comments = loadedComments.toList(),
                                                currentPage = totalPages,
                                            ),
                                        )
                                }
                                is com.jabook.app.jabook.compose.domain.model.Result.Error -> {
                                    // Fallback to page 1 if last page fails
                                    avatarPreloader.preloadAvatars(context, details.comments)
                                    val reversedComments = details.comments.reversed()
                                    loadedComments.addAll(reversedComments)
                                    _uiState.value =
                                        TopicUiState.Success(
                                            details.copy(comments = reversedComments),
                                        )
                                }
                                is com.jabook.app.jabook.compose.domain.model.Result.Loading -> {
                                    // Should not happen
                                }
                            }
                        } else {
                            // Single page: just reverse and show
                            avatarPreloader.preloadAvatars(context, details.comments)
                            val reversedComments = details.comments.reversed()
                            loadedComments.addAll(reversedComments)
                            currentLoadedPage = 1
                            nextPageToLoad = null

                            _uiState.value =
                                TopicUiState.Success(
                                    details.copy(comments = reversedComments),
                                )
                        }
                    }
                    is com.jabook.app.jabook.compose.domain.model.Result.Error -> {
                        _uiState.value = TopicUiState.Error(result.message ?: context.getString(R.string.unknownError))
                    }
                    is com.jabook.app.jabook.compose.domain.model.Result.Loading -> {
                        _uiState.value = TopicUiState.Loading
                    }
                }
            }
        }

        /**
         * Load more comments from the next page (reverse pagination: N-1, N-2, ..., 1).
         */
        public fun loadMoreComments() : Unit {
            val currentState = _uiState.value
            if (currentState !is TopicUiState.Success) return

            val details = currentState.details
            val pageToLoad = nextPageToLoad ?: return // No more pages to load
            if (pageToLoad < 1) return // Already loaded all pages
            if (_isLoadingMoreComments.value) return // Already loading

            viewModelScope.launch {
                _isLoadingMoreComments.value = true

                val result = rutrackerRepository.getTopicDetailsPage(topicId, pageToLoad)

                when (result) {
                    is com.jabook.app.jabook.compose.domain.model.Result.Success -> {
                        // Preload avatars for new comments
                        avatarPreloader.preloadAvatars(context, result.data.comments)

                        // Add older comments to the end of the list
                        // Comments on each page are oldest-to-newest naturally, so we reverse them
                        // to maintain newest-to-oldest order in the combined list
                        val reversedPageComments = result.data.comments.reversed()
                        loadedComments.addAll(reversedPageComments)

                        // Update pagination state
                        nextPageToLoad = if (pageToLoad > 1) pageToLoad - 1 else null

                        // Update UI state with all comments
                        _uiState.value =
                            TopicUiState.Success(
                                details.copy(
                                    comments = loadedComments.toList(),
                                    currentPage = currentLoadedPage, // Keep original page (last page)
                                ),
                            )
                    }
                    is com.jabook.app.jabook.compose.domain.model.Result.Error -> {
                        _message.value = result.message ?: context.getString(R.string.unknownError)
                    }
                    is com.jabook.app.jabook.compose.domain.model.Result.Loading -> {
                        // Ignore
                    }
                }

                _isLoadingMoreComments.value = false
            }
        }

        public fun refreshTopicDetails(silent: Boolean = true) {
            viewModelScope.launch {
                if (!silent) {
                    _uiState.value = TopicUiState.Loading
                } else {
                    _isRefreshing.value = true
                }

                val result = rutrackerRepository.getTopicDetails(topicId)

                when (result) {
                    is com.jabook.app.jabook.compose.domain.model.Result.Success -> {
                        // Preload avatars for comments (offline support)
                        avatarPreloader.preloadAvatars(context, result.data.comments)

                        _uiState.value =
                            TopicUiState.Success(
                                result.data.copy(
                                    comments = result.data.comments.reversed(), // Sort comments newest first
                                ),
                            )
                    }
                    is com.jabook.app.jabook.compose.domain.model.Result.Error -> {
                        if (!silent) {
                            _uiState.value = TopicUiState.Error(result.message ?: context.getString(R.string.unknownError))
                        } else {
                            _message.value = result.message ?: context.getString(R.string.unknownError)
                        }
                    }
                    is com.jabook.app.jabook.compose.domain.model.Result.Loading -> {
                        // Ignore loading state during silent refresh
                    }
                }

                _isRefreshing.value = false
            }
        }

        /**
         * Download torrent release (content) using magnet link or torrent URL.
         */
        public fun downloadTorrentRelease(
            magnetUrl: String?,
            torrentUrl: String?,
        ) {
            viewModelScope.launch {
                try {
                    // Prefer magnet URL over torrent URL
                    val downloadUrl = magnetUrl ?: torrentUrl
                    if (downloadUrl.isNullOrBlank()) {
                        Log.e("TopicViewModel", "No download URL available")
                        _message.value = context.getString(R.string.failedToStartDownload)
                        return@launch
                    }

                    // Validate URL format
                    if (!downloadUrl.startsWith("magnet:", ignoreCase = true) &&
                        !downloadUrl.startsWith("http://", ignoreCase = true) &&
                        !downloadUrl.startsWith("https://", ignoreCase = true)
                    ) {
                        Log.e("TopicViewModel", "Invalid download URL format: $downloadUrl")
                        _message.value = context.getString(R.string.invalidDownloadUrl)
                        return@launch
                    }

                    // Get default download path
                    val downloadsDir =
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS,
                        )
                    if (downloadsDir == null || !downloadsDir.exists()) {
                        Log.e("TopicViewModel", "Downloads directory not available")
                        _message.value = context.getString(R.string.downloadsDirectoryNotAvailable)
                        return@launch
                    }

                    // Create base directory for JabookAudio if it doesn't exist
                    val baseDir = File(downloadsDir, "JabookAudio")
                    if (!baseDir.exists()) {
                        val created = baseDir.mkdirs()
                        if (!created && !baseDir.exists()) {
                            Log.e("TopicViewModel", "Failed to create base directory: ${baseDir.absolutePath}")
                            _message.value = context.getString(R.string.failedToStartDownload)
                            return@launch
                        }
                    }

                    // Get book title from current state to create folder name
                    val bookTitle =
                        when (val state = _uiState.value) {
                            is TopicUiState.Success -> {
                                // Sanitize title for folder name (remove invalid characters)
                                state.details.title
                                    .replace(Regex("[<>:\"/\\|?*]"), "_")
                                    .replace(Regex("\\s+"), "_")
                                    .take(100) // Limit length
                            }
                            else -> topicId // Fallback to topicId if title not available
                        }

                    // Create folder for this specific book: JabookAudio/{topicId}_{sanitizedTitle}
                    val bookFolder = File(baseDir, "${topicId}_$bookTitle")
                    if (!bookFolder.exists()) {
                        val created = bookFolder.mkdirs()
                        if (!created && !bookFolder.exists()) {
                            Log.e("TopicViewModel", "Failed to create book directory: ${bookFolder.absolutePath}")
                            _message.value = context.getString(R.string.failedToStartDownload)
                            return@launch
                        }
                    }

                    val savePath = bookFolder.absolutePath
                    Log.d("TopicViewModel", "Saving torrent to: $savePath")

                    // Check if downloadUrl is a magnet URI or HTTP/HTTPS URL
                    // TorrentManager.addTorrent only accepts magnet URIs
                    if (!downloadUrl.startsWith("magnet:", ignoreCase = true)) {
                        Log.e("TopicViewModel", "downloadTorrentRelease only supports magnet URIs, got: $downloadUrl")
                        _message.value =
                            context.getString(
                                R.string.failedToStartDownloadWithError,
                                "Only magnet links are supported for torrent downloads",
                            )
                        return@launch
                    }

                    // Use WithAuthorisedCheckUseCase to ensure authentication before downloading
                    withAuthorisedCheckUseCase(operationId = "download_torrent_$topicId") {
                        // Ensure TorrentManager is initialized
                        try {
                            torrentManager.initialize()
                        } catch (e: Exception) {
                            Log.w("TopicViewModel", "TorrentManager already initialized or error: ${e.message}")
                        }

                        val result =
                            torrentManager.addTorrent(
                                magnetUri = downloadUrl,
                                savePath = savePath,
                                topicId = topicId,
                            )

                        if (result.isSuccess) {
                            val hash = result.getOrNull()
                            Log.i("TopicViewModel", "Torrent download started: $hash")
                            _message.value = context.getString(R.string.downloadStarted)
                        } else {
                            val exception = result.exceptionOrNull()
                            val error = exception?.message ?: context.getString(R.string.unknownError)
                            Log.e("TopicViewModel", "Failed to start torrent download: $error", exception)
                            _message.value = context.getString(R.string.failedToStartDownloadWithError, error)
                        }
                    }
                } catch (e: RuTrackerError.Unauthorized) {
                    Log.w("TopicViewModel", "Download requires authentication")
                    _message.value = context.getString(R.string.authenticationRequired)
                } catch (e: IllegalStateException) {
                    Log.e("TopicViewModel", "Illegal state during torrent download", e)
                    _message.value = context.getString(R.string.failedToStartDownloadWithError, e.message ?: "Illegal state")
                } catch (e: Exception) {
                    Log.e("TopicViewModel", "Unexpected error starting torrent download", e)
                    _message.value =
                        context.getString(R.string.failedToStartDownloadWithError, e.message ?: context.getString(R.string.unknownError))
                }
            }
        }

        /**
         * Download torrent file (.torrent) to device storage.
         */
        public fun downloadTorrentFile() : Unit {
            viewModelScope.launch {
                try {
                    // Use WithAuthorisedCheckUseCase to ensure authentication before downloading
                    withAuthorisedCheckUseCase(operationId = "download_torrent_file_$topicId") {
                        val response = rutrackerApi.downloadTorrent(topicId)
                        if (response.isSuccessful) {
                            val body: ResponseBody? = response.body()
                            if (body != null) {
                                withContext(Dispatchers.IO) {
                                    // Save to Downloads directory
                                    val downloadsDir =
                                        android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS,
                                        )
                                    val torrentFile = File(downloadsDir, "$topicId.torrent")

                                    body.byteStream().use { input ->
                                        FileOutputStream(torrentFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }

                                    Log.i("TopicViewModel", "Torrent file saved: ${torrentFile.absolutePath}")
                                    _message.value = context.getString(R.string.torrentFileSaved)
                                }
                            } else {
                                Log.e("TopicViewModel", "Response body is null")
                                _message.value = context.getString(R.string.failedToDownloadTorrentFile)
                            }
                        } else {
                            Log.e("TopicViewModel", "Failed to download torrent file: ${response.code()}")
                            _message.value = context.getString(R.string.failedToDownloadTorrentFileWithCode, response.code())
                        }
                    }
                } catch (e: RuTrackerError.Unauthorized) {
                    Log.w("TopicViewModel", "Download torrent file requires authentication")
                    _message.value = context.getString(R.string.authenticationRequired)
                } catch (e: Exception) {
                    Log.e("TopicViewModel", "Error downloading torrent file", e)
                }
            }
        }

        /**
         * Copy magnet link to clipboard.
         */
        public fun copyMagnetLink(magnetUrl: String?) {
            if (magnetUrl.isNullOrBlank()) {
                Log.e("TopicViewModel", "No magnet URL available")
                return
            }

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.magnetLinkLabel), magnetUrl)
            clipboard.setPrimaryClip(clip)
            Log.i("TopicViewModel", "Magnet link copied to clipboard")
            _message.value = context.getString(R.string.magnetLinkCopiedMessage)
        }

        /**
         * Download via magnet link (if available).
         */
        public fun downloadViaMagnet(magnetUrl: String?) {
            if (magnetUrl.isNullOrBlank()) {
                Log.e("TopicViewModel", "No magnet URL available")
                return
            }

            downloadTorrentRelease(magnetUrl, null)
        }

        public fun retry() : Unit {
            loadTopicDetails()
        }

        /**
         * Get URL for opening topic in browser using current mirror.
         */
        public fun getTopicUrl(): String {
            val baseUrl = mirrorManager.getBaseUrl()
            return "$baseUrl/forum/viewtopic.php?t=$topicId"
        }
    }
