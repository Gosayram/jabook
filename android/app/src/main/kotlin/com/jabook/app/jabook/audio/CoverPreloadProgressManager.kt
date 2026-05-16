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

package com.jabook.app.jabook.audio

import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.jabook.app.jabook.compose.core.lifecycle.LifecycleAware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages cover art preloading progress for playlists.
 *
 * P-05: Shows progress while preloading cover arts for large playlists.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoverPreloadProgressManager(
    private val playlistManager: PlaylistManager,
    private val coverPreloadExecutor: CoverPreloadExecutor,
) : DefaultLifecycleObserver,
    LifecycleAware {
    private val _progress =
        MutableStateFlow<CoverPreloadProgress>(
            CoverPreloadProgress(
                loaded = 0,
                total = 0,
                phase = Phase.IDLE,
            ),
        )
    val progress: StateFlow<CoverPreloadProgress> = _progress.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(owner: LifecycleOwner) {
        // Start observing playlist changes
        playlistManager.playlistLoadProgress.observe(owner) { playlistProgress ->
            // When playlist starts loading, start cover preloading
            if (playlistProgress.phase == Phase.LOADING_FIRST || playlistProgress.phase == Phase.LOADING_BACKGROUND) {
                startPreloadingCovers()
            } else if (playlistProgress.phase == Phase.DONE) {
                // Playlist done, stop cover preloading
                stopPreloadingCovers()
            }
        }
    }

    private fun startPreloadingCovers() {
        scope.launch {
            val filePaths = playlistManager.currentFilePaths ?: return@launch
            val totalCovers = filePaths.size
            _progress.value =
                CoverPreloadProgress(
                    loaded = 0,
                    total = totalCovers,
                    phase = Phase.LOADING,
                )

            // Preload covers in batches
            val batchSize = 10
            var loaded = 0
            while (loaded < totalCovers) {
                val batch = filePaths.slice(loaded until minOf(loaded + batchSize, totalCovers))
                preloadBatch(batch)
                loaded += batch.size
                _progress.value =
                    CoverPreloadProgress(
                        loaded = loaded,
                        total = totalCovers,
                        phase = Phase.LOADING,
                    )
            }
        }
    }

    private suspend fun preloadBatch(filePaths: List<String>) {
        withContext(Dispatchers.IO) {
            for (path in filePaths) {
                // Preload cover for this track if available
                val coverUri = extractCoverUriFromMetadata(path) ?: continue
                coverPreloadExecutor.preload(coverUri)
            }
        }
    }

    private fun extractCoverUriFromMetadata(path: String): String? {
        // Extract cover URI from metadata - simplified for demo
        // In real implementation, would parse metadata from file or database
        return null // Not implemented in this demo
    }

    private fun stopPreloadingCovers() {
        scope.coroutineContext.cancelChildren()
        _progress.value =
            CoverPreloadProgress(
                loaded = 0,
                total = 0,
                phase = Phase.IDLE,
            )
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
    }
}

data class CoverPreloadProgress(
    val loaded: Int,
    val total: Int,
    val phase: Phase,
) {
    val fraction: Float get() = if (total > 0) loaded.toFloat() / total else 0f
}

enum class Phase {
    IDLE,
    LOADING,
    DONE,
}

interface CoverPreloadExecutor {
    fun preload(coverUri: String)
}
