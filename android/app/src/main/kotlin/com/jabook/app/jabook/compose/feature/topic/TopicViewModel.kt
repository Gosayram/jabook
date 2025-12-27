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
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.data.torrent.TorrentManager
import com.jabook.app.jabook.compose.domain.model.AuthStatus
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
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
sealed interface TopicUiState {
    data object Loading : TopicUiState

    data class Success(
        val details: TopicDetails,
    ) : TopicUiState

    data class Error(
        val message: String,
    ) : TopicUiState
}

/**
 * ViewModel for Topic Screen.
 *
 * Loads and displays detailed information about a RuTracker topic.
 */
@HiltViewModel
class TopicViewModel
    @Inject
    constructor(
        private val rutrackerRepository: RutrackerRepository,
        private val authRepository: AuthRepository,
        private val torrentManager: TorrentManager,
        private val rutrackerApi: RutrackerApi,
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

        init {
            loadTopicDetails()
        }

        private fun loadTopicDetails() {
            viewModelScope.launch {
                _uiState.value = TopicUiState.Loading

                val result = rutrackerRepository.getTopicDetails(topicId)

                _uiState.value =
                    when (result) {
                        is com.jabook.app.jabook.compose.domain.model.Result.Success -> {
                            TopicUiState.Success(result.data)
                        }
                        is com.jabook.app.jabook.compose.domain.model.Result.Error -> {
                            TopicUiState.Error(result.message ?: context.getString(R.string.unknownError))
                        }
                        is com.jabook.app.jabook.compose.domain.model.Result.Loading -> {
                            TopicUiState.Loading
                        }
                    }
            }
        }

        /**
         * Download torrent release (content) using magnet link or torrent URL.
         */
        fun downloadTorrentRelease(
            magnetUrl: String?,
            torrentUrl: String?,
        ) {
            viewModelScope.launch {
                try {
                    val downloadUrl = magnetUrl ?: torrentUrl
                    if (downloadUrl.isNullOrBlank()) {
                        Log.e("TopicViewModel", "No download URL available")
                        return@launch
                    }

                    // Get default download path
                    val savePath = "${android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                    )}/JabookAudio"

                    val result =
                        torrentManager.addTorrent(
                            magnetUri = downloadUrl,
                            savePath = savePath,
                            topicId = topicId,
                        )

                    if (result.isSuccess) {
                        Log.i("TopicViewModel", "Torrent download started: ${result.getOrNull()}")
                        _message.value = context.getString(R.string.downloadStarted)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: context.getString(R.string.unknownError)
                        Log.e("TopicViewModel", "Failed to start torrent download: $error")
                        _message.value = context.getString(R.string.failedToStartDownloadWithError, error)
                    }
                } catch (e: Exception) {
                    Log.e("TopicViewModel", "Error starting torrent download", e)
                }
            }
        }

        /**
         * Download torrent file (.torrent) to device storage.
         */
        fun downloadTorrentFile() {
            viewModelScope.launch {
                try {
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
                } catch (e: Exception) {
                    Log.e("TopicViewModel", "Error downloading torrent file", e)
                }
            }
        }

        /**
         * Copy magnet link to clipboard.
         */
        fun copyMagnetLink(magnetUrl: String?) {
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
        fun downloadViaMagnet(magnetUrl: String?) {
            if (magnetUrl.isNullOrBlank()) {
                Log.e("TopicViewModel", "No magnet URL available")
                return
            }

            downloadTorrentRelease(magnetUrl, null)
        }

        fun retry() {
            loadTopicDetails()
        }
    }
